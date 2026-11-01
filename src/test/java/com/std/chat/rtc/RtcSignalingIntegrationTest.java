package com.std.chat.rtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.std.chat.auth.security.JwtUtil;
import com.std.chat.room.service.ChatRoomService;
import com.std.chat.rtc.domain.CallResult;
import com.std.chat.rtc.domain.SignalingMessageType;
import com.std.chat.rtc.dto.SignalingMessageRequest;
import com.std.chat.rtc.dto.SignalingMessageResponse;
import com.std.chat.rtc.service.CallHistoryService;

/**
 * 실제 서버(임의 포트)를 띄우고 STOMP 클라이언트로 시그널링 흐름을 검증한다.
 * 방 참여 검증과 통화 이력 저장은 Mock으로 대체해서 DB 데이터에 의존하지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RtcSignalingIntegrationTest {

    private static final Long ROOM = 5L;
    private static final long WAIT_SECONDS = 3;

    // 테스트마다 다른 사용자 id를 써서, 앞 테스트의 통화 상태가 남아 있어도 영향을 받지 않게 한다
    private static final AtomicLong USER_IDS = new AtomicLong(1000);

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @MockitoBean
    private CallHistoryService callHistoryService;

    private WebSocketStompClient stompClient;
    private final List<StompClient> clients = new ArrayList<>();

    private Long a;
    private Long b;
    private Long c;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        a = USER_IDS.incrementAndGet();
        b = USER_IDS.incrementAndGet();
        c = USER_IDS.incrementAndGet();
    }

    @AfterEach
    void tearDown() {
        clients.forEach(StompClient::disconnect);
        stompClient.stop();
    }

    @Test
    @DisplayName("걸기 → 수락 → SDP 교환 → 종료까지 상대에게 전달되고 COMPLETED로 기록된다")
    void fullCallFlow() throws Exception {
        StompClient userA = connect(a);
        StompClient userB = connect(b);

        userA.send(b, SignalingMessageType.CALL_REQUEST);
        assertThat(userB.nextSignal()).satisfies(m -> {
            assertThat(m.getType()).isEqualTo(SignalingMessageType.CALL_REQUEST);
            assertThat(m.getFromUserId()).isEqualTo(a);
        });

        userB.send(a, SignalingMessageType.CALL_ACCEPTED);
        assertThat(userA.nextSignal().getType()).isEqualTo(SignalingMessageType.CALL_ACCEPTED);

        userA.send(b, SignalingMessageType.VIDEO_OFFER, "offer-sdp");
        assertThat(userB.nextSignal().getSdp()).isEqualTo("offer-sdp");

        userB.send(a, SignalingMessageType.VIDEO_ANSWER, "answer-sdp");
        assertThat(userA.nextSignal().getSdp()).isEqualTo("answer-sdp");

        userA.send(b, SignalingMessageType.HANG_UP);
        assertThat(userB.nextSignal().getType()).isEqualTo(SignalingMessageType.HANG_UP);

        verify(callHistoryService, timeout(3000)).record(any(), eq(CallResult.COMPLETED));
    }

    @Test
    @DisplayName("벨이 울리는 사람에게 다른 사람이 걸면 건 사람에게 CALL_BUSY가 온다")
    void busyCallee() throws Exception {
        StompClient userA = connect(a);
        StompClient userB = connect(b);
        StompClient userC = connect(c);

        userA.send(b, SignalingMessageType.CALL_REQUEST);
        userB.nextSignal();

        userC.send(b, SignalingMessageType.CALL_REQUEST);
        assertThat(userC.nextSignal()).satisfies(m -> {
            assertThat(m.getType()).isEqualTo(SignalingMessageType.CALL_BUSY);
            assertThat(m.getFromUserId()).isEqualTo(b);
        });
        userB.assertNoSignal();
    }

    @Test
    @DisplayName("받는 사람이 탭 두 개로 접속하면 둘 다 벨이 울리고, 한 탭이 받으면 다른 탭에 CALL_ANSWERED_ELSEWHERE가 온다")
    void multiTabAccept() throws Exception {
        StompClient userA = connect(a);
        StompClient bTab1 = connect(b);
        StompClient bTab2 = connect(b);

        userA.send(b, SignalingMessageType.CALL_REQUEST);
        assertThat(bTab1.nextSignal().getType()).isEqualTo(SignalingMessageType.CALL_REQUEST);
        assertThat(bTab2.nextSignal().getType()).isEqualTo(SignalingMessageType.CALL_REQUEST);

        bTab1.send(a, SignalingMessageType.CALL_ACCEPTED);
        assertThat(userA.nextSignal().getType()).isEqualTo(SignalingMessageType.CALL_ACCEPTED);
        assertThat(bTab2.nextSignal().getType()).isEqualTo(SignalingMessageType.CALL_ANSWERED_ELSEWHERE);
        bTab1.assertNoSignal();
    }

    @Test
    @DisplayName("거절하면 건 사람에게 CALL_REJECTED가 오고 REJECTED로 기록된다")
    void rejectCall() throws Exception {
        StompClient userA = connect(a);
        StompClient userB = connect(b);

        userA.send(b, SignalingMessageType.CALL_REQUEST);
        userB.nextSignal();

        userB.send(a, SignalingMessageType.CALL_REJECTED);
        assertThat(userA.nextSignal().getType()).isEqualTo(SignalingMessageType.CALL_REJECTED);

        verify(callHistoryService, timeout(3000)).record(any(), eq(CallResult.REJECTED));
    }

    @Test
    @DisplayName("통화 중 한쪽 탭의 연결이 끊기면 상대에게 HANG_UP이 온다")
    void disconnectEndsCall() throws Exception {
        StompClient userA = connect(a);
        StompClient userB = connect(b);

        userA.send(b, SignalingMessageType.CALL_REQUEST);
        userB.nextSignal();
        userB.send(a, SignalingMessageType.CALL_ACCEPTED);
        userA.nextSignal();

        userB.disconnect();

        assertThat(userA.nextSignal()).satisfies(m -> {
            assertThat(m.getType()).isEqualTo(SignalingMessageType.HANG_UP);
            assertThat(m.getFromUserId()).isEqualTo(b);
        });
        verify(callHistoryService, timeout(3000))
                .record(argThat(call -> call.getCalleeId().equals(b)), eq(CallResult.COMPLETED));
    }

    @Test
    @DisplayName("통화 없이 SDP를 보내면 보낸 탭의 /user/queue/errors로 BAD_REQUEST가 온다")
    void relayWithoutCallReturnsError() throws Exception {
        StompClient userA = connect(a);
        StompClient userB = connect(b);

        userA.send(b, SignalingMessageType.VIDEO_OFFER, "offer-sdp");

        assertThat(userA.nextError()).containsEntry("code", "BAD_REQUEST");
        userB.assertNoSignal();
    }

    // 사용자가 room.html을 열엇을 때 브라우저의 동작을 재현함.
    private StompClient connect(Long userId) throws Exception {
        // 토큰을 발급 받아 저장
        String token = jwtUtil.generateToken(userId, "USER");
        // 웹소켓 연결 시도
        String url = "ws://localhost:" + port + "/ws/websocket?token=" + token;
        StompSession session = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
        }).get(WAIT_SECONDS, TimeUnit.SECONDS); // 비동기로 접속을 시작(connectAsync), 최대 3초 대기, 연결 세션 반환받는다.
        int before = countSubscriptions(userId);
        StompClient client = new StompClient(session);
        client.subscribe();
        clients.add(client);
        awaitSubscriptions(userId, before + 2); // rtc용, error용 두개의 연결이 만들어짐
        return client;
    }

    // SUBSCRIBE는 비동기로 처리되므로, 서버에 구독 2개(rtc, errors)가 등록될 때까지 기다린다.
    // 기다리지 않고 바로 SEND하면 구독 전에 도착한 시그널을 놓칠 수 있다.
    private void awaitSubscriptions(Long userId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (countSubscriptions(userId) < expected) {
            assertThat(System.currentTimeMillis()).as("구독 등록 대기").isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    private int countSubscriptions(Long userId) {
        SimpUser user = simpUserRegistry.getUser(String.valueOf(userId));
        if (user == null) {
            return 0;
        }
        return user.getSessions().stream()
                .mapToInt(session -> session.getSubscriptions().size())
                .sum();
    }

    /** 사용자 한 명(브라우저 탭 하나)에 해당하는 STOMP 연결 */
    private static class StompClient {

        private final StompSession session;
        private final BlockingQueue<SignalingMessageResponse> signals = new LinkedBlockingQueue<>();
        private final BlockingQueue<Map<String, Object>> errors = new LinkedBlockingQueue<>();

        StompClient(StompSession session) {
            this.session = session;
        }

        void subscribe() {
            session.subscribe("/user/queue/rtc", handler(SignalingMessageResponse.class, signals));
            session.subscribe("/user/queue/errors", handler(Map.class, errors));
        }

        void send(Long targetUserId, SignalingMessageType type) {
            send(targetUserId, type, null);
        }

        void send(Long targetUserId, SignalingMessageType type, String sdp) {
            SignalingMessageRequest request = new SignalingMessageRequest();
            request.setTargetUserId(targetUserId);
            request.setType(type);
            request.setSdp(sdp);
            session.send("/pub/rooms/" + ROOM + "/rtc", request);
        }

        SignalingMessageResponse nextSignal() throws InterruptedException {
            SignalingMessageResponse message = signals.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(message).as("시그널 수신").isNotNull();
            return message;
        }

        Map<String, Object> nextError() throws InterruptedException {
            Map<String, Object> error = errors.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(error).as("에러 수신").isNotNull();
            return error;
        }

        void assertNoSignal() throws InterruptedException {
            assertThat(signals.poll(500, TimeUnit.MILLISECONDS)).as("받지 않아야 할 시그널").isNull();
        }

        void disconnect() {
            if (session.isConnected()) {
                session.disconnect();
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> StompFrameHandler handler(Class<?> type, BlockingQueue<T> queue) {
            return new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return type;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    queue.add((T) payload);
                }
            };
        }
    }
}

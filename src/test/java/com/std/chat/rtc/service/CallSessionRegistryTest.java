package com.std.chat.rtc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.std.chat.common.exception.BadRequestException;
import com.std.chat.rtc.domain.CallSession;
import com.std.chat.rtc.domain.CallStatus;
import com.std.chat.rtc.exception.CallAlreadyAnsweredException;
import com.std.chat.rtc.exception.CalleeBusyException;
import com.std.chat.rtc.exception.CallerBusyException;

class CallSessionRegistryTest {

    private static final Long ROOM = 5L;
    private static final Long A = 1L;
    private static final Long B = 2L;
    private static final Long C = 3L;

    private CallSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CallSessionRegistry();
    }

    @Nested
    @DisplayName("전화 걸기")
    class CreateCall {

        @Test
        @DisplayName("걸면 caller와 callee가 같은 RINGING 통화를 가리킨다")
        void createsRingingCallForBoth() {
            CallSession call = registry.createCall(A, B, ROOM, "a-tab");

            assertThat(call.getStatus()).isEqualTo(CallStatus.RINGING);
            assertThat(call.getCallerSessionId()).isEqualTo("a-tab");
            assertThat(call.getCalleeSessionId()).isNull();
            assertThat(call.getRequestedAt()).isNotNull();
            assertThat(registry.getCall(A)).isSameAs(call);
            assertThat(registry.getCall(B)).isSameAs(call);
        }

        @Test
        @DisplayName("벨이 울리는 사람에게 걸면 CalleeBusyException")
        void calleeBusyWhileRinging() {
            registry.createCall(A, B, ROOM, "a-tab");

            assertThatThrownBy(() -> registry.createCall(C, B, ROOM, "c-tab"))
                    .isInstanceOf(CalleeBusyException.class);
            assertThat(registry.getCall(C)).isNull();
        }

        @Test
        @DisplayName("통화 중인 사람이 다른 사람에게 걸면 CallerBusyException")
        void callerBusyWhileInCall() {
            registry.createCall(A, B, ROOM, "a-tab");
            registry.acknowledgeCall(B, "b-tab");

            assertThatThrownBy(() -> registry.createCall(A, C, ROOM, "a-tab2"))
                    .isInstanceOf(CallerBusyException.class);
        }
    }

    @Nested
    @DisplayName("수락")
    class Accept {

        @Test
        @DisplayName("수락하면 IN_CALL이 되고 받은 탭과 시각이 기록된다")
        void acceptMakesInCall() {
            registry.createCall(A, B, ROOM, "a-tab");

            CallSession call = registry.acknowledgeCall(B, "b-tab1");

            assertThat(call.getStatus()).isEqualTo(CallStatus.IN_CALL);
            assertThat(call.getCalleeSessionId()).isEqualTo("b-tab1");
            assertThat(call.getAnsweredAt()).isNotNull();
        }

        @Test
        @DisplayName("통화가 없으면 수락할 수 없다")
        void cannotAcceptWithoutCall() {
            assertThatThrownBy(() -> registry.acknowledgeCall(B, "b-tab1"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("건 사람은 자기 전화를 수락할 수 없다")
        void callerCannotAccept() {
            registry.createCall(A, B, ROOM, "a-tab");

            assertThatThrownBy(() -> registry.acknowledgeCall(A, "a-tab"))
                    .isInstanceOf(BadRequestException.class);
            assertThat(registry.getCall(A).getStatus()).isEqualTo(CallStatus.RINGING);
        }

        @Test
        @DisplayName("다른 탭이 늦게 수락하면 CallAlreadyAnsweredException, 먼저 받은 탭이 유지된다")
        void lateAcceptFromOtherTab() {
            registry.createCall(A, B, ROOM, "a-tab");
            registry.acknowledgeCall(B, "b-tab1");

            assertThatThrownBy(() -> registry.acknowledgeCall(B, "b-tab2"))
                    .isInstanceOf(CallAlreadyAnsweredException.class);
            assertThat(registry.getCall(B).getCalleeSessionId()).isEqualTo("b-tab1");
        }
    }

    @Nested
    @DisplayName("종료")
    class End {

        @Test
        @DisplayName("한 명이 끊으면 두 사람 모두 통화에서 빠진다")
        void endRemovesBoth() {
            CallSession created = registry.createCall(A, B, ROOM, "a-tab");

            CallSession ended = registry.endCall(B);

            assertThat(ended).isSameAs(created);
            assertThat(registry.getCall(A)).isNull();
            assertThat(registry.getCall(B)).isNull();
        }

        @Test
        @DisplayName("통화가 없으면 종료할 수 없다")
        void cannotEndWithoutCall() {
            assertThatThrownBy(() -> registry.endCall(A))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("종료 후에는 다시 걸 수 있다")
        void canCallAgainAfterEnd() {
            registry.createCall(A, B, ROOM, "a-tab");
            registry.endCall(A);

            CallSession again = registry.createCall(B, A, ROOM, "b-tab1");

            assertThat(again.getCallerId()).isEqualTo(B);
        }
    }

    @Nested
    @DisplayName("연결 끊김")
    class Disconnect {

        @Test
        @DisplayName("통화 중인 탭이 끊기면 통화가 종료된다")
        void callTabDisconnectEndsCall() {
            registry.createCall(A, B, ROOM, "a-tab");
            registry.acknowledgeCall(B, "b-tab1");

            CallSession ended = registry.endCallBySession(B, "b-tab1");

            assertThat(ended).isNotNull();
            assertThat(registry.getCall(A)).isNull();
        }

        @Test
        @DisplayName("통화와 상관없는 탭이 끊기면 통화가 유지된다")
        void otherTabDisconnectKeepsCall() {
            registry.createCall(A, B, ROOM, "a-tab");
            registry.acknowledgeCall(B, "b-tab1");

            CallSession ended = registry.endCallBySession(B, "b-tab2");

            assertThat(ended).isNull();
            assertThat(registry.getCall(A).getStatus()).isEqualTo(CallStatus.IN_CALL);
        }

        @Test
        @DisplayName("벨이 울리는 중 받는 사람의 탭이 끊겨도 통화는 유지된다 (다른 탭에서 받을 수 있음)")
        void calleeTabDisconnectWhileRinging() {
            registry.createCall(A, B, ROOM, "a-tab");

            assertThat(registry.endCallBySession(B, "b-tab1")).isNull();
            assertThat(registry.getCall(B)).isNotNull();
        }

        @Test
        @DisplayName("벨이 울리는 중 건 사람의 탭이 끊기면 통화가 종료된다")
        void callerTabDisconnectWhileRinging() {
            registry.createCall(A, B, ROOM, "a-tab");

            assertThat(registry.endCallBySession(A, "a-tab")).isNotNull();
            assertThat(registry.getCall(B)).isNull();
        }
    }

    @Nested
    @DisplayName("동시 요청")
    class Concurrency {

        private static final int THREADS = 20;

        @Test
        @DisplayName("여러 사람이 동시에 같은 사람에게 걸면 한 명만 성공한다")
        void onlyOneCallerWins() throws Exception {
            AtomicInteger success = new AtomicInteger();
            AtomicInteger busy = new AtomicInteger();

            runConcurrently(i -> {
                try {
                    registry.createCall(100L + i, B, ROOM, "tab-" + i);
                    success.incrementAndGet();
                } catch (CalleeBusyException e) {
                    busy.incrementAndGet();
                }
            });

            assertThat(success.get()).isEqualTo(1);
            assertThat(busy.get()).isEqualTo(THREADS - 1);
        }

        @Test
        @DisplayName("받는 사람의 여러 탭이 동시에 수락하면 한 탭만 성공한다")
        void onlyOneTabWins() throws Exception {
            registry.createCall(A, B, ROOM, "a-tab");
            AtomicInteger success = new AtomicInteger();
            AtomicInteger answered = new AtomicInteger();

            runConcurrently(i -> {
                try {
                    registry.acknowledgeCall(B, "b-tab" + i);
                    success.incrementAndGet();
                } catch (CallAlreadyAnsweredException e) {
                    answered.incrementAndGet();
                }
            });

            assertThat(success.get()).isEqualTo(1);
            assertThat(answered.get()).isEqualTo(THREADS - 1);
        }

        // THREADS개의 스레드가 준비를 마친 뒤 같은 순간에 task를 실행
        private void runConcurrently(IndexedTask task) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < THREADS; i++) {
                int index = i;
                Callable<Void> job = () -> {
                    ready.countDown();
                    start.await();
                    task.run(index);
                    return null;
                };
                futures.add(pool.submit(job));
            }

            ready.await();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            pool.shutdown();
        }
    }

    @FunctionalInterface
    interface IndexedTask {
        void run(int index);
    }
}

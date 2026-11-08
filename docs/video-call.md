## 화상통화 기능 흐름
> 전제: DIRECT(1:1) 방 화면(`room.html`)에 두 사람이 접속해 있다. 채팅용 WebSocket 연결을 그대로 사용한다.

### 구성 요소
| 위치 | 파일 | 역할 |
|---|---|---|
| 브라우저 | `video-call.js` | 통화 화면 상태, WebRTC(`RTCPeerConnection`) 처리 |
| 브라우저 | `ws-client.js` | `/user/queue/rtc`, `/user/queue/errors` 구독, `/pub/rooms/{id}/rtc` 전송 |
| 서버 | `IceServerController` | `GET /api/rtc/ice-servers`: STUN/TURN 주소 + TURN 임시 인증 정보 |
| 서버 | `RtcSignalHandler` | `@MessageMapping("/rooms/{roomId}/rtc")`, 실패 시 보낸 탭의 `/user/queue/errors`로 응답 |
| 서버 | `RtcSignalingService` | 타입별 처리, 상대 탭으로 전달 |
| 서버 | `CallSessionRegistry` | 통화 상태 저장 (메모리, userId → `CallSession`) |
| 서버 | `RtcDisconnectListener` | 탭이 닫히면 통화 정리 |
| 서버 | `CallHistoryService` | 통화 종료 시 `call_history` 저장 + 채팅방 SYSTEM 메시지 |
| 외부 | coturn | STUN/TURN 서버 (영상 데이터 중계) |

### 주소
| 방향 | 주소 | 용도 |
|---|---|---|
| 브라우저 → 서버 | STOMP `SEND /pub/rooms/{id}/rtc` | 시그널 전송 (`targetUserId`, `type`, `sdp`, `ice`) |
| 서버 → 브라우저 | `/user/queue/rtc` | 시그널 수신 (`fromUserId`, `type`, `sdp`, `ice`) |
| 서버 → 브라우저 | `/user/queue/errors` | 실패 응답 (`code`, `message`) |
| 브라우저 → 서버 | `GET /api/rtc/ice-servers` | ICE 서버 목록 |

- `/user/queue/...`는 Spring이 접속(탭)별 주소로 바꿔서 전달한다. 같은 사용자가 탭을 여러 개 열면 탭마다 따로 받는다.
- 서버는 상황에 따라 **사용자의 모든 탭**(`convertAndSendToUser`) 또는 **특정 탭 하나**(세션 id 지정)로 보낸다.

### 통화 상태
| 서버 `CallStatus` | 브라우저 화면 상태 | 의미 |
|---|---|---|
| (없음) | `IDLE` | 통화 없음 |
| `RINGING` | 건 사람 `CALLING` / 받는 사람 `RINGING` | 걸었고 아직 받지 않음 |
| `IN_CALL` | `IN_CALL` | 수락됨. 영상 연결 정보(SDP/ICE) 교환 가능 |

- 한 사용자는 동시에 통화 하나만 할 수 있다. RINGING 중인 사람도 통화 중으로 본다.
- `CallSession`은 건 사람과 받는 사람 두 userId를 키로 함께 저장하고, 통화하는 **탭(세션 id)**도 기록한다.

### 1. 걸기
1. A가 `CALL_REQUEST` 전송
2. 서버 검증: 두 사람 모두 방 참여자, DIRECT 방, 자기 자신이 아님
3. `CallSessionRegistry.createCall` → `RINGING`
   - B가 이미 통화 중이면 A의 탭에 `CALL_BUSY`
4. B의 **모든 탭**에 `CALL_REQUEST` → 벨 울림

### 2. 수락
1. B의 한 탭이 `CALL_ACCEPTED` 전송
2. `acknowledgeCall` → `IN_CALL`, B의 통화 탭 기록
   - 다른 탭이 이미 받았으면 이 탭에 `CALL_ANSWERED_ELSEWHERE`
3. A의 건 탭에 `CALL_ACCEPTED`
4. B의 나머지 탭에 `CALL_ANSWERED_ELSEWHERE` → 벨 꺼짐

### 3. 영상 연결 (WebRTC)
1. 양쪽 모두 카메라·마이크(`getUserMedia`) + `GET /api/rtc/ice-servers` → `RTCPeerConnection` 생성
2. A: `VIDEO_OFFER`(SDP) 전송 → B: `VIDEO_ANSWER`(SDP) 전송
3. 양쪽: 연결 후보를 찾을 때마다 `NEW_ICE_CANDIDATE` 전송
4. 서버는 이 세 타입을 **내용을 보지 않고 상대의 통화 탭에 그대로 전달**한다 (relay)
   - `IN_CALL`이 아니거나 통화 탭이 아닌 곳에서 보내면 거부
5. 연결되면 영상·음성은 서버를 거치지 않고 **브라우저끼리 직접**(또는 TURN 경유) 전송된다

### 4. 종료
| 상황 | 보내는 타입 | 기록 결과 |
|---|---|---|
| 받는 사람이 거절 | `CALL_REJECTED` | `REJECTED` |
| 건 사람이 받기 전 취소 | `CALL_CANCEL` | `MISSED` |
| 통화 중 끊기 | `HANG_UP` | `COMPLETED` |
| 통화 탭이 닫힘 / 연결 끊김 | (서버가 상대에게 `HANG_UP`) | 상태에 따라 `COMPLETED` / `MISSED` |

- `endCall` → Registry에서 두 사람 모두 제거 → 상대에게 같은 타입 전달 → `CallHistoryService.record`
- 기록이 저장되면 채팅방에 SYSTEM 메시지("📞 영상통화 · 2분 5초" 등)가 표시된다.

### 메시지 타입 요약
| 타입 | 보내는 쪽 | 서버가 보내는 곳 |
|---|---|---|
| `CALL_REQUEST` | 건 사람 | 받는 사람의 모든 탭 |
| `CALL_BUSY` | 서버 | 건 사람의 탭 |
| `CALL_ACCEPTED` | 받는 사람 | 건 사람의 탭 |
| `CALL_ANSWERED_ELSEWHERE` | 서버 | 받는 사람의 나머지 탭 |
| `CALL_REJECTED`, `CALL_CANCEL`, `HANG_UP` | 둘 중 한 명 | 상대 (통화 탭이 정해졌으면 그 탭, 아니면 모든 탭) |
| `VIDEO_OFFER`, `VIDEO_ANSWER`, `NEW_ICE_CANDIDATE` | 통화 탭 | 상대의 통화 탭 |

### 실패 응답 (`/user/queue/errors`)
| code | 예 |
|---|---|
| `BAD_REQUEST` | 통화 없이 SDP 전송, 자기 자신에게 전송, 잘못된 메시지 형식 |
| `FORBIDDEN` | 방 참여자가 아님, 통화 탭이 아닌 탭에서 relay |
| `CONFLICT` | 건 사람이 이미 통화 중 |
| `NOT_FOUND` | 없는 방 |

### 테스트
```bash
# 단위 테스트: CallSessionRegistry (서버·DB 불필요)
./mvnw test -Dtest=CallSessionRegistryTest

# 통합 테스트: 실제 서버(임의 포트) + STOMP 클라이언트로 시그널링 검증 (docker compose 실행 필요)
./mvnw test -Dtest=RtcSignalingIntegrationTest
```
| 테스트 | 검증 내용 |
|---|---|
| `CallSessionRegistryTest` (16) | 걸기·수락·종료·연결 끊김 상태 변화, 동시 요청 시 한 건만 성공 |
| `RtcSignalingIntegrationTest` (6) | 전체 통화 흐름, `CALL_BUSY`, 멀티탭 수락, 거절, 연결 끊김, 에러 응답 |

---

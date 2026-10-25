# OCSMEET

## 프로젝트 간략 설명
WebSocket, STOMP 프로토콜 형식에 따라 구현한 간단한 채팅 서비스이다.
REST API(방 관리, 메시지 조회)와 WebSocket(실시간 메시지 전송)을 함께 사용한다.

---

### 주요 기능

#### 1. 인증/인가
| 기능 | 방식 |
|---|---|
| 로그인 | `POST /api/auth/login`: 비밀번호를 BCrypt로 확인한 뒤 access token(1시간)과 refresh token(7일)을 발급 |
| 토큰 갱신 | `POST /api/auth/refresh`: DB에 저장된 refresh token과 대조한 뒤 새 토큰 쌍을 발급 (Rotation) |
| 로그아웃 | `POST /api/auth/logout`: DB에서 refresh token 삭제 |
| REST 요청 인증 | `JwtAuthenticationFilter`: 매 요청의 JWT 검증 |
| WebSocket 연결 인증 | `JwtHandshakeInterceptor`: 핸드셰이크 시 URL의 JWT를 검증하고 userId를 세션에 저장 |
| STOMP 인가 | `StompAuthChannelInterceptor`: `CONNECT` 시 사용자 설정, `SUBSCRIBE`/`SEND` 시 **방 참여자인지** 확인 |

> 회원가입 기능은 없다. 사용자는 DB에 직접 등록한다.

#### 2. 채팅방
| 기능 | API | 설명 |
|---|---|---|
| 방 만들기 | `POST /api/chat-rooms` | 방 종류: `DIRECT`(1:1), `GROUP`, `CHANNEL`. 만든 사람은 OWNER로 참여하고, DIRECT 방은 상대방도 함께 참여 |
| 내 방 목록 | `GET /api/chat-rooms` | 참여 중인 방 + 방별 마지막 메시지 |
| 참여 가능한 방 목록 | `GET /api/chat-rooms/browse` | 아직 참여하지 않은 GROUP/CHANNEL 방 |
| 방 상세 | `GET /api/chat-rooms/{id}` | 방 정보 + 참여자 목록 (참여자만 조회 가능) |
| 방 참여 | `POST /api/chat-rooms/{id}/participants` | 나갔던 방이면 재참여. 차단된 사용자, 닫힌 방, 이미 2명인 DIRECT 방은 거부 |
| 방 나가기 | `DELETE /api/chat-rooms/{id}/participants/me` | 참여 상태를 LEFT로 변경 |
| 방 구독 | STOMP `SUBSCRIBE /sub/rooms/{id}` | 방 화면에 들어가면 실시간 메시지 수신을 시작 |

#### 3. 메시지
| 기능 | 방식 | 설명 |
|---|---|---|
| 메시지 조회 | `GET /api/chat-rooms/{id}/messages?cursor=&size=` | 커서 기반 페이징 (최신순) |
| 메시지 전송 | STOMP `SEND /pub/rooms/{id}/messages` | 검증 → DB 저장 → `/sub/rooms/{id}` 구독자 전원에게 전파 |

---

## 의존성
- Java 17, Spring Boot 3.4.5

| 분류 | 라이브러리 |
|---|---|
| Web | spring-boot-starter-web, spring-boot-starter-validation, spring-boot-starter-websocket |
| 보안 | spring-boot-starter-security, jjwt-api / jjwt-impl / jjwt-jackson |
| 데이터 | mybatis-spring-boot-starter, mariadb-java-client, spring-boot-starter-data-redis |
| 개발 | lombok, spring-boot-devtools, spring-boot-docker-compose |
| 테스트 | spring-boot-starter-test, spring-security-test |

---

## 프로젝트 실행 방법
```bash
# mariadb, redis 실행
docker compose up -d 

# 이미 DB가 있는 경우 아래 명령어를 실행하여 테스트 계정 재생성합니다.
# DB가 없는 경우에는 DB실행시에, 02-seed.sql이 자동 실행됩니다.
# 테스트 사용자 계정 생성 - testuser1, testuser2 테스트 계정이 만들어집니다. (비번은 1234)
docker exec -i mariadb mariadb -uroot -papplication < db/initdb.d/02-seed.sql

# maven-wrapper를 통해 프로젝트 실행
./mvnw spring-boot:run 
```

## 프로젝트 종료 방법
```bash 
# mariadb, redis 종료
docker compose down

# spring project는 ctrl+c로 종료
```

---

## Spring MVC 패턴 특징
- 요청 정보는 **DTO**로 받는다.
- Mybatis Mapper를 사용한다.
- Controller는 `ResponseEntity<응답 타입>`으로 응답한다.

---

## 채팅 기능 흐름
> 전제: 로그인해서 JWT(access token)를 가지고 있다.

### 1. 방 입장 (room.js)
1. REST로 방 정보와 지난 메시지를 불러온다: `GET /api/chat-rooms/{id}`, `/messages`
2. WebSocket 연결: `/ws?token=JWT` (SockJS)
   - `JwtHandshakeInterceptor`: JWT를 검증하고 userId를 세션에 저장한다. 실패하면 연결을 거절한다.
3. STOMP `CONNECT`
   - `StompAuthChannelInterceptor`: 세션의 userId로 사용자(Principal)를 설정한다.
4. STOMP `SUBSCRIBE /sub/rooms/{id}`
   - `StompAuthChannelInterceptor`: 이 방의 참여자인지 확인한다.
   - 통과하면 브로커(SimpleBroker)의 구독자 명단에 등록된다.

### 2. 메시지 전송
STOMP `SEND /pub/rooms/{id}/messages`
- `StompAuthChannelInterceptor`: 이 방의 참여자인지 확인한다.
- `/pub` 경로라서 `@MessageMapping`이 붙은 `ChatMessageHandler`로 전달된다.

### 3. 서버 처리: `ChatMessageService.sendMessage`
1. 빈 문자열이거나 5000자를 초과하는지 확인
2. 방이 존재하고, 이 방의 ACTIVE 참여자인지 확인: `validateActiveParticipant`
3. 방 상태가 ACTIVE인지 확인 (READ_ONLY/CLOSED면 거부): `assertRoomAllowsMessaging`
4. DB에 저장 (INSERT)
5. 다시 조회해서 보낸 사람 닉네임을 포함 (users JOIN)
6. `ChatMessageResponse` DTO로 변환
7. `convertAndSend("/sub/rooms/{id}", response)`
   - 브로커가 구독자 전원(보낸 사람 포함)에게 `MESSAGE` 프레임을 전송한다.

### 4. 화면 표시
각 브라우저의 `onWsMessage`가 `MESSAGE`를 받아 화면에 표시한다.

---

## 로그인 방식 (JWT)

### JWT의 특성
- 서버는 DB 조회 없이 **서명과 만료시간만** 검사한다. 빠르고 서버 확장에 유리하다.
- 대신 **한 번 발급하면 서버가 취소할 수 없다.** 탈취되면 만료될 때까지 누구나 쓸 수 있다.

### 토큰을 둘로 나누는 이유
> **오래 사는 토큰일수록 덜 노출되게 한다.**

| | access token | refresh token |
|---|---|---|
| 용도 | 모든 API 호출 | 새 access token 발급 |
| 수명 | 1시간 | 7일 |
| 전송 빈도 | 매 요청 (많음) | 가끔 (적음) |
| 서버 검증 | 서명만 | 서명 + DB 대조 |

- access token은 **항상 매 요청마다** 전송된다.
- refresh token은 수명이 길어서 서버가 무효화할 수 있어야 하므로 **DB에 저장**한다.

### refresh token의 활용 (`TokenService`)
- **로그아웃:** DB에서 삭제하면 이후 갱신할 수 없다. (`revokeTokens`)
- **갱신 시 대조:** 서명이 유효해도 DB 값과 다르면 거부한다. (`refreshTokens`)
- **Rotation:** 갱신할 때마다 새 토큰으로 덮어써서, 한 번 쓴 토큰은 재사용할 수 없다.

---
## OCSMEET에 화상통화 기능 추가하기

### 1. TURN 자격증명 API ✅
- [x] `GET /api/turn-credentials` (TurnController, TurnCredentialResponse)
- [x] 로그인한 userId로 time-limited credential 발급 (HMAC-SHA1)

### 2. STOMP 시그널링 (`/pub/rooms/{id}/rtc` → `/user/queue/rtc`) ✅
- [x] WebSocketConfig: simple broker에 `/queue` 추가
- [x] DTO: `SignalingMessageRequest`(targetUserId), `SignalingMessageResponse`(fromUserId), `SignalingMessageType`
- [x] RtcSignalHandler / RtcSignalingService: `convertAndSendToUser`로 상대방에게만 전달
- [x] 검증: 보낸 사람·받는 사람 모두 방 참여자, 자기 자신에게 전송 금지
- [x] StompAuthChannelInterceptor: `/user/queue/rtc` 구독은 로그인만 확인, `/rtc` 전송은 방 검증
- [x] 브라우저 2개로 동작·보안 테스트

### 3. 통화 상태 / 멀티탭 처리
- [x] `CallSession`(caller/callee/room/status/탭) + `CallSessionRegistry`(메모리 `ConcurrentHashMap`, `synchronized`)
- [x] 통화 제어 타입 추가: `CALL_REQUEST`, `CALL_ACCEPTED`, `CALL_REJECTED`, `CALL_CANCEL`, `HANG_UP` + 서버 전용 `CALL_BUSY`, `CALL_ANSWERED_ELSEWHERE`
- [x] 상대가 통화 중이면 `CALL_BUSY` (`CalleeBusyException`)
- [x] 멀티탭: 먼저 수락한 탭만 통화, 나머지 탭은 `CALL_ANSWERED_ELSEWHERE` (`SimpUserRegistry`, 세션 지정 전송)
- [x] relay: IN_CALL 상태 + 통화 중인 탭끼리만 OFFER/ANSWER/ICE 전달
- [x] 자동 테스트 20/20 통과 (걸기, 통화 중, 멀티탭 수락, relay 차단, 끊기, 재통화)
- [ ] 브라우저를 닫으면 통화 자동 종료 (`SessionDisconnectEvent`)
- [ ] 실패를 보낸 사람에게 알리기 (`@MessageExceptionHandler`)
- [ ] DIRECT 방에서만 통화 허용

| 받은 메시지 | 보낼 대상과 내용 |
|---|---|
| `CALL_REQUEST` 성공 | 받는 사람의 모든 탭에 `CALL_REQUEST` |
| `CALL_REQUEST` 실패 (상대가 바쁨) | 건 사람의 탭에 `CALL_BUSY` |
| `CALL_ACCEPTED` | 건 사람의 탭에 `CALL_ACCEPTED` + 받는 사람의 나머지 탭에 `CALL_ANSWERED_ELSEWHERE` |
| `CALL_REJECTED`, `CALL_CANCEL`, `HANG_UP` | 상대에게 같은 타입 (상대 탭이 정해졌으면 그 탭, 아니면 모든 탭) |
| `VIDEO_OFFER`, `VIDEO_ANSWER`, `NEW_ICE_CANDIDATE` | 상대의 통화 중인 탭에 그대로 전달 (relay) |

### 4. 프론트엔드
- [ ] 

### 5. HTTPS / coturn
- [ ] 

### 6. 통화 기록
- [ ] 

### 7. 테스트 / 문서
- [ ] 
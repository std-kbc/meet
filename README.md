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

## 실행 방법
```bash
docker compose up -d
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

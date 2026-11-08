# OCSMEET

## 프로젝트 간략 설명
WebSocket, STOMP 프로토콜 형식에 따라 구현한 간단한 채팅 서비스이다.
REST API(방 관리, 메시지 조회)와 WebSocket(실시간 메시지 전송)을 함께 사용한다.

실세 서비스 링크: [https://ocsmeet.kmjoyit.xyz](https://ocsmeet.kmjoyit.xyz)
테스트계정1(ID/PW): user1/ocsmeet1!
테스트계정2(ID/PW): user2/ocsmeet1!

![실행화면](./readme_img_src/스크린샷%202026-11-01%20오후%203.06.16.png)
![실시간통신로그기능](./readme_img_src/스크린샷%202026-11-01%20오후%2011.05.51.png)
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

## 화상통화 기능 흐름
구성 요소, 시그널링 주소, 통화 상태, 메시지 타입, 테스트 방법은 [docs/video-call.md](docs/video-call.md)에 정리했다.

---
## OCSMEET에 화상통화 기능 추가하기

### 1. ICE 서버 API (STUN + TURN) ✅
- [x] `GET /api/rtc/ice-servers` (IceServerController, IceServerResponse): STUN + TURN
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
- [x] 브라우저를 닫으면 통화 자동 종료 (`SessionDisconnectEvent` → 통화 중인 탭일 때만 상대에게 `HANG_UP`)
- [x] 자동 테스트 24/24 통과 (걸기, 통화 중, 멀티탭 수락, relay 차단, 끊기, 재통화, 연결 끊김)
- [x] 실패를 보낸 사람에게 알리기 (`@MessageExceptionHandler` → 보낸 탭의 `/user/queue/errors`로 `ErrorResponse`)
- [x] DIRECT 방에서만 통화 허용 (`ChatRoomService.validateDirectRoom`)
- [x] DIRECT 방 자동 테스트 16/16, 오류 알림 테스트 8/8 통과

| 받은 메시지 | 보낼 대상과 내용 |
|---|---|
| `CALL_REQUEST` 성공 | 받는 사람의 모든 탭에 `CALL_REQUEST` |
| `CALL_REQUEST` 실패 (상대가 바쁨) | 건 사람의 탭에 `CALL_BUSY` |
| `CALL_ACCEPTED` | 건 사람의 탭에 `CALL_ACCEPTED` + 받는 사람의 나머지 탭에 `CALL_ANSWERED_ELSEWHERE` |
| `CALL_REJECTED`, `CALL_CANCEL`, `HANG_UP` | 상대에게 같은 타입 (상대 탭이 정해졌으면 그 탭, 아니면 모든 탭) |
| `VIDEO_OFFER`, `VIDEO_ANSWER`, `NEW_ICE_CANDIDATE` | 상대의 통화 중인 탭에 그대로 전달 (relay) |

### 4. 프론트엔드 ✅
#### 4-1. 통화 UI + 통화 제어 (영상 없이) ✅
- [x] `room.html`: 영상통화 버튼, 통화 패널(상태 문구, 영상 영역, 수락/거절/취소/끊기 버튼), `video-call.js` 스크립트 추가
- [x] `app.css`: 통화 패널 스타일
- [x] `ws-client.js`: `subscribeUserQueue`(`/user/queue/rtc`, `/user/queue/errors` 구독), `sendRtc`(`/pub/rooms/{id}/rtc` 전송)
- [x] `video-call.js`(새 파일): 화면 상태 `IDLE`/`CALLING`/`RINGING`/`IN_CALL`, 버튼 동작, 수신 타입별 처리
- [x] `room.js`: DIRECT 방이면 상대 참여자를 찾아 `VideoCall.init(roomId, partnerId, partnerName)`
- [x] 브라우저 테스트 (5번 DIRECT 방, user1 ↔ user2): 걸기·수락·끊기·거절·취소, 멀티탭 `ANSWERED_ELSEWHERE`

#### 4-2. WebRTC 영상 연결 ✅
- [x] `GET /api/rtc/ice-servers`로 ICE 서버를 받아 `RTCPeerConnection` 생성
- [x] `getUserMedia`로 카메라·마이크 → `localVideo`, 트랙을 PeerConnection에 추가
- [x] 건 사람: `CALL_ACCEPTED` 수신 후 `VIDEO_OFFER` 전송 (그 전에 보내면 서버가 거절)
- [x] 받는 사람: `VIDEO_OFFER` 수신 → `VIDEO_ANSWER` 전송
- [x] `onicecandidate` → `NEW_ICE_CANDIDATE` 전송 (ice는 `JSON.stringify`), 수신 시 `addIceCandidate`
- [x] `ontrack` → `remoteVideo` 표시
- [x] 통화 종료 시 정리: PeerConnection close, 카메라 트랙 stop
- [x] 카메라 권한 거부·장치 없음 처리
- [x] 브라우저 테스트: 실제 영상·음성 통화
- [x] 통화 중 레이아웃: 왼쪽 영상, 오른쪽 채팅

### 5. HTTPS / coturn ✅
#### 5-1. HTTPS ✅
- [x] mkcert 인증서(`cert/ocsmeet.p12`, git 제외) + `https` 프로필(`application-https.properties`, 8443)
- [x] 실행: `./mvnw spring-boot:run -Dspring-boot.run.profiles=https`
- [x] 같은 와이파이의 휴대폰에서 `https://{Mac IP}:8443` 접속·영상통화 성공
- 주의: Mac IP가 바뀌면(DHCP) 인증서를 새 IP로 다시 생성

#### 5-2. coturn + ICE 서버 설정 환경변수화 ✅
- [x] ICE 서버 설정을 `-D` 시스템 프로퍼티 대신 `application.properties` + 환경변수(`RTC_STUN_URL`, `RTC_TURN_URLS`, `RTC_TURN_SECRET`)로
- [x] 로컬 환경변수 파일(`.env.rtc`, git 제외)
- [x] coturn 실행 (`--use-auth-secret --static-auth-secret`, relay 포트 범위 지정)

#### 5-3. TURN 검증 ✅
- [x] `turnutils_uclient`로 발급 credential 할당 성공 / 틀린 credential 거절 확인
- [x] `room.html?id=5&relay` → `iceTransportPolicy: 'relay'`로 TURN 경유 통화 성공 (Mac ↔ 휴대폰)

#### 화상통화 테스트 실행 방법 (같은 와이파이)
```bash
# 0) Mac IP 확인. 바뀌었으면 인증서와 .env.rtc의 IP를 새 IP로
ipconfig getifaddr en0
mkcert -pkcs12 -p12-file cert/ocsmeet.p12 localhost 127.0.0.1 {Mac IP}

# .env.rtc (git 제외)
export RTC_TURN_SECRET={openssl rand -hex 32 결과}
export RTC_STUN_URL=stun:{Mac IP}:53478
export RTC_TURN_URLS=turn:{Mac IP}:53478

# 1) 터미널 1: coturn
source .env.rtc
turnserver -p 53478 \
  --listening-ip={Mac IP} --relay-ip={Mac IP} \
  --realm=ocsmeet \
  --use-auth-secret --static-auth-secret="$RTC_TURN_SECRET" \
  --min-port=49160 --max-port=49200 \
  --fingerprint --no-tls --no-dtls --no-cli \
  --log-file=stdout --verbose

# 2) 터미널 2: Spring (HTTPS)
source .env.rtc
./mvnw spring-boot:run -Dspring-boot.run.profiles=https

# 3) 접속: https://{Mac IP}:8443/room.html?id={DIRECT 방 번호}   (TURN 강제: 뒤에 &relay)
```

### 6. 통화 기록 ✅
- [x] `call_history` 테이블 (`db/initdb.d/03-call-history.sql`): caller, callee, room, 결과, 건·받은·종료 시각
- [x] 결과 `CallResult`: `COMPLETED`(연결 후 종료), `REJECTED`(거절), `MISSED`(받기 전 종료). `CALL_BUSY`는 기록 안 함
- [x] 통화 종료 지점(`handleEndCall`, `handleDisconnect`)에서 `CallHistoryService.record` 호출 (실패해도 통화 종료 처리에는 영향 없음)
- [x] 채팅방에 `SYSTEM` 메시지로 표시 (`ChatMessageService.sendSystemMessage`): "📞 영상통화 · 2초", "📞 거절된 영상통화", "📞 부재중 영상통화"
- [x] 화면: 시스템 메시지를 가운데 회색 안내로 표시
- [x] 자동 테스트 5/5 (완료·거절·부재중·탭 닫힘)
- 기존 DB에는 직접 적용: `docker exec -i mariadb mariadb -uroot -papplication < db/initdb.d/03-call-history.sql`

### 7. 테스트 / 문서 ✅
- [x] `CallSessionRegistry` 단위 테스트 16개 (걸기·수락·종료·연결 끊김·동시 요청)
- [x] 시그널링 통합 테스트 6개 (`WebSocketStompClient`, `ChatRoomService`·`CallHistoryService`는 Mock)
- [x] README에 화상통화 흐름(구성 요소, 주소, 통화 상태, 메시지 타입, 실패 응답, 테스트) 정리

### 7-추가. WebRTC 연결 정보 패널 ✅
- [x] `rtc-monitor.js`: 방 헤더에 나/상대, 시그널 로그(OFFER/ANSWER/ICE 후보), 연결 상태, 채택된 경로(host/srflx/relay + IP), RTT, 송수신 속도·누적량 표시
- [x] 전송량은 `pc.getStats()`의 채택된 candidate pair를 1초마다 조회 (9단계 사용량 측정의 브라우저 쪽 데이터)

### 개선 백로그 (나중에)
- [ ] 같은 탭에서 보낸 메시지의 처리 순서 보장: 서버 inbound 채널이 스레드 풀이라 `VIDEO_OFFER`보다 `NEW_ICE_CANDIDATE`가 먼저 전달될 수 있음 (`StompEndpointRegistry.setPreserveReceiveOrder(true)` 검토)
- [ ] RINGING 타임아웃: 일정 시간 안 받으면 자동 취소 (받는 사람이 모든 탭을 닫은 경우 포함)
- [ ] 다른 방 화면에서도 전화를 받을 수 있는 전역 수신 UI (지금은 그 방 화면에서만 벨이 울림)
- [ ] 서버 여러 대 운영 시 `CallSessionRegistry`를 Redis로 이전
- [ ] `CallerBusyException` 메시지 정리 ("caller is already on call")
- [ ] 운영 프로필에서 `RTC_TURN_SECRET` 기본값 제거 (환경변수 없으면 서버 시작 실패하도록)
- [ ] 다른 네트워크 간 통화: 공인 IP, 포트포워딩(8443, 53478, relay 포트 범위), coturn `-X 공인IP/내부IP` → 8단계에서 Lightsail로 해결

---
## AWS Lightsail 배포 / 비용 기반 통화 제어

### 8. AWS Lightsail 배포 (인스턴스 1대 + Docker Compose, 보유 도메인)
#### 8-1. 운영 설정 준비 (로컬에서) ✅
- [x] `application-prod.properties`: 기본값 없는 환경변수로 덮어쓰기
  - DB: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` / Redis: `REDIS_HOST`, `REDIS_PASSWORD`
  - `JWT_SECRET` (지금 `application.properties`의 키는 개발용), `COOKIE_DOMAIN`
  - `RTC_STUN_URL`, `RTC_TURN_URLS`, `RTC_TURN_SECRET`
  - `server.forward-headers-strategy` 설정 (Caddy 뒤에서 https 요청으로 인식)
  - JWT·TURN 값이 없으면 시작 실패, DB·Redis 값이 틀리면 첫 요청 때 에러
- [x] `Dockerfile` (멀티 스테이지: Maven 빌드 → Java 17 JRE, `TZ=Asia/Seoul`, root 아닌 사용자로 실행) + `.dockerignore`
- [x] `compose.prod.yaml`: app, mariadb, redis, coturn, Caddy (`name: ocsmeet-prod`로 프로젝트 이름 고정)
- [x] `Caddyfile`: `{$DOMAIN}` → `app:8080` (WebSocket 포함, Let's Encrypt 자동 발급·갱신)
- [x] DB·Redis·app 포트는 외부에 열지 않음, DB 초기화는 스키마만 (`02-seed.sql` 테스트 계정 제외)
- [x] coturn: `network_mode: host`, `--external-ip`, 내부망 중계 금지(`--denied-peer-ip`)
- [x] `.env.prod.example` (변수 이름만 커밋) + 실제 `.env`는 git 제외
- [x] 로컬에서 prod compose 실행 확인 (`DOMAIN=localhost` → Caddy 자체 인증서)
- 실행 (로컬 확인·서버 공통): `docker compose -f compose.prod.yaml up -d --build`

#### 8-2. Lightsail 리소스 ✅
- [ ] AWS Budgets 알림 설정 (예상치 못한 요금 방지)
- [x] Lightsail 인스턴스: 2GB, Dual-stack(IPv4 포함), 서울, Ubuntu 24.04
- [x] 플랜의 월 데이터 전송 허용량·초과 단가 기록 (9단계 판단 기준) - 3TB, 초과 $0.13/GB(서울, 나가는 트래픽만)
- [x] Static IP 연결
- [x] Lightsail 방화벽: 22(내 IP만 + 브라우저 SSH), 80/443, 3478(UDP/TCP), 49160-49200(UDP)
- [x] 도메인 DNS A 레코드 → Static IP
- 참고: 22번 포트가 막힌 네트워크에서는 콘솔의 브라우저 SSH 사용

#### 8-3. 배포 ✅
- [x] 스왑 2GB 추가 (서버에서 이미지 빌드 시 메모리 부족 방지)
- [x] Docker 설치 (`get.docker.com`), `ubuntu` 사용자 docker 그룹 추가 (다시 로그인해야 적용)
- [x] `git clone` 후 서버 전용 `.env` 작성 (비밀값은 서버에서 `openssl rand`로 생성, `chmod 600`)
- [x] `docker compose -f compose.prod.yaml up -d --build`
- [x] 운영 계정 생성: `./scripts/create-user.sh <username> <nickname>` (Caddy `hash-password`로 bcrypt 해시)
- [x] `https://{도메인}` 접속, 로그인 확인
- 겪은 문제: `.gitignore`의 `docs` 규칙이 `src/.../docs` 패키지까지 제외 → 서버 컴파일 실패 (규칙 삭제로 해결), 큰 파일 push 시 HTTP 400 → `git config http.postBuffer 157286400`
- 코드 반영: 로컬에서 push → 서버에서 `git pull` → `docker compose -f compose.prod.yaml up -d --build`

#### 8-4. coturn 검증 (다른 네트워크 간 통화) ✅
- [x] coturn `--external-ip={공인IP}/{사설IP}` (Lightsail 인스턴스는 NAT 뒤에 있음)
- [x] `--allowed-peer-ip={사설IP}`: 양쪽 모두 TURN을 쓸 때 서버 자신의 사설 IP가 `--denied-peer-ip`에 막히지 않게
- [x] `&relay`로 TURN 경유 통화 성공
- 로그 확인: `./scripts/logs.sh` (app), `docker compose -f compose.prod.yaml logs -f coturn`

#### 8-5. TURN 비용 보호 ✅
TURN은 영상을 서버가 대신 전달하므로 서버 전송량(=비용)이 생긴다. P2P 통화는 서버를 거치지 않아 비용이 없다.

| 장치 | 위치 | 하는 일 |
|---|---|---|
| `--max-bps=125000` | coturn | 통로(할당) 1개의 속도 상한 125KB/s(1Mbps). 넘으면 브라우저가 화질을 낮춤 |
| `--bps-capacity=500000` | coturn | 서버 전체 속도 상한 500KB/s. 통로를 빌려줄 때마다 125,000씩 미리 차감 → **통로 최대 4개** |
| `rtc.turn-ttl-seconds=3600` | `application-prod.properties` | TURN credential 유효 시간 1시간 (유출돼도 오래 못 씀) |
| 20초 후 종료 | `video-call.js` | relay로 연결되면 20초 뒤 안내와 함께 통화 종료 (사용자 안내용, 우회 가능) |

- 실제 비용 상한은 **coturn 설정**이 보장한다. 브라우저 코드(20초 종료)는 수정·우회할 수 있어서 비용 보호 장치가 아니다.
- 값 변경: 서버 `.env`에 `TURN_MAX_BPS`, `TURN_BPS_CAPACITY` (바이트/초)

**동시 TURN 통화 수** (통로 4개 기준)
| 통화 형태 | 통로 사용 | 동시 통화 |
|---|---|---|
| 두 사람 모두 TURN | 4개 (사람당 UDP 1 + TCP 1) | **1건** |
| 한 사람만 TURN | 2개 | 2건 |
| P2P | 0개 | 제한 없음 |

통로가 없으면 coturn이 거절(`486`)하고, 브라우저는 P2P로만 연결을 시도한다.
`동시 TURN 통화 수 × 통화당 속도 ≤ bps-capacity` → 비용을 유지하면서 동시 통화를 늘리려면 `max-bps`를 낮춘다(화질 저하).

**최대 비용** (Lightsail 2GB 플랜: 월 $12, 전송 허용량 3TB, 받는 양+보내는 양 합산)
| 최악의 경우 (30일 내내 최대 속도) | 양 |
|---|---|
| 보내는 양 500KB/s × 2,592,000초 | 약 1.3TB |
| 받는 양 (coturn이 따로 제한) | 약 1.3TB |
| 합계 | **약 2.6TB < 3TB → 초과 요금 0원, 최대 월 $12** |
- relay 테스트 링크: https://{도메인}/room.html?id={방 번호}&relay
- `bps-capacity` 한계: 약 520,000 (31일 × 받기·보내기 합계가 3TB 미만). 더 올리면 초과 요금($0.13/GB, 나가는 양) 가능
- 실제로는 TURN 통화가 20초에 끝나서 1건당 약 20MB
- coturn 제한 밖인 웹 트래픽 등은 AWS Budgets 알림으로 감시
- 확인 필요: P2P로 연결되는 통화도 후보 수집 중 잠깐 통로를 빌릴 수 있음 (coturn 로그로 반납 시점 확인)

### 9. 비용 기반 통화 제어 (전송 허용량 초과 예측 시 TURN만 차단, P2P 허용) — 보류
> 8-5의 coturn 상한으로 최대 비용이 이미 고정되어 당장은 필요 없음. 사용량 통계·예측이 필요해지면 진행.

- [ ] coturn 통계를 Spring이 읽을 수 있게 설정 (로그 또는 Prometheus)
- [ ] 측정: coturn 통계로 relay 전송량을 사용자별·일별 저장 (username `만료시각:userId`), 브라우저 `getStats()`는 참고·대조용
- [ ] 설정(`rtc.cost.*`): 플랜 월 전송 허용량(GB), 차단 기준(예: 90%), 초과 단가(참고용)
- [ ] 예측: 예상 전송량 = 이번 달 누적 + 최근 7일 하루 평균 × 남은 일수 (`@Scheduled`로 주기 계산)
- [ ] 교차 검증: Lightsail 인스턴스 지표(`NetworkOut`, Lightsail API)와 측정값 비교, AWS Budgets 알림을 최후 안전장치로
- [ ] 제한: 예상 전송량이 허용량 × 차단 기준을 넘으면 `/api/rtc/ice-servers`가 TURN을 빼고 STUN만 반환
- [ ] TURN credential 유효 시간 단축 (지금 24시간 → 이미 발급된 credential로 계속 쓰는 것 방지)
- [ ] 화면: relay 없이 연결에 실패하면 안내 문구
- [ ] 확인: 관리자용 비용 조회 API, AWS Cost Explorer 실제 요금과 예측 비교

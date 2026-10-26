package com.std.chat.rtc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.std.chat.auth.security.SecurityUtil;
import com.std.chat.rtc.dto.IceServerResponse;

/**
 * Time-Limited ICE 서버(STUN + TURN) 설정 발급 엔드포인트 (RFC 8489 기반)
 *
 * username = "{만료 unix timestamp}:{사용자명}"
 * credential = base64( HMAC-SHA1(shared_secret, username) )
 *
 * coturn 실행 옵션:
 * --use-auth-secret --static-auth-secret=${turn.secret}
 *
 * JVM 시스템 프로퍼티로 주입 (-D 옵션):
 * -Dturn.secret=<shared_secret>
 * -Dturn.url.internal=turn:<내부IP>:53478
 * -Dturn.url.external=turn:<외부IP>:53478
 * -Dstun.url=stun:<외부IP>:53478
 */
@RestController
@RequestMapping("/api/rtc")
public class IceServerController {

    private static final Logger log = LoggerFactory.getLogger(IceServerController.class);

    private static final String SHARED_SECRET = System.getProperty("turn.secret", "change_this_secret");
    private static final String STUN_URL = System.getProperty("stun.url", "stun:61.73.25.18:53478");
    private static final String URL_INTERNAL = System.getProperty("turn.url.internal", "turn:192.168.0.90:53478");
    private static final String URL_EXTERNAL = System.getProperty("turn.url.external", "turn:61.73.25.18:53478");
    private static final long TTL_SECONDS = 86400; // 24시간

    @GetMapping("/ice-servers")
    public ResponseEntity<?> getIceServers() {
        Long userId = SecurityUtil.getCurrentUserId();

        try {
            long expiry = Instant.now().getEpochSecond() + TTL_SECONDS;
            String username = expiry + ":" + userId;
            String credential = hmacSha1(SHARED_SECRET, username);

            return ResponseEntity.ok(new IceServerResponse(Arrays.asList(
                    new IceServerResponse.IceServer(STUN_URL, null, null),
                    new IceServerResponse.IceServer(URL_INTERNAL, username, credential),
                    new IceServerResponse.IceServer(URL_EXTERNAL, username, credential))));

        } catch (Exception e) {
            log.error("ice servers 응답 실패", e);
            return ResponseEntity.internalServerError().body("ice servers 응답 실패");
        }
    }

    private String hmacSha1(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}

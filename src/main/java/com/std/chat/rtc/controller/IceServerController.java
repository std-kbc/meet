package com.std.chat.rtc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.std.chat.auth.security.SecurityUtil;
import com.std.chat.rtc.dto.IceServerResponse;

/**
 * Time-Limited ICE 서버(STUN + TURN) 설정 발급 엔드포인트 (RFC 8489 기반)
 *
 * username = "{만료 unix timestamp}:{사용자명}"
 * credential = base64( HMAC-SHA1(turnSecret, username) )
 *
 * coturn 실행 옵션:
 * --use-auth-secret --static-auth-secret=${turn.secret}
 *
 * JVM 시스템 프로퍼티로 주입 (-D 옵션):
 * -Dturn.secret=<turnSecret>
 * -Dturn.url.internal=turn:<내부IP>:53478
 * -Dturn.url.external=turn:<외부IP>:53478
 * -Dstun.url=stun:<외부IP>:53478
 */
@RestController
@RequestMapping("/api/rtc")
public class IceServerController {

    private static final Logger log = LoggerFactory.getLogger(IceServerController.class);

    @Value("${rtc.stun-url}")
    private String stunUrl;

    @Value("${rtc.turn-urls}")
    private List<String> turnUrls;

    @Value("${rtc.turn-secret}")
    private String turnSecret;

    @Value("${rtc.turn-ttl-seconds}")
    private long ttlSeconds;

    @GetMapping("/ice-servers")
    public ResponseEntity<?> getIceServers() {
        Long userId = SecurityUtil.getCurrentUserId();

        try {
            long expiry = Instant.now().getEpochSecond() + ttlSeconds;
            String username = expiry + ":" + userId;
            String credential = hmacSha1(turnSecret, username);

            List<IceServerResponse.IceServer> iceServers = new ArrayList<>();
            iceServers.add(new IceServerResponse.IceServer(stunUrl, null, null));
            for (String turnUrl : turnUrls) {
                iceServers.add(new IceServerResponse.IceServer(turnUrl, username, credential));
            }
            return ResponseEntity.ok(new IceServerResponse(iceServers));

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

package com.std.chat.rtc.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CallSession {
    private Long callerId;
    private Long calleeId;
    private Long roomId;
    private CallStatus status;
    private String callerSessionId;
    private String calleeSessionId;
    private LocalDateTime requestedAt; // 건 시각
    private LocalDateTime answeredAt; // 받은 시각 (받기 전이면 null)

    // 이 통화에서 userId의 상대방
    public Long partnerOf(Long userId) {
        return callerId.equals(userId) ? calleeId : callerId;
    }

    // 이 통화에서 userId가 쓰고 있는 탭 (callee가 아직 받기 전이면 null)
    public String sessionIdOf(Long userId) {
        return callerId.equals(userId) ? callerSessionId : calleeSessionId;
    }

}
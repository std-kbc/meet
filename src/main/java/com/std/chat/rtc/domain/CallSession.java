package com.std.chat.rtc.domain;

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
    
    // 이 통화에서 userId의 상대방
    public Long partnerOf(Long userId) {
        return callerId.equals(userId) ? calleeId : callerId;
    }

    // 이 통화에서 userId가 쓰고 있는 탭 (callee가 아직 받기 전이면 null)
    public String sessionIdOf(Long userId) {
        return callerId.equals(userId) ? callerSessionId : calleeSessionId;
    }

}
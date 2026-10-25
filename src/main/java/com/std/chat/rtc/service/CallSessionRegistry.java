package com.std.chat.rtc.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.std.chat.rtc.domain.CallSession;
import com.std.chat.rtc.domain.CallStatus;
import com.std.chat.rtc.exception.CallAlreadyAnsweredException;
import com.std.chat.rtc.exception.CalleeBusyException;
import com.std.chat.rtc.exception.CallerBusyException;
import com.std.chat.common.exception.BadRequestException;

@Component
public class CallSessionRegistry {

    private final Map<Long, CallSession> calls = new ConcurrentHashMap<>();

    // 전화 걸기
    public synchronized CallSession createCall(
                            Long callerId,
                            Long calleeId, 
                            Long roomId, 
                            String calleSessionId) 
    {
        // validation
        if(calls.get(callerId) != null) {
            throw new CallerBusyException("caller is already on call");
        }else if (calls.get(calleeId) != null) {
            throw new CalleeBusyException("callee is already on call");
        }

        // create callSession
        CallSession callSession = new CallSession();
        callSession.setRoomId(roomId);
        callSession.setCallerId(callerId);
        callSession.setCalleeId(calleeId);
        callSession.setCallerSessionId(calleSessionId);
        callSession.setStatus(CallStatus.RINGING);

        // register
        calls.put(callerId, callSession);
        calls.put(calleeId, callSession);
        return callSession;
    }

    // 수락
    public synchronized CallSession acknowledgeCall(Long calleeId, String calleeSessionId) {
        
        // check is session of callee exist
        CallSession callSession = calls.get(calleeId);
        if(callSession == null) {
            throw new BadRequestException("no call is ringing");
        }else if(!calleeId.equals(callSession.getCalleeId())) {
            throw new BadRequestException("only callee can accept");
        }
        
        // check is the session is available
        if(CallStatus.IN_CALL == callSession.getStatus()) {
            throw new CallAlreadyAnsweredException("callee is already on call");
        }

        // set callee's session id
        callSession.setCalleeSessionId(calleeSessionId);
        callSession.setStatus(CallStatus.IN_CALL);

        return callSession;
    }

    // 종료
    public synchronized CallSession endCall(Long userId) {
        CallSession callSession = calls.get(userId);
        if(callSession == null) {
            throw new BadRequestException("no call exist");
        }
        calls.remove(callSession.getCalleeId());
        calls.remove(callSession.getCallerId());
        return callSession;
    }

    // 조회
    public synchronized CallSession getCall(Long userId){
        return calls.get(userId);
    }
}

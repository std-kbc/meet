package com.std.chat.rtc.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.std.chat.message.service.ChatMessageService;
import com.std.chat.rtc.domain.CallHistory;
import com.std.chat.rtc.domain.CallResult;
import com.std.chat.rtc.domain.CallSession;
import com.std.chat.rtc.mapper.CallHistoryMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallHistoryService {

    private final CallHistoryMapper callHistoryMapper;
    private final ChatMessageService chatMessageService;

    @Transactional
    public void record(CallSession callSession, CallResult result) {
        CallHistory history = new CallHistory();
        history.setChatRoomId(callSession.getRoomId());
        history.setCallerId(callSession.getCallerId());
        history.setCalleeId(callSession.getCalleeId());
        history.setResult(result);
        history.setRequestedAt(callSession.getRequestedAt());
        history.setAnsweredAt(callSession.getAnsweredAt());
        history.setEndedAt(LocalDateTime.now());
        callHistoryMapper.insert(history);

        chatMessageService.sendSystemMessage(callSession.getRoomId(), callSession.getCallerId(), toMessage(history));
    }

    private String toMessage(CallHistory history) {
        return switch (history.getResult()) {
            case COMPLETED ->
                "📞 영상통화 · " + formatDuration(Duration.between(history.getAnsweredAt(), history.getEndedAt()));
            case REJECTED -> "📞 거절된 영상통화";
            case MISSED -> "📞 부재중 영상통화";
        };
    }

    private String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long seconds = duration.toSecondsPart();
        return minutes > 0 ? minutes + "분 " + seconds + "초" : seconds + "초";
    }
}

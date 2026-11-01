package com.std.chat.rtc.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CallHistory {
    private Long id;
    private Long chatRoomId;
    private Long callerId;
    private Long calleeId;
    private CallResult result;
    private LocalDateTime requestedAt;
    private LocalDateTime answeredAt;
    private LocalDateTime endedAt;
}

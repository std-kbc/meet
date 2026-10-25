package com.std.chat.rtc.domain;

public enum SignalingMessageType {
    CALL_REQUEST,
    CALL_REJECTED,
    CALL_ACCEPTED,
    CALL_BUSY, // 상대가 통화 중
    CALL_ANSWERED_ELSEWHERE, // 다른 탭에서 받음
    VIDEO_OFFER,
    VIDEO_ANSWER,
    NEW_ICE_CANDIDATE,
    HANG_UP,
    CALL_CANCEL
}

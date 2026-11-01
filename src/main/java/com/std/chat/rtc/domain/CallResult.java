package com.std.chat.rtc.domain;

public enum CallResult {
    COMPLETED, // 연결된 통화가 정상 종료
    REJECTED, // 받는 사람이 거절
    MISSED // 받기 전에 종료 (건 사람 취소, 연결 끊김)
}

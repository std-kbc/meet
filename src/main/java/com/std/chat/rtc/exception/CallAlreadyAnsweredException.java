package com.std.chat.rtc.exception;

import com.std.chat.common.exception.ConflictException;

public class CallAlreadyAnsweredException extends ConflictException{
    
    public CallAlreadyAnsweredException(String message) {
        super(message);
    }
}

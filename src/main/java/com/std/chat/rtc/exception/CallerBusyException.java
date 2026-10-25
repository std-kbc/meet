package com.std.chat.rtc.exception;

import com.std.chat.common.exception.ConflictException;

public class CallerBusyException extends ConflictException{
    
    public CallerBusyException(String message) {
        super(message);
    }
}

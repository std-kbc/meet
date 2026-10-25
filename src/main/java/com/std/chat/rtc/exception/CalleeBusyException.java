package com.std.chat.rtc.exception;

import com.std.chat.common.exception.ConflictException;

public class CalleeBusyException extends ConflictException{
    
    public CalleeBusyException(String message) {
        super(message);
    }
}

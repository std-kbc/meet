package com.std.chat.rtc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import lombok.Getter;
import com.std.chat.rtc.domain.SignalingMessageType;

@Getter
@Setter
@Schema(description = "시그널링 메시지 요청")
public class SignalingMessageRequest {
    
    @NotNull
    Long targetUserId;
    
    @NotNull
    SignalingMessageType type;
    
    String ice;
    
    String sdp;
}


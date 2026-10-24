package com.std.chat.rtc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import lombok.Getter;
import com.std.chat.rtc.domain.SignalingMessageType;

@Getter
@Setter
@Schema(description = "시그널링 메시지 응답")
public class SignalingMessageResponse {

    Long fromUserId;
    
    SignalingMessageType type;
    
    String ice;
    
    String sdp;
}

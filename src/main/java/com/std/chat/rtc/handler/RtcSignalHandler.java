package com.std.chat.rtc.handler;

import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import com.std.chat.rtc.service.RtcSignalingService;
import com.std.chat.rtc.dto.SignalingMessageRequest;
import jakarta.validation.Valid;

@Controller
public class RtcSignalHandler {
    
	private final RtcSignalingService rtcSignalingService;

	public RtcSignalHandler(RtcSignalingService rtcSignalingService) {
		this.rtcSignalingService = rtcSignalingService;
	}

    @MessageMapping("/rooms/{roomId}/rtc")
	public void sendMessage(@DestinationVariable Long roomId,
							@Valid @Payload SignalingMessageRequest request,
							Principal principal,
							SimpMessageHeaderAccessor headerAccessor) {
		
		String sessionId = headerAccessor.getSessionId();
								Long userId = Long.parseLong(principal.getName());
		rtcSignalingService.sendSignal(userId, roomId, sessionId, request);
	}
}

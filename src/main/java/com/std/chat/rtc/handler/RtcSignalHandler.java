package com.std.chat.rtc.handler;

import java.security.Principal;

import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import com.std.chat.rtc.service.RtcSignalingService;
import com.std.chat.common.dto.ErrorResponse;
import com.std.chat.common.exception.BadRequestException;
import com.std.chat.common.exception.ConflictException;
import com.std.chat.common.exception.ForbiddenException;
import com.std.chat.common.exception.ResourceNotFoundException;
import com.std.chat.rtc.dto.SignalingMessageRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

	@MessageExceptionHandler
	@SendToUser(destinations = "/queue/errors", broadcast = false)
	public ErrorResponse handleException(Exception ex) {
		String code = errorCode(ex);
		log.warn("rtc signal failed: code={}, message={}", code, ex.getMessage());
		String message = code.equals("INTERNAL_ERROR") ? "Internal server error" : ex.getMessage();
		return ErrorResponse.builder().code(code).message(message).build();
	}

	private String errorCode(Exception ex) {
		if (ex instanceof ResourceNotFoundException)
			return "NOT_FOUND";
		if (ex instanceof ForbiddenException)
			return "FORBIDDEN";
		if (ex instanceof ConflictException)
			return "CONFLICT";
		if (ex instanceof BadRequestException
				|| ex instanceof MethodArgumentNotValidException
				|| ex instanceof MessageConversionException)
			return "BAD_REQUEST";
		return "INTERNAL_ERROR";
	}

}

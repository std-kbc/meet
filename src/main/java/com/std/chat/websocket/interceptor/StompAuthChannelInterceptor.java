package com.std.chat.websocket.interceptor;

import com.std.chat.auth.security.JwtUtil;
import com.std.chat.common.exception.ForbiddenException;
import com.std.chat.room.service.ChatRoomService;
import com.std.chat.websocket.support.StompDestinationUtils;
import com.std.chat.websocket.support.WebSocketAuthAttributes;
import com.std.chat.websocket.support.WebSocketUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private final JwtUtil jwtUtil;
	private final ChatRoomService chatRoomService;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}

		if (StompCommand.CONNECT.equals(accessor.getCommand())) {
			resolveUser(accessor);
			if (accessor.getUser() == null) {
				log.warn("STOMP CONNECT rejected: unauthenticated");
				return null;
			}
			return message;
		}

		if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
			validateRoomAccess(accessor, StompDestinationUtils.extractRoomIdFromSubscribe(accessor.getDestination()));
			return message;
		}

		if (StompCommand.SEND.equals(accessor.getCommand())) {
			validateRoomAccess(accessor, StompDestinationUtils.extractRoomIdFromSend(accessor.getDestination()));
			return message;
		}

		return message;
	}

	private void resolveUser(StompHeaderAccessor accessor) {
		if (accessor.getUser() != null) {
			return;
		}

		Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
		if (sessionAttributes != null && sessionAttributes.get(WebSocketAuthAttributes.USER_ID) != null) {
			Long userId = (Long) sessionAttributes.get(WebSocketAuthAttributes.USER_ID);
			String role = (String) sessionAttributes.get(WebSocketAuthAttributes.ROLE);
			accessor.setUser(new WebSocketUser(userId, role));
			return;
		}

		String token = resolveTokenFromConnectHeaders(accessor);
		if (token == null) {
			return;
		}

		try {
			var claims = jwtUtil.parseToken(token);
			Long userId = Long.parseLong(claims.getSubject());
			String role = claims.get("role", String.class);
			accessor.setUser(new WebSocketUser(userId, role));
			if (sessionAttributes != null) {
				sessionAttributes.put(WebSocketAuthAttributes.USER_ID, userId);
				sessionAttributes.put(WebSocketAuthAttributes.ROLE, role);
			}
		} catch (Exception ex) {
			log.warn("STOMP CONNECT token invalid: {}", ex.getMessage());
		}
	}

	private void validateRoomAccess(StompHeaderAccessor accessor, java.util.Optional<Long> roomIdOptional) {
		if (accessor.getUser() == null) {
			log.warn("STOMP {} rejected: unauthenticated", accessor.getCommand());
			throw new ForbiddenException("Authentication required");
		}

		Long roomId = roomIdOptional.orElseThrow(() ->
				new ForbiddenException("Invalid destination: " + accessor.getDestination()));

		Long userId = Long.parseLong(accessor.getUser().getName());
		chatRoomService.validateActiveParticipant(userId, roomId);
	}

	@Nullable
	private String resolveTokenFromConnectHeaders(StompHeaderAccessor accessor) {
		List<String> authorization = accessor.getNativeHeader("Authorization");
		if (authorization != null && !authorization.isEmpty()) {
			String header = authorization.get(0);
			if (header.startsWith("Bearer ")) {
				return header.substring(7);
			}
		}

		List<String> tokenHeader = accessor.getNativeHeader("token");
		if (tokenHeader != null && !tokenHeader.isEmpty()) {
			return tokenHeader.get(0);
		}
		return null;
	}

}

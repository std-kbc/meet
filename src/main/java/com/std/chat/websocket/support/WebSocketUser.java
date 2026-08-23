package com.std.chat.websocket.support;

import java.security.Principal;

public record WebSocketUser(Long userId, String role) implements Principal {

	@Override
	public String getName() {
		return String.valueOf(userId);
	}

}

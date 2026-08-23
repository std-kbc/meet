package com.std.chat.websocket.interceptor;

import com.std.chat.auth.security.JwtUtil;
import com.std.chat.websocket.support.WebSocketAuthAttributes;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

	private final JwtUtil jwtUtil;

	@Override
	public boolean beforeHandshake(ServerHttpRequest request,
								   ServerHttpResponse response,
								   WebSocketHandler wsHandler,
								   Map<String, Object> attributes) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			return false;
		}

		HttpServletRequest httpRequest = servletRequest.getServletRequest();
		String token = resolveToken(httpRequest);
		if (token == null) {
			return false;
		}

		try {
			Claims claims = jwtUtil.parseToken(token);
			attributes.put(WebSocketAuthAttributes.USER_ID, Long.parseLong(claims.getSubject()));
			attributes.put(WebSocketAuthAttributes.ROLE, claims.get("role", String.class));
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request,
							   ServerHttpResponse response,
							   WebSocketHandler wsHandler,
							   Exception exception) {
		// no-op
	}

	private String resolveToken(HttpServletRequest request) {
		String token = request.getParameter("token");
		if (token != null && !token.isBlank()) {
			return token;
		}

		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			return header.substring(7);
		}
		return null;
	}

}

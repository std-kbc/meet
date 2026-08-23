package com.std.chat.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityRequestUtils {

	public boolean isBrowserRequest(HttpServletRequest request) {
		String accept = request.getHeader("Accept");
		if (accept != null && accept.contains("text/html")) {
			return true;
		}
		return !request.getRequestURI().startsWith("/api/");
	}

}

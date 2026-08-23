package com.std.chat.auth.security;

import com.std.chat.auth.exception.UnauthenticatedException;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@UtilityClass
public class SecurityUtil {

	public Long getCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication.getPrincipal() == null
				|| "anonymousUser".equals(authentication.getPrincipal())) {
			throw new UnauthenticatedException("Authentication required");
		}
		return Long.parseLong(authentication.getName());
	}

}

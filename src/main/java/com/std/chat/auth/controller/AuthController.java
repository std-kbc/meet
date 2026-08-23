package com.std.chat.auth.controller;

import com.std.chat.auth.dto.LoginRequest;
import com.std.chat.auth.dto.LoginResponse;
import com.std.chat.auth.dto.LogoutResponse;
import com.std.chat.auth.dto.RefreshRequest;
import com.std.chat.auth.dto.TokenPair;
import com.std.chat.auth.security.SecurityUtil;
import com.std.chat.auth.service.AuthService;
import com.std.chat.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final TokenService tokenService;

	@Value("${auth.use-header-token:true}")
	private boolean useHeaderToken;

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request.getUsername(), request.getPassword());

		if (useHeaderToken) {
			return ResponseEntity.ok(response);
		}

		return ResponseEntity.ok()
				.headers(buildCookieHeaders(tokenService.createTokenCookies(
						response.getAccessToken(), response.getRefreshToken())))
				.body(LoginResponse.builder()
						.userId(response.getUserId())
						.role(response.getRole())
						.build());
	}

	@PostMapping("/logout")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<LogoutResponse> logout() {
		Long userId = SecurityUtil.getCurrentUserId();
		authService.logout(userId);

		return ResponseEntity.ok()
				.headers(buildCookieHeaders(tokenService.clearTokenCookies()))
				.body(LogoutResponse.builder().userId(userId).build());
	}

	@PostMapping("/refresh")
	public ResponseEntity<Void> refresh(@RequestBody(required = false) RefreshRequest request,
									  HttpServletRequest httpRequest) {
		String refreshToken = resolveRefreshToken(request, httpRequest);
		TokenPair tokens = authService.refresh(refreshToken);

		return ResponseEntity.noContent()
				.headers(buildCookieHeaders(tokenService.createTokenCookies(
						tokens.getAccessToken(), tokens.getRefreshToken())))
				.build();
	}

	private String resolveRefreshToken(RefreshRequest request, HttpServletRequest httpRequest) {
		if (useHeaderToken && request != null && request.getRefreshToken() != null) {
			return request.getRefreshToken();
		}
		String cookieToken = tokenService.extractRefreshTokenFromCookie(httpRequest);
		if (cookieToken != null) {
			return cookieToken;
		}
		if (request != null && request.getRefreshToken() != null) {
			return request.getRefreshToken();
		}
		throw new com.std.chat.auth.exception.UnauthenticatedException("Refresh token is required");
	}

	private HttpHeaders buildCookieHeaders(java.util.List<org.springframework.http.ResponseCookie> cookies) {
		HttpHeaders headers = new HttpHeaders();
		cookies.forEach(cookie -> headers.add(HttpHeaders.SET_COOKIE, cookie.toString()));
		return headers;
	}

}

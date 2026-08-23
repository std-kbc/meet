package com.std.chat.auth.service;

import com.std.chat.auth.domain.User;
import com.std.chat.auth.dto.TokenPair;
import com.std.chat.auth.exception.UnauthenticatedException;
import com.std.chat.auth.mapper.RefreshTokenMapper;
import com.std.chat.auth.security.JwtAuthenticationFilter;
import com.std.chat.auth.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

	private final JwtUtil jwtUtil;
	private final RefreshTokenMapper refreshTokenMapper;

	@Value("${jwt.expiration}")
	private long accessExpiration;

	@Value("${jwt.refresh-expiration}")
	private long refreshExpiration;

	@Value("${cookie.domain}")
	private String cookieDomain;

	public TokenPair generateTokens(User user) {
		String accessToken = jwtUtil.generateToken(user.getId(), user.getRole());
		String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getRole());

		LocalDateTime expiresAt = LocalDateTime.now()
				.plusSeconds(refreshExpiration / 1000);
		refreshTokenMapper.upsert(user.getId(), refreshToken, expiresAt);

		return TokenPair.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.build();
	}

	public TokenPair refreshTokens(String refreshToken, User user) {
		String storedToken = refreshTokenMapper.findByUserId(user.getId())
				.orElseThrow(() -> new UnauthenticatedException("Refresh token not found"));

		if (!storedToken.equals(refreshToken)) {
			throw new UnauthenticatedException("Refresh token mismatch");
		}

		return generateTokens(user);
	}

	public void revokeTokens(Long userId) {
		refreshTokenMapper.deleteByUserId(userId);
	}

	public List<ResponseCookie> createTokenCookies(String accessToken, String refreshToken) {
		List<ResponseCookie> cookies = new ArrayList<>();
		cookies.add(buildCookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, accessToken, accessExpiration / 1000));
		cookies.add(buildCookie(JwtAuthenticationFilter.REFRESH_TOKEN_COOKIE, refreshToken, refreshExpiration / 1000));
		return cookies;
	}

	public List<ResponseCookie> clearTokenCookies() {
		List<ResponseCookie> cookies = new ArrayList<>();
		cookies.add(buildCookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, "", 0));
		cookies.add(buildCookie(JwtAuthenticationFilter.REFRESH_TOKEN_COOKIE, "", 0));
		return cookies;
	}

	public String extractRefreshTokenFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (JwtAuthenticationFilter.REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("None")
				.domain(cookieDomain)
				.path("/")
				.maxAge(maxAgeSeconds)
				.build();
	}

}

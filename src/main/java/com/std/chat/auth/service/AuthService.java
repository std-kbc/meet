package com.std.chat.auth.service;

import com.std.chat.auth.domain.User;
import com.std.chat.auth.dto.LoginResponse;
import com.std.chat.auth.dto.TokenPair;
import com.std.chat.auth.exception.UnauthenticatedException;
import com.std.chat.auth.mapper.UserMapper;
import com.std.chat.auth.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final JwtUtil jwtUtil;

	@Transactional
	public LoginResponse login(String username, String password) {
		User user = userMapper.findByUsername(username)
				.orElseThrow(() -> new UnauthenticatedException("Invalid username or password"));

		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new UnauthenticatedException("Invalid username or password");
		}

		userMapper.updateLastLoginAt(user.getId());
		TokenPair tokens = tokenService.generateTokens(user);

		return LoginResponse.builder()
				.userId(user.getId())
				.nickname(user.getNickname())
				.role(user.getRole())
				.accessToken(tokens.getAccessToken())
				.refreshToken(tokens.getRefreshToken())
				.build();
	}

	@Transactional
	public TokenPair refresh(String refreshToken) {
		Claims claims = jwtUtil.parseToken(refreshToken);
		Long userId = Long.parseLong(claims.getSubject());

		User user = userMapper.findById(userId)
				.orElseThrow(() -> new UnauthenticatedException("User not found"));

		return tokenService.refreshTokens(refreshToken, user);
	}

	@Transactional
	public void logout(Long userId) {
		tokenService.revokeTokens(userId);
	}

}

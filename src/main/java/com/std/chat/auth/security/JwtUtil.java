package com.std.chat.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration}")
	private long expiration;

	@Value("${jwt.refresh-expiration}")
	private long refreshExpiration;

	private SecretKey signingKey;

	@PostConstruct
	public void init() {
		byte[] keyBytes = Decoders.BASE64.decode(secret);
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
	}

	public String generateToken(Long userId, String role) {
		return buildToken(userId, role, expiration);
	}

	public String generateRefreshToken(Long userId, String role) {
		return buildToken(userId, role, refreshExpiration);
	}

	public Claims parseToken(String token) {
		try {
			return Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid JWT token", ex);
		}
	}

	private String buildToken(Long userId, String role, long ttl) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + ttl);

		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("role", role)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(signingKey)
				.compact();
	}

}

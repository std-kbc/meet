package com.std.chat.auth.security;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * application.properties의 jwt.secret 값을 생성할 때 사용하는 유틸리티.
 * <pre>
 * SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
 * String base64Secret = Encoders.BASE64.encode(key.getEncoded());
 * </pre>
 */
public final class JwtSecretGenerator {

	private JwtSecretGenerator() {
	}

	public static void main(String[] args) {
		SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
		String base64Secret = Encoders.BASE64.encode(key.getEncoded());
		System.out.println(base64Secret);
	}

}

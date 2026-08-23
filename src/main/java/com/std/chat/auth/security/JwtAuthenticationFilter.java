package com.std.chat.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.std.chat.common.dto.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String ACCESS_TOKEN_COOKIE = "accessToken";
	public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

	private final JwtUtil jwtUtil;
	private final ObjectMapper objectMapper;

	@Value("${auth.use-header-token:true}")
	private boolean useHeaderToken;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getServletPath();
		return path.equals("/")
				|| path.equals("/index.html")
				|| path.equals("/room.html")
				|| path.equals("/login.html")
				|| path.startsWith("/css/")
				|| path.startsWith("/js/")
				|| path.startsWith("/ws")
				|| path.startsWith("/swagger-ui")
				|| path.startsWith("/v3/api-docs")
				|| path.startsWith("/webjars");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
									HttpServletResponse response,
									FilterChain filterChain) throws ServletException, IOException {
		String token = resolveToken(request);
		if (token == null) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			Claims claims = jwtUtil.parseToken(token);
			String userId = claims.getSubject();
			String role = claims.get("role", String.class);

			UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
					userId,
					null,
					List.of(new SimpleGrantedAuthority("ROLE_" + role))
			);
			auth.setDetails(claims);
			SecurityContextHolder.getContext().setAuthentication(auth);
			filterChain.doFilter(request, response);
		} catch (ExpiredJwtException ex) {
			writeUnauthorized(response, "Token has expired");
		} catch (IllegalArgumentException ex) {
			writeUnauthorized(response, "Invalid token");
		}
	}

	private String resolveToken(HttpServletRequest request) {
		if (useHeaderToken) {
			String header = request.getHeader("Authorization");
			if (header != null && header.startsWith("Bearer ")) {
				return header.substring(7);
			}
			return null;
		}
		return extractCookie(request, ACCESS_TOKEN_COOKIE);
	}

	private String extractCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(),
				ErrorResponse.builder()
						.code("UNAUTHORIZED")
						.message(message)
						.build());
	}

}

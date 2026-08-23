package com.std.chat.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.std.chat.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ObjectMapper objectMapper;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/", "/index.html", "/room.html", "/login.html", "/error").permitAll()
						.requestMatchers("/css/**", "/js/**").permitAll()
						.requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
						.requestMatchers("/ws", "/ws/**").permitAll()
						.requestMatchers(
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/v3/api-docs",
								"/v3/api-docs/**"
						).permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(this::handleUnauthorized)
						.accessDeniedHandler((request, response, accessDeniedException) ->
								handleForbidden(request, response))
				)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private void handleUnauthorized(HttpServletRequest request, HttpServletResponse response,
									org.springframework.security.core.AuthenticationException authException)
			throws java.io.IOException {
		if (SecurityRequestUtils.isBrowserRequest(request)) {
			response.sendRedirect("/login.html");
			return;
		}
		writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
	}

	private void handleForbidden(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
		if (SecurityRequestUtils.isBrowserRequest(request)) {
			response.sendRedirect("/login.html");
			return;
		}
		writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied");
	}

	private void writeError(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(),
				ErrorResponse.builder().code(code).message(message).build());
	}

}

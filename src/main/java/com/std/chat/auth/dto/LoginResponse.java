package com.std.chat.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

	private Long userId;
	private String nickname;
	private String role;
	private String accessToken;
	private String refreshToken;

}

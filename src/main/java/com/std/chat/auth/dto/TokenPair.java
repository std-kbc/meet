package com.std.chat.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenPair {

	private String accessToken;
	private String refreshToken;

}

package com.std.chat.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LogoutResponse {

	private Long userId;

}

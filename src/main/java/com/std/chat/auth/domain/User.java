package com.std.chat.auth.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class User {

	private Long id;
	private String username;
	private String passwordHash;
	private String nickname;
	private String role;
	private LocalDateTime createdAt;
	private LocalDateTime lastLoginAt;

}

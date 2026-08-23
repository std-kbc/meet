package com.std.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "메시지 전송 요청")
public class SendMessageRequest {

	@NotBlank
	@Size(max = 5000)
	@Schema(description = "메시지 내용", example = "안녕하세요")
	private String message;

}

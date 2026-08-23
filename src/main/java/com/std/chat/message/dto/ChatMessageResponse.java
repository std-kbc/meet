package com.std.chat.message.dto;

import com.std.chat.message.domain.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "메시지 응답")
public class ChatMessageResponse {

	@Schema(description = "메시지 ID")
	private Long id;

	@Schema(description = "채팅방 ID")
	private Long chatRoomId;

	@Schema(description = "발신자 ID")
	private Long senderId;

	@Schema(description = "발신자 닉네임")
	private String senderNickname;

	@Schema(description = "메시지 유형")
	private MessageType messageType;

	@Schema(description = "메시지 내용")
	private String message;

	@Schema(description = "발송 일시")
	private LocalDateTime sentAt;

}

package com.std.chat.message.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatMessage {

	private Long id;
	private Long chatRoomId;
	private Long senderId;
	private String senderNickname;
	private MessageType messageType;
	private String message;
	private Long parentMessageId;
	private Long fileGroupId;
	private MessageStatus status;
	private LocalDateTime editedAt;
	private LocalDateTime sentAt;
	private LocalDateTime deletedAt;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

}

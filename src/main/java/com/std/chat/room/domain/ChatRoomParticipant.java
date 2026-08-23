package com.std.chat.room.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatRoomParticipant {

	private Long chatRoomId;
	private Long userId;
	private String nickname;
	private ParticipantRole participantRole;
	private ParticipantStatus participantStatus;
	private Long lastReadMessageId;
	private String mutedYn;
	private LocalDateTime joinedAt;
	private LocalDateTime leftAt;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

}

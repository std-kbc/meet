package com.std.chat.room.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatRoom {

	private Long id;
	private RoomType roomType;
	private String referenceType;
	private Long referenceId;
	private String name;
	private RoomStatus status;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private LocalDateTime deletedAt;
	private ParticipantRole myParticipantRole;

}

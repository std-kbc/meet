package com.std.chat.room.dto;

import com.std.chat.room.domain.ParticipantRole;
import com.std.chat.room.domain.RoomStatus;
import com.std.chat.room.domain.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "채팅방 응답")
public class ChatRoomResponse {

	@Schema(description = "채팅방 ID")
	private Long id;

	@Schema(description = "채팅방 유형")
	private RoomType roomType;

	@Schema(description = "외부 컨텍스트 유형")
	private String referenceType;

	@Schema(description = "외부 컨텍스트 ID")
	private Long referenceId;

	@Schema(description = "채팅방 이름")
	private String name;

	@Schema(description = "채팅방 상태")
	private RoomStatus status;

	@Schema(description = "생성일시")
	private LocalDateTime createdAt;

	@Schema(description = "활성 참여자 수")
	private Integer participantCount;

	@Schema(description = "내 참여자 권한")
	private ParticipantRole myParticipantRole;

	@Schema(description = "마지막 메시지")
	private String lastMessage;

}

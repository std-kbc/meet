package com.std.chat.room.dto;

import com.std.chat.room.domain.ParticipantRole;
import com.std.chat.room.domain.ParticipantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "채팅방 참여자 응답")
public class ChatRoomParticipantResponse {

	@Schema(description = "사용자 ID")
	private Long userId;

	@Schema(description = "닉네임")
	private String nickname;

	@Schema(description = "참여자 권한")
	private ParticipantRole participantRole;

	@Schema(description = "참여 상태")
	private ParticipantStatus participantStatus;

	@Schema(description = "참여 일시")
	private LocalDateTime joinedAt;

}

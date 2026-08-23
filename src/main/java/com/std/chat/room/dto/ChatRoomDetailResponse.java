package com.std.chat.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "채팅방 상세 응답")
public class ChatRoomDetailResponse {

	private ChatRoomResponse room;
	private List<ChatRoomParticipantResponse> participants;

}

package com.std.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "메시지 목록 응답")
public class ChatMessageListResponse {

	@Schema(description = "메시지 목록 (오래된 순)")
	private List<ChatMessageResponse> messages;

	@Schema(description = "다음 페이지 cursor (더 오래된 메시지 조회용)")
	private Long nextCursor;

	@Schema(description = "더 조회할 메시지 존재 여부")
	private boolean hasMore;

}

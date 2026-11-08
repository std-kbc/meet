package com.std.chat.message.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ChatMessageController의 Swagger 문서 어노테이션 모음.
 */
public final class ChatMessageApiDocs {

	private static final String TAG = "Chat Message";

	private ChatMessageApiDocs() {
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Tag(name = TAG)
	@Operation(summary = "메시지 목록 조회", description = "채팅방 메시지를 최신순으로 커서 기반 페이징 조회한다.",
			parameters = {
					@Parameter(name = "roomId", description = "채팅방 ID"),
					@Parameter(name = "cursor", description = "이 메시지 ID보다 이전 메시지부터 조회 (생략 시 최신부터)"),
					@Parameter(name = "size", description = "조회 개수")
			})
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "성공"),
			@ApiResponse(responseCode = "401", description = "인증 필요"),
			@ApiResponse(responseCode = "403", description = "참여자가 아님"),
			@ApiResponse(responseCode = "404", description = "채팅방 없음")
	})
	public @interface ListMessagesApi {
	}

}

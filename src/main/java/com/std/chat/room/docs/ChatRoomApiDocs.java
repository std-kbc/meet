package com.std.chat.room.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ChatRoomController의 Swagger 문서 어노테이션 모음.
 */
public final class ChatRoomApiDocs {

	private static final String TAG = "Chat Room";

	private ChatRoomApiDocs() {
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Tag(name = TAG)
	@Operation(summary = "채팅방 생성", description = "새 채팅방을 만들고 요청자를 OWNER로 참여시킨다. DIRECT 방은 targetUserId가 필요하다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "생성됨"),
			@ApiResponse(responseCode = "400", description = "잘못된 요청 (DIRECT 방 대상 누락, 자기 자신과의 DIRECT 방 등)"),
			@ApiResponse(responseCode = "401", description = "인증 필요"),
			@ApiResponse(responseCode = "409", description = "이미 존재하는 DIRECT 방")
	})
	public @interface CreateChatRoomApi {
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Tag(name = TAG)
	@Operation(summary = "내 채팅방 목록", description = "현재 사용자가 참여 중인 채팅방 목록을 조회한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "성공"),
			@ApiResponse(responseCode = "401", description = "인증 필요")
	})
	public @interface ListMyChatRoomsApi {
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Tag(name = TAG)
	@Operation(summary = "참여 가능한 채팅방 목록", description = "아직 참여하지 않은 GROUP/CHANNEL 채팅방 목록을 조회한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "성공"),
			@ApiResponse(responseCode = "401", description = "인증 필요")
	})
	public @interface BrowseChatRoomsApi {
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Tag(name = TAG)
	@Operation(summary = "채팅방 상세 조회", description = "채팅방 정보와 참여자 목록을 조회한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "성공"),
			@ApiResponse(responseCode = "401", description = "인증 필요"),
			@ApiResponse(responseCode = "403", description = "참여자가 아님"),
			@ApiResponse(responseCode = "404", description = "채팅방 없음")
	})
	public @interface GetChatRoomApi {
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Tag(name = TAG)
	@Operation(summary = "채팅방 참여", description = "현재 사용자를 채팅방에 참여시킨다. 이전에 나간 방이면 다시 참여한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "참여 완료"),
			@ApiResponse(responseCode = "400", description = "종료된 방 또는 인원이 찬 DIRECT 방"),
			@ApiResponse(responseCode = "401", description = "인증 필요"),
			@ApiResponse(responseCode = "403", description = "차단된 사용자"),
			@ApiResponse(responseCode = "404", description = "채팅방 없음"),
			@ApiResponse(responseCode = "409", description = "이미 참여 중")
	})
	public @interface JoinChatRoomApi {
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@Tag(name = TAG)
	@Operation(summary = "채팅방 나가기", description = "현재 사용자를 채팅방에서 나가게 한다.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "나가기 완료"),
			@ApiResponse(responseCode = "400", description = "참여 중인 사용자가 아님"),
			@ApiResponse(responseCode = "401", description = "인증 필요"),
			@ApiResponse(responseCode = "404", description = "채팅방 없음")
	})
	public @interface LeaveChatRoomApi {
	}

}

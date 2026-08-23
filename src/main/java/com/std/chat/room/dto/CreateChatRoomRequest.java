package com.std.chat.room.dto;

import com.std.chat.room.domain.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "채팅방 생성 요청")
public class CreateChatRoomRequest {

	@NotNull
	@Schema(description = "채팅방 유형", example = "GROUP")
	private RoomType roomType;

	@NotBlank
	@Size(max = 100)
	@Schema(description = "채팅방 이름", example = "프로젝트 A 팀")
	private String name;

	@Schema(description = "외부 컨텍스트 유형", example = "PROJECT")
	private String referenceType;

	@Schema(description = "외부 컨텍스트 ID", example = "1001")
	private Long referenceId;

	@Schema(description = "1:1 채팅 대상 사용자 ID (DIRECT 방 필수)", example = "2")
	private Long targetUserId;

}

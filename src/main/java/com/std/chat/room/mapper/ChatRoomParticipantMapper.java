package com.std.chat.room.mapper;

import com.std.chat.room.domain.ChatRoomParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ChatRoomParticipantMapper {

	void insert(ChatRoomParticipant participant);

	Optional<ChatRoomParticipant> findByRoomAndUser(@Param("chatRoomId") Long chatRoomId,
													@Param("userId") Long userId);

	List<ChatRoomParticipant> findActiveByRoomId(@Param("chatRoomId") Long chatRoomId);

	void updateRejoin(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

	void updateLeave(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

	int countActiveByRoomId(@Param("chatRoomId") Long chatRoomId);

}

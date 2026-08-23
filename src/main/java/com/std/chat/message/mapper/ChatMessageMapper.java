package com.std.chat.message.mapper;

import com.std.chat.message.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ChatMessageMapper {

	void insert(ChatMessage chatMessage);

	Optional<ChatMessage> findById(@Param("id") Long id);

	List<ChatMessage> findByRoomId(@Param("chatRoomId") Long chatRoomId,
								   @Param("cursor") Long cursor,
								   @Param("size") int size);

	List<ChatMessage> findLastMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);

}

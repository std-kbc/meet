package com.std.chat.room.mapper;

import com.std.chat.room.domain.ChatRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ChatRoomMapper {

	void insert(ChatRoom chatRoom);

	Optional<ChatRoom> findById(@Param("id") Long id);

	List<ChatRoom> findByUserId(@Param("userId") Long userId);

	List<ChatRoom> findJoinableRooms(@Param("userId") Long userId);

	Optional<Long> findDirectRoomIdBetweenUsers(@Param("userId1") Long userId1,
												@Param("userId2") Long userId2);

}

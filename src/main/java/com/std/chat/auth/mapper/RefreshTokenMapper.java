package com.std.chat.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface RefreshTokenMapper {

	void upsert(@Param("userId") Long userId,
				@Param("token") String token,
				@Param("expiresAt") LocalDateTime expiresAt);

	Optional<String> findByUserId(@Param("userId") Long userId);

	void deleteByUserId(@Param("userId") Long userId);

}

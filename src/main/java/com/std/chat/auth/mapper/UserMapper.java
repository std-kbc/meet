package com.std.chat.auth.mapper;

import com.std.chat.auth.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

	Optional<User> findByUsername(@Param("username") String username);

	Optional<User> findById(@Param("id") Long id);

	void updateLastLoginAt(@Param("id") Long id);

}

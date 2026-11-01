package com.std.chat.rtc.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.std.chat.rtc.domain.CallHistory;

@Mapper
public interface CallHistoryMapper {

    void insert(CallHistory callHistory);

}

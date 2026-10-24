package com.std.chat.rtc.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.std.chat.rtc.dto.SignalingMessageResponse;
import com.std.chat.rtc.dto.SignalingMessageRequest;
import com.std.chat.room.service.ChatRoomService;
import com.std.chat.common.exception.*;


@Service
public class RtcSignalingService {

    private final SimpMessagingTemplate messagingTemplate;
	private final ChatRoomService chatRoomService;

    public RtcSignalingService(SimpMessagingTemplate messagingTemplate, ChatRoomService chatRoomService) {
        this.messagingTemplate = messagingTemplate;
        this.chatRoomService = chatRoomService;
    }
    
	public SignalingMessageResponse sendSignal(Long userId, Long roomId, SignalingMessageRequest request) {
		
		// validation
		chatRoomService.validateActiveParticipant(userId, roomId);
		chatRoomService.validateActiveParticipant(request.getTargetUserId(), roomId);
		if(userId.equals(request.getTargetUserId())) {
			throw new BadRequestException("cannot send signal to yourself.");
		}

		// send signal to the target user
        SignalingMessageResponse response = toResponse(userId, request);
        messagingTemplate.convertAndSendToUser(request.getTargetUserId().toString(), "/queue/rtc", response);

        return response;
	}

    private SignalingMessageResponse toResponse(Long userId, SignalingMessageRequest request) {
		SignalingMessageResponse response = new SignalingMessageResponse();
		response.setFromUserId(userId);
		response.setType(request.getType());
		response.setIce(request.getIce());
		response.setSdp(request.getSdp());
		return response;
	}
}

package com.std.chat.rtc.service;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.stereotype.Service;
import com.std.chat.rtc.dto.SignalingMessageResponse;
import com.std.chat.rtc.exception.CallAlreadyAnsweredException;
import com.std.chat.rtc.exception.CalleeBusyException;
import com.std.chat.rtc.domain.CallSession;
import com.std.chat.rtc.domain.CallStatus;
import com.std.chat.rtc.domain.SignalingMessageType;
import com.std.chat.rtc.dto.SignalingMessageRequest;
import com.std.chat.room.service.ChatRoomService;
import com.std.chat.common.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.user.SimpUserRegistry;

@Slf4j
@Service
public class RtcSignalingService {

	private final CallSessionRegistry callSessionRegistry;
	private final SimpMessagingTemplate messagingTemplate;
	private final ChatRoomService chatRoomService;
	private final SimpUserRegistry simpUserRegistry;

	public RtcSignalingService(SimpMessagingTemplate messagingTemplate, ChatRoomService chatRoomService,
			CallSessionRegistry callSessionRegistry, SimpUserRegistry simpUserRegistry) {
		this.messagingTemplate = messagingTemplate;
		this.chatRoomService = chatRoomService;
		this.callSessionRegistry = callSessionRegistry;
		this.simpUserRegistry = simpUserRegistry;
	}

	public void sendSignal(Long fromUserId, Long roomId, String sessionId, SignalingMessageRequest request) {

		// validation
		chatRoomService.validateActiveParticipant(fromUserId, roomId);
		chatRoomService.validateActiveParticipant(request.getTargetUserId(), roomId);
		if (fromUserId.equals(request.getTargetUserId())) {
			throw new BadRequestException("cannot send signal to yourself.");
		}

		Long toUserId = request.getTargetUserId();

		switch (request.getType()) {
			case CALL_REQUEST -> handleCallRequest(fromUserId, toUserId, roomId, sessionId, request);
			case CALL_ACCEPTED -> handleCallAccepted(toUserId, fromUserId, sessionId, request);
			case VIDEO_OFFER, VIDEO_ANSWER, NEW_ICE_CANDIDATE -> relay(fromUserId, sessionId, request);
			case CALL_REJECTED, CALL_CANCEL, HANG_UP -> handleEndCall(fromUserId, request.getType());
			default -> throw new BadRequestException("unsupported type");
		}
	}

	private SignalingMessageResponse toResponse(Long fromUserId, SignalingMessageRequest request) {
		SignalingMessageResponse response = new SignalingMessageResponse();
		response.setFromUserId(fromUserId);
		response.setType(request.getType());
		response.setIce(request.getIce());
		response.setSdp(request.getSdp());
		return response;
	}

	private SignalingMessageResponse toResponse(Long fromUserId, SignalingMessageType type) {
		SignalingMessageResponse response = new SignalingMessageResponse();
		response.setFromUserId(fromUserId);
		response.setType(type);
		return response;
	}

	private void sendToSession(Long toUserId, String sessionId, SignalingMessageResponse payload) {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headers.setSessionId(sessionId);
		headers.setLeaveMutable(true);
		messagingTemplate.convertAndSendToUser(
				String.valueOf(toUserId), "/queue/rtc", payload, headers.getMessageHeaders());
	}

	// CALL_REQUESTED HANDLER
	private void handleCallRequest(Long callerId, Long calleeId, Long roomId, String sessionId,
			SignalingMessageRequest request) {

		log.info("inbound Signaling Message Type: CALL_REQUEST");

		try {
			callSessionRegistry.createCall(callerId, calleeId, roomId, sessionId);
		} catch (CalleeBusyException e) {
			log.info(e.getMessage());
			sendToSession(callerId, sessionId, toResponse(calleeId, SignalingMessageType.CALL_BUSY));
			return;
		}

		// send signal to the target user
		SignalingMessageResponse response = toResponse(callerId, request);
		messagingTemplate.convertAndSendToUser(String.valueOf(calleeId), "/queue/rtc", response);
	}

	// CALL_ACCEPTED HANDLER
	private void handleCallAccepted(Long callerId, Long calleeId, String calleeSessionId,
			SignalingMessageRequest request) {

		log.info("inbound Signaling Message Type: CALL_ACCEPTED");

		CallSession callSession;
		try {
			callSession = callSessionRegistry.acknowledgeCall(calleeId, calleeSessionId);
		} catch (CallAlreadyAnsweredException e) {
			// (A) 다른 탭이 먼저 받음 → 늦게 누른 "이 탭"의 벨만 끈다
			log.info(e.getMessage());
			sendToSession(calleeId, calleeSessionId,
					toResponse(callerId, SignalingMessageType.CALL_ANSWERED_ELSEWHERE));
			return;
		}

		// (B) 건 사람에게: "받았다" → 전화를 건 탭 하나에만
		sendToSession(callSession.getCallerId(), callSession.getCallerSessionId(),
				toResponse(calleeId, SignalingMessageType.CALL_ACCEPTED));

		// (C) 받는 사람의 나머지 탭들에게: "다른 탭에서 받았다" → 벨 끄기
		SimpUser callee = simpUserRegistry.getUser(String.valueOf(calleeId));
		if (callee == null) {
			return;
		}
		for (SimpSession session : callee.getSessions()) {
			if (!session.getId().equals(calleeSessionId)) {
				sendToSession(calleeId, session.getId(),
						toResponse(callSession.getCallerId(), SignalingMessageType.CALL_ANSWERED_ELSEWHERE));
			}
		}
	}

	// END CALL
	private void handleEndCall(Long senderId, SignalingMessageType type) {

		log.info("inbound Signaling Message Type: {}", type);

		CallSession callSession = callSessionRegistry.endCall(senderId);
		notifyPartner(callSession, senderId, type);
	}

	// 브라우저 탭이 닫히거나 연결이 끊겼을 때 (RtcDisconnectListener에서 호출)
	public void handleDisconnect(Long userId, String sessionId) {
		CallSession callSession = callSessionRegistry.endCallBySession(userId, sessionId);
		if (callSession == null) {
			return;
		}
		log.info("call ended by disconnect: userId={}", userId);
		notifyPartner(callSession, userId, SignalingMessageType.HANG_UP);
	}

	private void notifyPartner(CallSession callSession, Long senderId, SignalingMessageType type) {
		Long partnerId = callSession.partnerOf(senderId);
		String partnerSessionId = callSession.sessionIdOf(partnerId);

		SignalingMessageResponse response = toResponse(senderId, type);
		if (partnerSessionId != null) {
			sendToSession(partnerId, partnerSessionId, response);
		} else {
			messagingTemplate.convertAndSendToUser(String.valueOf(partnerId), "/queue/rtc", response);
		}
	}

	// RELAY
	private void relay(Long senderId, String senderSessionId, SignalingMessageRequest request) {

		CallSession callSession = callSessionRegistry.getCall(senderId);

		// ① 연결된 통화가 있어야 한다 (벨이 울리는 중에는 영상 정보 교환 불가)
		if (callSession == null || callSession.getStatus() != CallStatus.IN_CALL) {
			throw new BadRequestException("no active call");
		}

		// ② 통화 중인 그 탭에서 보낸 것이어야 한다 (같은 사용자의 다른 탭이 끼어드는 것 방지)
		if (!senderSessionId.equals(callSession.sessionIdOf(senderId))) {
			throw new ForbiddenException("this tab is not in the call");
		}

		// ③ 상대의 통화 중인 탭에만 그대로 전달
		Long partnerId = callSession.partnerOf(senderId);
		sendToSession(partnerId, callSession.sessionIdOf(partnerId), toResponse(senderId, request));
	}

}

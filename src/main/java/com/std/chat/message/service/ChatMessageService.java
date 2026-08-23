package com.std.chat.message.service;

import com.std.chat.common.exception.BadRequestException;
import com.std.chat.message.domain.ChatMessage;
import com.std.chat.message.domain.MessageStatus;
import com.std.chat.message.domain.MessageType;
import com.std.chat.message.dto.ChatMessageListResponse;
import com.std.chat.message.dto.ChatMessageResponse;
import com.std.chat.message.mapper.ChatMessageMapper;
import com.std.chat.room.domain.ChatRoom;
import com.std.chat.room.domain.RoomStatus;
import com.std.chat.room.mapper.ChatRoomMapper;
import com.std.chat.room.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

	private static final int DEFAULT_PAGE_SIZE = 50;
	private static final int MAX_PAGE_SIZE = 100;

	private final ChatMessageMapper chatMessageMapper;
	private final ChatRoomMapper chatRoomMapper;
	private final ChatRoomService chatRoomService;
	private final SimpMessagingTemplate messagingTemplate;

	@Transactional
	public ChatMessageResponse sendMessage(Long userId, Long roomId, String content) {
		validateMessageContent(content);
		chatRoomService.validateActiveParticipant(userId, roomId);
		assertRoomAllowsMessaging(roomId);

		ChatMessage chatMessage = new ChatMessage();
		chatMessage.setChatRoomId(roomId);
		chatMessage.setSenderId(userId);
		chatMessage.setMessageType(MessageType.TEXT);
		chatMessage.setMessage(content.trim());
		chatMessage.setStatus(MessageStatus.NORMAL);
		chatMessage.setSentAt(LocalDateTime.now());
		chatMessageMapper.insert(chatMessage);

		ChatMessage savedMessage = chatMessageMapper.findById(chatMessage.getId())
				.orElseThrow(() -> new BadRequestException("Failed to save message"));

		ChatMessageResponse response = toResponse(savedMessage);
		messagingTemplate.convertAndSend("/sub/rooms/" + roomId, response);
		return response;
	}

	@Transactional(readOnly = true)
	public ChatMessageListResponse getMessages(Long userId, Long roomId, Long cursor, Integer size) {
		chatRoomService.validateActiveParticipant(userId, roomId);

		int pageSize = normalizePageSize(size);
		List<ChatMessage> messages = chatMessageMapper.findByRoomId(roomId, cursor, pageSize + 1);

		boolean hasMore = messages.size() > pageSize;
		if (hasMore) {
			messages = messages.subList(0, pageSize);
		}

		Collections.reverse(messages);

		List<ChatMessageResponse> responses = messages.stream()
				.map(this::toResponse)
				.toList();

		Long nextCursor = hasMore && !messages.isEmpty() ? messages.get(0).getId() : null;

		return ChatMessageListResponse.builder()
				.messages(responses)
				.nextCursor(nextCursor)
				.hasMore(hasMore)
				.build();
	}

	private void validateMessageContent(String content) {
		if (content == null || content.isBlank()) {
			throw new BadRequestException("Message must not be blank");
		}
		if (content.length() > 5000) {
			throw new BadRequestException("Message is too long");
		}
	}

	private void assertRoomAllowsMessaging(Long roomId) {
		ChatRoom room = chatRoomMapper.findById(roomId)
				.orElseThrow(() -> new BadRequestException("Chat room not found"));

		if (room.getStatus() != RoomStatus.ACTIVE) {
			throw new BadRequestException("Messages cannot be sent to this chat room");
		}
	}

	private int normalizePageSize(Integer size) {
		if (size == null || size <= 0) {
			return DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}

	private ChatMessageResponse toResponse(ChatMessage message) {
		return ChatMessageResponse.builder()
				.id(message.getId())
				.chatRoomId(message.getChatRoomId())
				.senderId(message.getSenderId())
				.senderNickname(message.getSenderNickname())
				.messageType(message.getMessageType())
				.message(message.getMessage())
				.sentAt(message.getSentAt())
				.build();
	}

}

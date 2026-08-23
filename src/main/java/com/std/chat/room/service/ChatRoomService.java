package com.std.chat.room.service;

import com.std.chat.auth.mapper.UserMapper;
import com.std.chat.message.domain.ChatMessage;
import com.std.chat.message.mapper.ChatMessageMapper;
import com.std.chat.common.exception.BadRequestException;
import com.std.chat.common.exception.ConflictException;
import com.std.chat.common.exception.ForbiddenException;
import com.std.chat.common.exception.ResourceNotFoundException;
import com.std.chat.room.domain.ChatRoom;
import com.std.chat.room.domain.ChatRoomParticipant;
import com.std.chat.room.domain.ParticipantRole;
import com.std.chat.room.domain.ParticipantStatus;
import com.std.chat.room.domain.RoomStatus;
import com.std.chat.room.domain.RoomType;
import com.std.chat.room.dto.ChatRoomDetailResponse;
import com.std.chat.room.dto.ChatRoomParticipantResponse;
import com.std.chat.room.dto.ChatRoomResponse;
import com.std.chat.room.dto.CreateChatRoomRequest;
import com.std.chat.room.mapper.ChatRoomMapper;
import com.std.chat.room.mapper.ChatRoomParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

	private final ChatRoomMapper chatRoomMapper;
	private final ChatRoomParticipantMapper chatRoomParticipantMapper;
	private final ChatMessageMapper chatMessageMapper;
	private final UserMapper userMapper;

	@Transactional
	public ChatRoomResponse createRoom(Long userId, CreateChatRoomRequest request) {
		validateCreateRequest(userId, request);

		ChatRoom chatRoom = new ChatRoom();
		chatRoom.setRoomType(request.getRoomType());
		chatRoom.setReferenceType(request.getReferenceType());
		chatRoom.setReferenceId(request.getReferenceId());
		chatRoom.setName(request.getName());
		chatRoom.setStatus(RoomStatus.ACTIVE);
		chatRoomMapper.insert(chatRoom);

		addParticipant(chatRoom.getId(), userId, ParticipantRole.OWNER);

		if (request.getRoomType() == RoomType.DIRECT) {
			addParticipant(chatRoom.getId(), request.getTargetUserId(), ParticipantRole.MEMBER);
		}

		ChatRoom savedRoom = findActiveRoom(chatRoom.getId());
		return toResponse(savedRoom, chatRoomParticipantMapper.countActiveByRoomId(savedRoom.getId()),
				ParticipantRole.OWNER, null);
	}

	@Transactional(readOnly = true)
	public List<ChatRoomResponse> getMyRooms(Long userId) {
		List<ChatRoom> rooms = chatRoomMapper.findByUserId(userId);
		Map<Long, String> lastMessageByRoomId = findLastMessagesByRoomIds(
				rooms.stream().map(ChatRoom::getId).toList());

		return rooms.stream()
				.map(room -> toResponse(
						room,
						chatRoomParticipantMapper.countActiveByRoomId(room.getId()),
						room.getMyParticipantRole(),
						lastMessageByRoomId.get(room.getId())))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ChatRoomResponse> browseRooms(Long userId) {
		return chatRoomMapper.findJoinableRooms(userId).stream()
				.map(room -> toResponse(room, chatRoomParticipantMapper.countActiveByRoomId(room.getId()), null, null))
				.toList();
	}

	@Transactional(readOnly = true)
	public ChatRoomDetailResponse getRoom(Long userId, Long roomId) {
		ChatRoom room = findActiveRoom(roomId);
		assertActiveParticipant(roomId, userId);

		List<ChatRoomParticipantResponse> participants = chatRoomParticipantMapper.findActiveByRoomId(roomId).stream()
				.map(this::toParticipantResponse)
				.toList();

		return ChatRoomDetailResponse.builder()
				.room(toResponse(room, participants.size(), findMyParticipantRole(roomId, userId), null))
				.participants(participants)
				.build();
	}

	@Transactional
	public void joinRoom(Long userId, Long roomId) {
		ChatRoom room = findActiveRoom(roomId);
		if (room.getStatus() == RoomStatus.CLOSED) {
			throw new BadRequestException("Closed chat room cannot be joined");
		}

		ChatRoomParticipant existing = chatRoomParticipantMapper.findByRoomAndUser(roomId, userId).orElse(null);
		if (existing != null && existing.getParticipantStatus() == ParticipantStatus.ACTIVE) {
			throw new ConflictException("Already joined this chat room");
		}
		if (existing != null && existing.getParticipantStatus() == ParticipantStatus.BANNED) {
			throw new ForbiddenException("Banned from this chat room");
		}

		if (room.getRoomType() == RoomType.DIRECT) {
			int activeCount = chatRoomParticipantMapper.countActiveByRoomId(roomId);
			if (activeCount >= 2) {
				throw new BadRequestException("Direct chat room is full");
			}
		}

		if (existing != null && existing.getParticipantStatus() == ParticipantStatus.LEFT) {
			chatRoomParticipantMapper.updateRejoin(roomId, userId);
			return;
		}

		addParticipant(roomId, userId, ParticipantRole.MEMBER);
	}

	@Transactional
	public void leaveRoom(Long userId, Long roomId) {
		findActiveRoom(roomId);
		ChatRoomParticipant participant = chatRoomParticipantMapper.findByRoomAndUser(roomId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Participant not found"));

		if (participant.getParticipantStatus() != ParticipantStatus.ACTIVE) {
			throw new BadRequestException("Not an active participant");
		}

		chatRoomParticipantMapper.updateLeave(roomId, userId);
	}

	@Transactional(readOnly = true)
	public void validateActiveParticipant(Long userId, Long roomId) {
		findActiveRoom(roomId);
		assertActiveParticipant(roomId, userId);
	}

	private void validateCreateRequest(Long userId, CreateChatRoomRequest request) {
		if (request.getRoomType() == RoomType.DIRECT) {
			if (request.getTargetUserId() == null) {
				throw new BadRequestException("targetUserId is required for DIRECT room");
			}
			if (userId.equals(request.getTargetUserId())) {
				throw new BadRequestException("Cannot create direct room with yourself");
			}
			userMapper.findById(request.getTargetUserId())
					.orElseThrow(() -> new ResourceNotFoundException("Target user not found"));
			chatRoomMapper.findDirectRoomIdBetweenUsers(userId, request.getTargetUserId())
					.ifPresent(roomId -> {
						throw new ConflictException("Direct chat room already exists");
					});
		}
	}

	private ChatRoom findActiveRoom(Long roomId) {
		return chatRoomMapper.findById(roomId)
				.orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));
	}

	private void assertActiveParticipant(Long roomId, Long userId) {
		ChatRoomParticipant participant = chatRoomParticipantMapper.findByRoomAndUser(roomId, userId)
				.orElseThrow(() -> new ForbiddenException("Not a participant of this chat room"));

		if (participant.getParticipantStatus() != ParticipantStatus.ACTIVE) {
			throw new ForbiddenException("Not a participant of this chat room");
		}
	}

	private void addParticipant(Long roomId, Long userId, ParticipantRole role) {
		ChatRoomParticipant participant = new ChatRoomParticipant();
		participant.setChatRoomId(roomId);
		participant.setUserId(userId);
		participant.setParticipantRole(role);
		participant.setParticipantStatus(ParticipantStatus.ACTIVE);
		chatRoomParticipantMapper.insert(participant);
	}

	private ChatRoomResponse toResponse(ChatRoom room, int participantCount, ParticipantRole myParticipantRole,
										String lastMessage) {
		return ChatRoomResponse.builder()
				.id(room.getId())
				.roomType(room.getRoomType())
				.referenceType(room.getReferenceType())
				.referenceId(room.getReferenceId())
				.name(room.getName())
				.status(room.getStatus())
				.createdAt(room.getCreatedAt())
				.participantCount(participantCount)
				.myParticipantRole(myParticipantRole)
				.lastMessage(lastMessage)
				.build();
	}

	private Map<Long, String> findLastMessagesByRoomIds(List<Long> roomIds) {
		if (roomIds.isEmpty()) {
			return Map.of();
		}

		return chatMessageMapper.findLastMessagesByRoomIds(roomIds).stream()
				.collect(Collectors.toMap(ChatMessage::getChatRoomId, ChatMessage::getMessage, (a, b) -> a));
	}

	private ParticipantRole findMyParticipantRole(Long roomId, Long userId) {
		return chatRoomParticipantMapper.findByRoomAndUser(roomId, userId)
				.map(ChatRoomParticipant::getParticipantRole)
				.orElse(null);
	}

	private ChatRoomParticipantResponse toParticipantResponse(ChatRoomParticipant participant) {
		return ChatRoomParticipantResponse.builder()
				.userId(participant.getUserId())
				.nickname(participant.getNickname())
				.participantRole(participant.getParticipantRole())
				.participantStatus(participant.getParticipantStatus())
				.joinedAt(participant.getJoinedAt())
				.build();
	}

}

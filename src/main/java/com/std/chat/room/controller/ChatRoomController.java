package com.std.chat.room.controller;

import com.std.chat.auth.security.SecurityUtil;
import com.std.chat.room.docs.ChatRoomApiDocs;
import com.std.chat.room.dto.ChatRoomDetailResponse;
import com.std.chat.room.dto.ChatRoomResponse;
import com.std.chat.room.dto.CreateChatRoomRequest;
import com.std.chat.room.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatRoomController {

	private final ChatRoomService chatRoomService;

	@PostMapping
	@ChatRoomApiDocs.CreateChatRoomApi
	public ResponseEntity<ChatRoomResponse> createRoom(@Valid @RequestBody CreateChatRoomRequest request) {
		Long userId = SecurityUtil.getCurrentUserId();
		ChatRoomResponse response = chatRoomService.createRoom(userId, request);
		return ResponseEntity.created(URI.create("/api/chat-rooms/" + response.getId())).body(response);
	}

	@GetMapping
	@ChatRoomApiDocs.ListMyChatRoomsApi
	public ResponseEntity<List<ChatRoomResponse>> getMyRooms() {
		Long userId = SecurityUtil.getCurrentUserId();
		return ResponseEntity.ok(chatRoomService.getMyRooms(userId));
	}

	@GetMapping("/browse")
	@ChatRoomApiDocs.BrowseChatRoomsApi
	public ResponseEntity<List<ChatRoomResponse>> browseRooms() {
		Long userId = SecurityUtil.getCurrentUserId();
		return ResponseEntity.ok(chatRoomService.browseRooms(userId));
	}

	@GetMapping("/{id}")
	@ChatRoomApiDocs.GetChatRoomApi
	public ResponseEntity<ChatRoomDetailResponse> getRoom(@PathVariable Long id) {
		Long userId = SecurityUtil.getCurrentUserId();
		return ResponseEntity.ok(chatRoomService.getRoom(userId, id));
	}

	@PostMapping("/{id}/participants")
	@ChatRoomApiDocs.JoinChatRoomApi
	public ResponseEntity<Void> joinRoom(@PathVariable Long id) {
		Long userId = SecurityUtil.getCurrentUserId();
		chatRoomService.joinRoom(userId, id);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	@DeleteMapping("/{id}/participants/me")
	@ChatRoomApiDocs.LeaveChatRoomApi
	public ResponseEntity<Void> leaveRoom(@PathVariable Long id) {
		Long userId = SecurityUtil.getCurrentUserId();
		chatRoomService.leaveRoom(userId, id);
		return ResponseEntity.noContent().build();
	}

}

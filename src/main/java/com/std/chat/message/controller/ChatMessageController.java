package com.std.chat.message.controller;

import com.std.chat.auth.security.SecurityUtil;
import com.std.chat.message.docs.ChatMessageApiDocs;
import com.std.chat.message.dto.ChatMessageListResponse;
import com.std.chat.message.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-rooms/{roomId}/messages")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatMessageController {

	private final ChatMessageService chatMessageService;

	@GetMapping
	@ChatMessageApiDocs.ListMessagesApi
	public ResponseEntity<ChatMessageListResponse> getMessages(@PathVariable Long roomId,
															   @RequestParam(required = false) Long cursor,
															   @RequestParam(required = false) Integer size) {
		Long userId = SecurityUtil.getCurrentUserId();
		return ResponseEntity.ok(chatMessageService.getMessages(userId, roomId, cursor, size));
	}

}

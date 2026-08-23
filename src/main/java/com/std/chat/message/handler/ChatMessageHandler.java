package com.std.chat.message.handler;

import com.std.chat.message.dto.SendMessageRequest;
import com.std.chat.message.service.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {

	private final ChatMessageService chatMessageService;

	@MessageMapping("/rooms/{roomId}/messages")
	public void sendMessage(@DestinationVariable Long roomId,
							@Valid @Payload SendMessageRequest request,
							Principal principal) {
		Long userId = Long.parseLong(principal.getName());
		chatMessageService.sendMessage(userId, roomId, request.getMessage());
	}

}

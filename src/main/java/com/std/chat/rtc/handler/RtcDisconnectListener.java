package com.std.chat.rtc.handler;

import java.security.Principal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.std.chat.rtc.service.RtcSignalingService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RtcDisconnectListener {

    private final RtcSignalingService rtcSignalingService;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal == null) {
            return;
        }
        Long userId = Long.parseLong(principal.getName());
        rtcSignalingService.handleDisconnect(userId, event.getSessionId());
    }
}

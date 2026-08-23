package com.std.chat.websocket.support;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StompDestinationUtils {

	private static final Pattern ROOM_SUBSCRIBE = Pattern.compile("^/sub/rooms/(\\d+)$");
	private static final Pattern ROOM_SEND = Pattern.compile("^/pub/rooms/(\\d+)/messages$");

	private StompDestinationUtils() {
	}

	public static Optional<Long> extractRoomIdFromSubscribe(String destination) {
		return extractRoomId(destination, ROOM_SUBSCRIBE);
	}

	public static Optional<Long> extractRoomIdFromSend(String destination) {
		return extractRoomId(destination, ROOM_SEND);
	}

	private static Optional<Long> extractRoomId(String destination, Pattern pattern) {
		if (destination == null) {
			return Optional.empty();
		}
		Matcher matcher = pattern.matcher(destination);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(Long.parseLong(matcher.group(1)));
	}

}

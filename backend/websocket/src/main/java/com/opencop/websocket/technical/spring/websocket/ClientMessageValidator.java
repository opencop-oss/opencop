// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import java.util.Set;
import java.util.UUID;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ClientMessageValidator {

  private static final String TOPIC_PREFIX = "/topic/";
  private static final String EVENT_DESTINATION_PREFIX = "/topic/queue/event/";
  private static final String WATCHBOX_DESTINATION_PREFIX = "/topic/watchbox/";
  private static final int MAX_DESTINATION_BYTES = 128;
  private static final Set<String> USER_DESTINATIONS =
      Set.of("/user/queue/history", "/user/queue/messages", "/user/queue/notification");

  Validation validate(SocketMessage message) {
    SocketMessage.Type type = SocketMessage.Type.forNumber(message.getTypeValue());
    if (type == null) {
      return Validation.rejected(ErrorCode.UNSUPPORTED_FRAME_TYPE, Operation.UNKNOWN, null);
    }
    return switch (type) {
      case SUBSCRIBE -> validateDestinationControl(message, Operation.SUBSCRIBE);
      case UNSUBSCRIBE -> validateDestinationControl(message, Operation.UNSUBSCRIBE);
      case HEARTBEAT -> validateHeartbeat(message);
      case SEND -> Validation.rejected(ErrorCode.SEND_NOT_SUPPORTED, Operation.SEND, null);
      case CONNECT ->
          Validation.rejected(ErrorCode.UNSUPPORTED_FRAME_TYPE, Operation.CONNECT, null);
      case ERROR -> Validation.rejected(ErrorCode.UNSUPPORTED_FRAME_TYPE, Operation.ERROR, null);
      case UNKNOWN ->
          Validation.rejected(ErrorCode.UNSUPPORTED_FRAME_TYPE, Operation.UNKNOWN, null);
      case UNRECOGNIZED ->
          Validation.rejected(ErrorCode.UNSUPPORTED_FRAME_TYPE, Operation.UNKNOWN, null);
    };
  }

  private Validation validateDestinationControl(SocketMessage message, Operation operation) {
    String destination = message.getDestination();
    if (exceedsDestinationByteLimit(destination)) {
      return Validation.rejected(ErrorCode.INVALID_DESTINATION, operation, null);
    }
    if (hasUnusedFields(message)) {
      return Validation.rejected(ErrorCode.INVALID_FRAME, operation, null);
    }
    return validateDestination(destination, operation);
  }

  private static boolean exceedsDestinationByteLimit(String destination) {
    int byteCount = 0;
    for (int offset = 0; offset < destination.length(); ) {
      int codePoint = destination.codePointAt(offset);
      byteCount += utf8ByteCount(codePoint);
      if (byteCount > MAX_DESTINATION_BYTES) {
        return true;
      }
      offset += Character.charCount(codePoint);
    }
    return false;
  }

  private static int utf8ByteCount(int codePoint) {
    if (codePoint <= 0x7F) {
      return 1;
    }
    if (codePoint <= 0x7FF) {
      return 2;
    }
    if (codePoint <= 0xFFFF) {
      return Character.isSurrogate((char) codePoint) ? 1 : 3;
    }
    return 4;
  }

  Validation validateDestination(@Nullable String destination, Operation operation) {
    if (destination == null) {
      return Validation.rejected(ErrorCode.INVALID_DESTINATION, operation, null);
    }
    if (USER_DESTINATIONS.contains(destination)) {
      return Validation.accepted(null);
    }
    if (destination.startsWith(EVENT_DESTINATION_PREFIX)) {
      return validateTopicDestination(
          destination, EVENT_DESTINATION_PREFIX, TopicFamily.LIVE_EVENT, operation);
    }
    if (destination.startsWith(WATCHBOX_DESTINATION_PREFIX)) {
      return validateTopicDestination(
          destination, WATCHBOX_DESTINATION_PREFIX, TopicFamily.WATCHBOX, operation);
    }
    if (destination.startsWith(TOPIC_PREFIX)) {
      return Validation.rejected(ErrorCode.UNKNOWN_TOPIC, operation, destination);
    }
    return Validation.rejected(ErrorCode.INVALID_DESTINATION, operation, destination);
  }

  private static Validation validateTopicDestination(
      String destination, String prefix, TopicFamily family, Operation operation) {
    String id = destination.substring(prefix.length());
    try {
      UUID parsed = UUID.fromString(id);
      if (id.equals(parsed.toString())) {
        return Validation.accepted(new TopicDestination(family, parsed));
      }
    } catch (IllegalArgumentException _) {
      // Classified as an invalid topic below.
    }
    return Validation.rejected(ErrorCode.INVALID_TOPIC, operation, destination);
  }

  private Validation validateHeartbeat(SocketMessage message) {
    if (!message.getDestination().isEmpty() || hasUnusedFields(message)) {
      return Validation.rejected(ErrorCode.INVALID_FRAME, Operation.HEARTBEAT, null);
    }
    return Validation.accepted(null);
  }

  private static boolean hasUnusedFields(SocketMessage message) {
    return message.getHeadersCount() != 0
        || !message.getPayload().isEmpty()
        || message.getCompressed();
  }

  sealed interface Validation permits Accepted, Rejected {
    static Validation accepted(@Nullable TopicDestination topicDestination) {
      return new Accepted(topicDestination);
    }

    static Validation rejected(
        ErrorCode errorCode, Operation operation, @Nullable String rejectedDestination) {
      return new Rejected(new Rejection(errorCode, operation, rejectedDestination));
    }
  }

  record Accepted(@Nullable TopicDestination topicDestination) implements Validation {}

  record TopicDestination(TopicFamily family, UUID id) {}

  enum TopicFamily {
    LIVE_EVENT,
    WATCHBOX
  }

  record Rejected(Rejection rejection) implements Validation {}

  public record Rejection(
      ErrorCode errorCode, Operation operation, @Nullable String rejectedDestination) {}

  public enum Operation {
    SUBSCRIBE,
    UNSUBSCRIBE,
    HEARTBEAT,
    SEND,
    CONNECT,
    ERROR,
    UNKNOWN,
    TEXT
  }

  public enum ErrorCode {
    INVALID_DESTINATION("Invalid destination"),
    INVALID_TOPIC("Invalid topic destination id"),
    UNKNOWN_TOPIC("Unknown topic destination"),
    SEND_NOT_SUPPORTED("SEND not supported"),
    SUBSCRIBE_DENIED("Insufficient access"),
    INVALID_FRAME("Invalid frame"),
    UNSUPPORTED_FRAME_TYPE("Frame type not supported"),
    UNSUPPORTED_MESSAGE_TYPE("Text messages not supported");

    private final String reason;

    ErrorCode(String reason) {
      this.reason = reason;
    }

    public String reason() {
      return reason;
    }
  }
}

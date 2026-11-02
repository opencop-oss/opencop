// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import com.google.protobuf.ByteString;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.jspecify.annotations.Nullable;
import org.springframework.web.socket.BinaryMessage;

/**
 * The one construction site for protocol error frames, so admission rejections and server-initiated
 * revocations stay a single wire contract for clients.
 */
final class UserErrorFrames {

  private UserErrorFrames() {}

  static BinaryMessage errorFrame(String code, String destination, String reason) {
    return new BinaryMessage(errorMessage(code, "SUBSCRIBE", destination, reason).toByteArray());
  }

  static SocketMessage errorMessage(
      String code, String operation, @Nullable String rejectedDestination, String reason) {
    var builder =
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.ERROR)
            .putHeaders("code", code)
            .putHeaders("operation", operation)
            .setPayload(ByteString.copyFromUtf8(reason))
            .setCompressed(false);
    if (rejectedDestination != null) {
      builder.putHeaders("rejected-destination", rejectedDestination);
    }
    return builder.build();
  }
}

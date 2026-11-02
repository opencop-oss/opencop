// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.gateway.websocket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SocketProducer {

  void sendToUser(UUID userId, String destination, byte[] event, boolean compress);

  /**
   * Sends a frame to the user and returns {@code true} when at least one local subscribed session
   * received it. Returns {@code false} when no local session for the user exists or none of their
   * sessions are subscribed to the destination.
   */
  default boolean sendToUser(
      UUID userId,
      String destination,
      byte[] event,
      Map<String, String> headers,
      boolean compress) {
    sendToUser(userId, destination, event, compress);
    // Default: callers that do not care about delivery confirmation can ignore the return value.
    // Implementations that have real session visibility should override this.
    return true;
  }

  void broadcast(String topic, byte[] event, boolean compress);

  /**
   * Sends a binary frame to all local sessions subscribed for the user and awaits write completion.
   * Returns a future that resolves to NO_SESSION when the user has no local subscribed session,
   * DELIVERED when at least one session write completed successfully, or completes exceptionally
   * when all session writes failed.
   *
   * <p>Existing in-tree fakes that do not override this method receive an already failed future
   * containing {@link UnsupportedOperationException}; this keeps awaited callers on the
   * asynchronous completion path.
   */
  default CompletableFuture<SendAttemptResult> sendToUserAwait(
      UUID userId,
      String destination,
      byte[] event,
      Map<String, String> headers,
      boolean compress) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("sendToUserAwait not implemented"));
  }
}

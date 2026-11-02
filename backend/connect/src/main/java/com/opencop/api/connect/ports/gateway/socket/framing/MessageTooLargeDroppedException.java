// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.socket.framing;

/**
 * Exception thrown when a message exceeds maxMessageSize and has been dropped.
 *
 * <p>This is a recoverable exception - the framing strategy has already resynced to the next
 * message boundary before throwing this exception. The caller can safely continue reading the next
 * message from the stream.
 *
 * <p>The stream position after this exception is thrown: - Positioned immediately after the
 * delimiter that ended the oversized message - Ready to read the next message
 */
public class MessageTooLargeDroppedException extends FramingException {

  public MessageTooLargeDroppedException(String message) {
    super(message);
  }

  public MessageTooLargeDroppedException(String message, Throwable cause) {
    super(message, cause);
  }
}

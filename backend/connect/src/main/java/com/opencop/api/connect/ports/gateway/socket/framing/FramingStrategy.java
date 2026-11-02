// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.socket.framing;

import java.io.IOException;
import java.io.InputStream;

/**
 * Strategy for reading complete messages from a TCP byte stream.
 *
 * <p>TCP provides a continuous stream of bytes with no built-in message boundaries. FramingStrategy
 * implementations define how to split that stream into discrete messages.
 *
 * <p>Common strategies: - Delimiter-based: Read until delimiter (e.g., newline, XML closing tag) -
 * Length-prefixed: First N bytes indicate message length - Fixed-size: Every message is exactly N
 * bytes
 */
public interface FramingStrategy {

  /**
   * Implementations should avoid throwing for recoverable framing conditions because propagating an
   * IOException terminates the owning socket connection.
   *
   * <p>Read from input stream until a complete message is available.
   *
   * <p>The input stream is indefinitely long (think long-lived server pushing data to us) and
   * represents multiple records. You will need to call this in a loop to subscribe to records in an
   * input stream.
   *
   * <p>This method blocks until: - A complete message has been read (returns byte[]) - End of
   * stream is reached (returns null) - An error occurs (throws IOException)
   *
   * @param input The socket input stream to read from
   * @return Complete message as byte[], or null if EOF
   * @throws IOException If read error occurs
   * @throws FramingException If message framing is corrupted (e.g., message exceeds max size)
   */
  byte[] readMessage(InputStream input) throws IOException;
}

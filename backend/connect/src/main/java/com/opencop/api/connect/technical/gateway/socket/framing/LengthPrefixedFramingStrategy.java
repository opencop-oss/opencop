// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.framing;

import com.opencop.api.connect.ports.gateway.socket.framing.FramingException;
import com.opencop.api.connect.ports.gateway.socket.framing.FramingStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Length-prefixed framing strategy for binary protocols.
 *
 * <p>Message Format: [4 bytes: message length N (unsigned int, big-endian)] [N bytes: message
 * payload]
 *
 * <p>This is the standard framing approach for binary protocols like: - ASTERIX (air traffic
 * surveillance) - Military tactical data links - Custom binary telemetry protocols
 *
 * <p>Unlike delimiter-based framing, length-prefixed framing: - Works with arbitrary binary data
 * (no special characters needed) - Allows exact message boundaries without scanning entire payload
 * - Efficient for large messages (no byte-by-byte delimiter matching)
 *
 * <p>Example: Binary position report (16-byte payload): [0x00, 0x00, 0x00, 0x10] → length = 16
 * [0xDE, 0xAD, 0xBE, 0xEF, ...] → 16 bytes of payload
 *
 * <p>Security Considerations: - Enforces maxMessageSize to prevent memory exhaustion attacks -
 * Validates length prefix is reasonable before allocating buffer - Fails fast if length prefix
 * indicates corrupt data
 */
public class LengthPrefixedFramingStrategy implements FramingStrategy {

  private static final int LENGTH_PREFIX_SIZE = 4; // 4 bytes for 32-bit unsigned int
  private final int maxMessageSize;

  /** Create length-prefixed framing strategy with default 10MB max message size. */
  public LengthPrefixedFramingStrategy() {
    this(10 * 1024 * 1024); // 10MB default
  }

  /**
   * Create length-prefixed framing strategy with specified max message size.
   *
   * @param maxMessageSize Maximum message size in bytes (protects against memory exhaustion)
   */
  public LengthPrefixedFramingStrategy(int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  @Override
  public byte[] readMessage(InputStream input) throws IOException {
    // Read 4-byte length prefix (big-endian unsigned int)
    byte[] lengthBytes = readExactly(input, LENGTH_PREFIX_SIZE);
    if (lengthBytes == null) {
      return null; // Clean EOF before length prefix
    }

    // note: this handles endianess, `.wrap()` is big-endian which matches network protocols
    int messageLength = ByteBuffer.wrap(lengthBytes).getInt();

    // validate length before allocating buffer
    if (messageLength < 0) {
      throw new FramingException(
          "Invalid message length: "
              + messageLength
              + " (negative value indicates corrupt length prefix)");
    }

    if (messageLength == 0) {
      throw new FramingException("Invalid message length: 0 (zero-length messages not allowed)");
    }

    // error handling is different here versus the delimiter framing strategy
    // without a delimiter, you cannot resync
    // killing the connection and relying on the server to start sending valid data on reconnect is
    // the only viable strategy
    if (messageLength > maxMessageSize) {
      throw new FramingException(
          "Message length "
              + messageLength
              + " exceeds max size of "
              + maxMessageSize
              + " bytes. "
              + "Possible length prefix corruption or malicious data.");
    }

    // no delimiter: we're reading forward exactly how many bytes the message length (first 4 bytes)
    // indicated
    byte[] payload = readExactly(input, messageLength);
    if (payload == null) {
      throw new FramingException(
          "Incomplete message: Expected "
              + messageLength
              + " bytes after length prefix, but stream ended");
    }

    return payload;
  }

  /**
   * Read exactly N bytes from input stream.
   *
   * <p>This is critical for length-prefixed protocols - we MUST read the exact number of bytes
   * specified, not fewer. InputStream.read(byte[]) may return fewer bytes than requested, so we
   * loop until we have them all.
   *
   * @param input InputStream to read from
   * @param n Number of bytes to read
   * @return Exactly N bytes, or null if EOF reached before reading any bytes
   * @throws IOException If read error occurs
   * @throws FramingException If EOF reached after reading partial data
   */
  private byte[] readExactly(InputStream input, int n) throws IOException {
    byte[] buffer = new byte[n];
    int totalRead = 0;

    while (totalRead < n) {
      int bytesRead = input.read(buffer, totalRead, n - totalRead);

      if (bytesRead == -1) {
        // EOF reached
        if (totalRead == 0) {
          return null; // Clean EOF, no partial data
        } else {
          throw new FramingException(
              "Unexpected EOF: Read " + totalRead + " of " + n + " expected bytes");
        }
      }

      totalRead += bytesRead;
    }

    return buffer;
  }
}

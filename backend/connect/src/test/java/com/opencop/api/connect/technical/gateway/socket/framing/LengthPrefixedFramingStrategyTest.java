// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.framing;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.api.connect.ports.gateway.socket.framing.FramingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LengthPrefixedFramingStrategyTest {

  @Test
  void readMessage_readsBasicLengthPrefixedMessage() throws IOException {
    var strategy = new LengthPrefixedFramingStrategy();

    // Create: [4-byte length: 5] [5-byte payload: "Hello"]
    var buffer = ByteBuffer.allocate(9);
    buffer.putInt(5); // Length prefix
    buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
    var input = new ByteArrayInputStream(buffer.array());

    byte[] message = strategy.readMessage(input);

    assertEquals("Hello", new String(message, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_readsMultipleLengthPrefixedMessages() throws IOException {
    var strategy = new LengthPrefixedFramingStrategy();

    // Two messages: "Hi" (2 bytes) and "World" (5 bytes)
    var buffer = ByteBuffer.allocate(15);
    buffer.putInt(2);
    buffer.put("Hi".getBytes(StandardCharsets.UTF_8));
    buffer.putInt(5);
    buffer.put("World".getBytes(StandardCharsets.UTF_8));
    var input = new ByteArrayInputStream(buffer.array());

    var message1 = strategy.readMessage(input);
    var message2 = strategy.readMessage(input);
    var message3 = strategy.readMessage(input);

    assertEquals("Hi", new String(message1, StandardCharsets.UTF_8));
    assertEquals("World", new String(message2, StandardCharsets.UTF_8));
    assertNull(message3); // Clean EOF
  }

  @Test
  void readMessage_handlesBinaryPayload() throws IOException {
    var strategy = new LengthPrefixedFramingStrategy();

    // Binary data: 4 random bytes
    byte[] binaryData = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
    var buffer = ByteBuffer.allocate(8);
    buffer.putInt(4); // Length
    buffer.put(binaryData);
    var input = new ByteArrayInputStream(buffer.array());

    var message = strategy.readMessage(input);

    assertArrayEquals(binaryData, message);
  }

  @Test
  void readMessage_throwsExceptionWhenLengthExceedsMax() {
    var strategy = new LengthPrefixedFramingStrategy(10); // Max 10 bytes

    // Claim 100-byte message (exceeds max)
    var buffer = ByteBuffer.allocate(4);
    buffer.putInt(100);
    var input = new ByteArrayInputStream(buffer.array());

    var exception = assertThrows(FramingException.class, () -> strategy.readMessage(input));

    assertTrue(exception.getMessage().contains("exceeds max size"));
  }

  @Test
  void readMessage_throwsExceptionOnNegativeLength() {
    var strategy = new LengthPrefixedFramingStrategy();

    // Negative length (corrupt or malicious data)
    var buffer = ByteBuffer.allocate(4);
    buffer.putInt(-1);
    var input = new ByteArrayInputStream(buffer.array());

    var exception = assertThrows(FramingException.class, () -> strategy.readMessage(input));

    assertTrue(exception.getMessage().contains("negative value"));
  }

  @Test
  void readMessage_throwsExceptionOnZeroLength() {
    var strategy = new LengthPrefixedFramingStrategy();

    var buffer = ByteBuffer.allocate(4);
    buffer.putInt(0);
    var input = new ByteArrayInputStream(buffer.array());

    var exception = assertThrows(FramingException.class, () -> strategy.readMessage(input));

    assertTrue(exception.getMessage().contains("zero-length messages not allowed"));
  }

  @Test
  void readMessage_throwsExceptionOnIncompletePayload() {
    var strategy = new LengthPrefixedFramingStrategy();

    // Length says 10 bytes, but only 5 bytes follow the 4 byte length
    var buffer = ByteBuffer.allocate(9);
    buffer.putInt(10); // Claim 10 bytes
    buffer.put("12345".getBytes(StandardCharsets.UTF_8)); // Only 5 bytes
    var input = new ByteArrayInputStream(buffer.array());

    var exception = assertThrows(FramingException.class, () -> strategy.readMessage(input));

    assertTrue(exception.getMessage().contains("Unexpected EOF"));
  }

  @Test
  void readMessage_returnsNullOnCleanEOF() throws IOException {
    var strategy = new LengthPrefixedFramingStrategy();
    var input = new ByteArrayInputStream(new byte[0]);

    var message = strategy.readMessage(input);

    assertNull(message);
  }

  @Test
  void readMessage_handlesLargeMessage() throws IOException {
    var strategy = new LengthPrefixedFramingStrategy(1024 * 1024); // 1MB max

    // Create 1000-byte message
    byte[] largePayload = new byte[1000];
    Arrays.fill(largePayload, (byte) 'A');

    var buffer = ByteBuffer.allocate(1004);
    buffer.putInt(1000);
    buffer.put(largePayload);
    var input = new ByteArrayInputStream(buffer.array());

    var message = strategy.readMessage(input);

    assertEquals(1000, message.length);
    assertEquals('A', message[0]);
    assertEquals('A', message[999]);
  }

  /**
   * This test demonstrates the key difference between binary and delimiter-based framing:
   *
   * <p>Delimiter mode (see DelimiterFramingStrategyTest): - Can resync to next delimiter when
   * message is too large - Throws MessageTooLargeDroppedException (recoverable) - Connection
   * continues reading subsequent messages
   *
   * <p>Binary mode (this test): - Cannot resync without delimiters - Throws FramingException
   * (unrecoverable) - Connection must be killed and re-established
   */
  @Test
  void readMessage_throwsUnrecoverableExceptionWhenLengthExceedsMax_cannotResyncWithoutDelimiter() {
    var strategy = new LengthPrefixedFramingStrategy(10); // Max 10 bytes

    // Simulate stream with oversized message (50 bytes) followed by valid message
    // [length: 50][50 bytes of data][length: 5][5 bytes of data]
    var buffer = ByteBuffer.allocate(63);

    // Oversized message: length prefix = 50, exceeds max of 10
    buffer.putInt(50);
    byte[] oversizedPayload = new byte[50];
    Arrays.fill(oversizedPayload, (byte) 'X');
    buffer.put(oversizedPayload);

    // Valid message that follows (would be readable if we could resync)
    buffer.putInt(5);
    buffer.put("VALID".getBytes(StandardCharsets.UTF_8));

    var input = new ByteArrayInputStream(buffer.array());

    // First read: throws FramingException (not MessageTooLargeDroppedException)
    // This is unrecoverable because there's no delimiter to resync to
    var exception = assertThrows(FramingException.class, () -> strategy.readMessage(input));

    assertTrue(exception.getMessage().contains("exceeds max size"));
    assertTrue(exception.getMessage().contains("50"));
    assertTrue(exception.getMessage().contains("10"));

    // Unlike delimiter mode, we CANNOT continue reading
    // The stream is positioned after the length prefix (4 bytes into the oversized message)
    // There's no way to find where the next message starts without a delimiter
    // Attempting to read would interpret the payload bytes as a new length prefix = garbage

    // This demonstrates why binary mode must kill the connection and reconnect
    // (The TcpSocketConnector's retry logic handles reconnection automatically)
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.framing;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.api.connect.ports.gateway.socket.framing.MessageTooLargeDroppedException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DelimiterFramingStrategyTest {

  @Test
  void readMessage_readsUntilNewlineDelimiter() throws IOException {
    var strategy = new DelimiterFramingStrategy("\n", false); // Strip delimiter
    var input = new ByteArrayInputStream("Hello World\n".getBytes(StandardCharsets.UTF_8));

    var message = strategy.readMessage(input);

    assertEquals("Hello World", new String(message, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_readsMultipleMessages() throws IOException {
    var strategy = new DelimiterFramingStrategy("\n", false); // Strip delimiter
    var input = new ByteArrayInputStream("First\nSecond\nThird\n".getBytes(StandardCharsets.UTF_8));

    var message1 = strategy.readMessage(input);
    var message2 = strategy.readMessage(input);
    var message3 = strategy.readMessage(input);
    var message4 = strategy.readMessage(input);

    assertEquals("First", new String(message1, StandardCharsets.UTF_8));
    assertEquals("Second", new String(message2, StandardCharsets.UTF_8));
    assertEquals("Third", new String(message3, StandardCharsets.UTF_8));
    assertNull(message4); // EOF
  }

  @Test
  void readMessage_handlesMultiCharDelimiterStrip() throws IOException {
    var strategy = new DelimiterFramingStrategy("||", false); // Strip delimiter
    var input = new ByteArrayInputStream("r1||r2".getBytes(StandardCharsets.UTF_8));

    var message = strategy.readMessage(input);

    assertEquals("r1", new String(message, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_handlesMultiCharDelimiterInclude() throws IOException {
    var strategy = new DelimiterFramingStrategy("</event>", true); // Include delimiter
    var input =
        new ByteArrayInputStream(
            "<event>r1</event><event>r2</event>".getBytes(StandardCharsets.UTF_8));

    var message = strategy.readMessage(input);

    assertEquals("<event>r1</event>", new String(message, StandardCharsets.UTF_8));
  }

  // Note: ByteArrayInputStream doesn't actually split reads - the algorithm reads byte-by-byte
  // regardless.
  // This test validates that multi-character delimiters work correctly when the delimiter spans
  // multiple positions in the stream (e.g., "</event>" where the algorithm must match 8 consecutive
  // bytes).
  @Test
  void readMessage_handlesMultiCharDelimiterMatching() throws IOException {
    var strategy = new DelimiterFramingStrategy("</event>", false); // Strip delimiter

    // Multi-character delimiter split across what could be separate socket reads
    var data = "<event>data</ev".getBytes(StandardCharsets.UTF_8);
    var data2 = "ent>more".getBytes(StandardCharsets.UTF_8);

    var combined = new byte[data.length + data2.length];
    System.arraycopy(data, 0, combined, 0, data.length);
    System.arraycopy(data2, 0, combined, data.length, data2.length);

    var input = new ByteArrayInputStream(combined);
    var message = strategy.readMessage(input);

    assertEquals("<event>data", new String(message, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_throwsExceptionWhenMaxSizeExceeded() {
    var strategy =
        new DelimiterFramingStrategy(
            "\n", false, StandardCharsets.UTF_8.name(), 10); // Max 10 bytes
    var input =
        new ByteArrayInputStream(
            "This message exceeds our max length of 10\n".getBytes(StandardCharsets.UTF_8));

    var exception =
        assertThrows(MessageTooLargeDroppedException.class, () -> strategy.readMessage(input));

    assertTrue(exception.getMessage().contains("exceeding"));
  }

  @Test
  void readMessage_returnsNullOnEOF() throws IOException {
    var strategy = new DelimiterFramingStrategy("\n", false); // Strip delimiter
    var input = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

    var message = strategy.readMessage(input);

    assertNull(message);
  }

  // this is pretty logical for `includeDelimiter: false` - think new lines vs xml closing tags
  @Test
  void readMessage_returnsPartialMessageOnEOFWithoutDelimiterIncludeFalse() throws IOException {
    var strategy = new DelimiterFramingStrategy("\n", false); // Strip delimiter
    var input = new ByteArrayInputStream("Incomplete".getBytes(StandardCharsets.UTF_8));

    var message = strategy.readMessage(input);

    assertEquals("Incomplete", new String(message, StandardCharsets.UTF_8));
  }

  // This case for including the delimiter (think xml) may make less sense to process a partial
  // message.
  // I think later validation will reject the message and write it to a Dead Letter Topic though.
  // There's probably still observability value in recording that we got the partial record.
  @Test
  void readMessage_returnsPartialMessageOnEOFWithoutDelimiterIncludeTrue() throws IOException {
    var strategy = new DelimiterFramingStrategy("</event>", true);
    var input = new ByteArrayInputStream("<event>r1".getBytes(StandardCharsets.UTF_8));

    var message = strategy.readMessage(input);

    assertEquals("<event>r1", new String(message, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_handlesEmptyMessage() throws IOException {
    var strategy = new DelimiterFramingStrategy("\n", false); // Strip delimiter
    var input = new ByteArrayInputStream("\n".getBytes(StandardCharsets.UTF_8));

    var message = strategy.readMessage(input);

    assertEquals("", new String(message, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_handlesConsecutiveDelimiters() throws IOException {
    var strategy = new DelimiterFramingStrategy("\n", false); // Strip delimiter
    var input = new ByteArrayInputStream("First\n\nThird\n".getBytes(StandardCharsets.UTF_8));

    var message1 = strategy.readMessage(input);
    var message2 = strategy.readMessage(input);
    var message3 = strategy.readMessage(input);

    assertEquals("First", new String(message1, StandardCharsets.UTF_8));
    assertEquals(
        "", new String(message2, StandardCharsets.UTF_8)); // Empty message between delimiters
    assertEquals("Third", new String(message3, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_handlesPartialDelimiterMatchInContent() throws IOException {
    var strategy = new DelimiterFramingStrategy("--END--", false); // Strip delimiter
    // Message contains "--" (partial delimiter match) but not full delimiter
    // This tests the matchIndex reset logic when a partial match fails
    var input =
        new ByteArrayInputStream(
            "Data with -- dashes--END--More data--END--".getBytes(StandardCharsets.UTF_8));

    var message1 = strategy.readMessage(input);
    var message2 = strategy.readMessage(input);

    assertEquals("Data with -- dashes", new String(message1, StandardCharsets.UTF_8));
    assertEquals("More data", new String(message2, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_handlesOverlappingDelimiterPattern() throws IOException {
    var strategy = new DelimiterFramingStrategy("ABA", false); // Strip delimiter
    // Stream: "AABA" - First 'A' starts matching, second 'A' breaks match
    // WITHOUT lines 127-129: Second 'A' is ignored, 'B' doesn't match, third 'A' starts new match
    //   → EOF with incomplete match → returns "AABA"
    // WITH lines 127-129: Second 'A' recognized as new match start
    //   → Finds "ABA" → returns "A"
    var input = new ByteArrayInputStream("AABA".getBytes(StandardCharsets.UTF_8));

    var message = strategy.readMessage(input);

    // Should find "ABA" starting at second 'A', returning the first 'A' as message
    assertEquals("A", new String(message, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_handlesMultiByteUTF8Characters() throws IOException {
    var strategy = new DelimiterFramingStrategy("\n", false); // Strip delimiter
    // Test with Mandarin Chinese and emoji (multi-byte UTF-8 characters)
    // 世界 = "world" in Chinese (3 bytes per character in UTF-8)
    // 🎉 = party popper emoji (4 bytes in UTF-8)
    var input = new ByteArrayInputStream("Hello 世界\nEmoji 🎉\n".getBytes(StandardCharsets.UTF_8));

    var message1 = strategy.readMessage(input);
    var message2 = strategy.readMessage(input);

    assertEquals("Hello 世界", new String(message1, StandardCharsets.UTF_8));
    assertEquals("Emoji 🎉", new String(message2, StandardCharsets.UTF_8));
  }

  @Test
  void readMessage_dropsOversizedMessageAndContinuesReading_includeDelimiterMode()
      throws IOException {
    // An oversized message is followed by a valid message to prove reading resumes after the drop.
    String input = "<t>this is a very long message exceeding max</t><t>good</t>";
    var strategy = new DelimiterFramingStrategy("</t>", true, "UTF-8", 20);

    var stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

    // read on too large message throws recoverable exception (this avoids introducing a wrapper
    // return type)
    MessageTooLargeDroppedException exception =
        assertThrows(MessageTooLargeDroppedException.class, () -> strategy.readMessage(stream));
    assertTrue(exception.getMessage().contains("20"));

    // resync happened so second read is good
    byte[] msg2 = strategy.readMessage(stream);
    assertEquals("<t>good</t>", new String(msg2, StandardCharsets.UTF_8));

    assertNull(strategy.readMessage(stream));
  }

  // like the above test but with the other delimiter strategy
  @Test
  void readMessage_dropsOversizedMessageAndContinuesReading_splitDelimiterMode()
      throws IOException {
    String input = "this is a very long message that exceeds maximum size\ngood message\n";
    var strategy = new DelimiterFramingStrategy("\n", false, "UTF-8", 20);

    var stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

    assertThrows(MessageTooLargeDroppedException.class, () -> strategy.readMessage(stream));

    byte[] msg2 = strategy.readMessage(stream);
    assertEquals("good message", new String(msg2, StandardCharsets.UTF_8));

    assertNull(strategy.readMessage(stream));
  }
}

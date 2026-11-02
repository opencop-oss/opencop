// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.framing;

import com.opencop.api.connect.ports.gateway.socket.framing.FramingStrategy;
import com.opencop.api.connect.ports.gateway.socket.framing.MessageTooLargeDroppedException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delimiter-based framing strategy.
 *
 * <p>Reads from input stream until delimiter is found, then returns the complete message. Commonly
 * used for text protocols (JSON with newline, XML with closing tags, etc.)
 *
 * <p>Delimiter Behavior: - Split delimiter (includeDelimiter=false): Delimiter is BETWEEN records,
 * stripped from message Example: AIS with "\n" → message1\nmessage2 → returns "message1",
 * "message2" - End delimiter (includeDelimiter=true): Delimiter is PART of record, kept in message
 * Example: XML with "</ship>" → <ship>...</ship> → returns "<ship>...</ship>"
 *
 * <p>Encoding: - The delimiter string must be encoded to bytes for byte-level comparison with the
 * stream - Encoding matters because different encodings represent characters with different byte
 * sequences - Example: In UTF-8, newline is 0x0A (1 byte), but in UTF-16 it's 0x00 0x0A (2 bytes) -
 * Currently only UTF-8 is supported and tested (throws UnsupportedOperationException for others)
 *
 * <p>Examples: - Newline-delimited JSON: delimiter = "\n", includeDelimiter = false, encoding =
 * "UTF-8" - XML events: delimiter = "</event>", includeDelimiter = true, encoding = "UTF-8" - NMEA
 * sentences: delimiter = "\r\n", includeDelimiter = false, encoding = "UTF-8"
 */
public class DelimiterFramingStrategy implements FramingStrategy {

  private static final Logger log = LoggerFactory.getLogger(DelimiterFramingStrategy.class);

  /** Default max message size (1MB). Used by test constructors; production uses config. */
  private static final int DEFAULT_MAX_MESSAGE_SIZE = 1_024 * 1_024;

  /** Only UTF-8 is currently supported. */
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  private final byte[] delimiter;
  private final boolean includeDelimiter;
  private final int maxMessageSize;
  private final Charset charset;

  /**
   * Create delimiter framing strategy with specified delimiter behavior (UTF-8, 1MB max).
   *
   * <p>Intended for tests that don't need to specify max message size.
   *
   * @param delimiter String delimiter (e.g., "\n", "</event>")
   * @param includeDelimiter true = keep delimiter in message (end delimiter), false = strip it
   *     (split delimiter)
   */
  public DelimiterFramingStrategy(String delimiter, boolean includeDelimiter) {
    this(delimiter, includeDelimiter, DEFAULT_MAX_MESSAGE_SIZE);
  }

  /**
   * Create delimiter framing strategy with specified delimiter behavior and max size (UTF-8).
   *
   * <p>Intended for production use where max message size comes from configuration.
   *
   * @param delimiter String delimiter (e.g., "\n", "</event>")
   * @param includeDelimiter true = keep delimiter in message (end delimiter), false = strip it
   *     (split delimiter)
   * @param maxMessageSize Maximum message size in bytes (protects against unbounded growth)
   */
  public DelimiterFramingStrategy(String delimiter, boolean includeDelimiter, int maxMessageSize) {
    this(delimiter, includeDelimiter, DEFAULT_CHARSET.name(), maxMessageSize);
  }

  /**
   * Create delimiter framing strategy with all parameters.
   *
   * @param delimiter String delimiter (e.g., "\n", "</event>")
   * @param includeDelimiter true = keep delimiter in message (end delimiter), false = strip it
   *     (split delimiter)
   * @param encoding Character encoding (e.g., "UTF-8") - only UTF-8 is currently supported
   * @param maxMessageSize Maximum message size in bytes
   * @throws UnsupportedOperationException if encoding is not UTF-8
   */
  public DelimiterFramingStrategy(
      String delimiter, boolean includeDelimiter, String encoding, int maxMessageSize) {
    // Validate encoding: only UTF-8 is tested and supported (TODO: add other encodings if needed)
    if (!DEFAULT_CHARSET.name().equalsIgnoreCase(encoding)) {
      throw new UnsupportedOperationException(
          "Only UTF-8 encoding is currently supported. Requested: "
              + encoding
              + ". "
              + "To add support for other encodings, update DelimiterFramingStrategy with proper testing.");
    }

    this.charset = Charset.forName(encoding);
    this.delimiter = delimiter.getBytes(this.charset);
    this.includeDelimiter = includeDelimiter;
    this.maxMessageSize = maxMessageSize;
  }

  /**
   * We are reading a stream of an indefinitely long sequence bytes representing multiple records.
   * We read until we have a complete record and then return it. This means you need to call this
   * method in a loop to consume an input stream.
   *
   * @param input The socket input stream to read from
   * @return byte[] representing a single record
   * @throws IOException
   */
  @Override
  public byte[] readMessage(InputStream input) throws IOException {
    var buffer = new ByteArrayOutputStream();

    // we read forward a byte at a time and track when we *might* be reading a delimiter
    // this removes the assumption that the records are *also* newline delimited
    int b;
    int delimiterIndex = 0;
    while ((b = input.read()) != -1) {
      buffer.write(b);

      if (b == delimiter[delimiterIndex]) {
        delimiterIndex++;

        // Found complete delimiter
        if (delimiterIndex == delimiter.length) {
          var message = buffer.toByteArray();

          if (includeDelimiter) {
            return message;
          } else {
            // Split delimiter: strip delimiter from message (e.g., \n)
            var messageWithoutDelimiter = new byte[message.length - delimiter.length];
            System.arraycopy(
                message, 0, messageWithoutDelimiter, 0, messageWithoutDelimiter.length);
            return messageWithoutDelimiter;
          }
        }
      } else {
        // Byte didn't match - reset delimiter matching
        delimiterIndex = 0;

        // Edge case: If we just wrote a byte that happens to be the FIRST delimiter byte,
        // we need to start matching from index 1
        // See test: readMessage_handlesOverlappingDelimiterPattern()
        // if we have input "AABA" and delim "ABA" we still need to extract "A"
        if (b == delimiter[0]) {
          delimiterIndex = 1;
        }
      }

      // Check if message is too large - if so, resync and throw recoverable exception
      if (buffer.size() > maxMessageSize) {
        byte[] initialForDebugging =
            Arrays.copyOf(buffer.toByteArray(), Math.min(100, buffer.size()));
        log.warn(
            "Message exceeded {} bytes. First 100 bytes: {}",
            maxMessageSize,
            new String(initialForDebugging, this.charset));

        // RESYNC: consume rest of oversized message until we find the delimiter
        // This positions the stream at the start of the NEXT message
        int resyncDelimiterIndex = delimiterIndex; // Continue from where we were
        while ((b = input.read()) != -1) {
          if (b == delimiter[resyncDelimiterIndex]) {
            resyncDelimiterIndex++;
            if (resyncDelimiterIndex == delimiter.length) {
              // Found delimiter, stream is now positioned after it (at next message)
              break;
            }
          } else {
            // Byte didn't match - reset delimiter matching
            resyncDelimiterIndex = 0;
            // Check if this byte happens to be the first delimiter byte
            if (b == delimiter[0]) {
              resyncDelimiterIndex = 1;
            }
          }
        }

        // Stream is now positioned at start of NEXT message (or EOF if delimiter never found)
        throw new MessageTooLargeDroppedException(
            "Dropped message exceeding " + maxMessageSize + " bytes");
      }
    }

    // EOF reached - return any remaining bytes (incomplete message)
    // We could handle this, but it would mix concerns (messages validated later in the pipeline)
    // and partial message is good for debugging
    if (buffer.size() > 0) {
      return buffer.toByteArray();
    }

    return null; // Clean EOF, no pending data
  }
}

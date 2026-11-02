// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reassembles complete KLV units from JavaCV packet payloads.
 *
 * <p>See {@code RtspKlvConnectorFactory} for why this stays separate from socket stream framing.
 *
 * <p>Three on-wire forms are normalised to full-key form before buffering (see {@link
 * #appendPacket}):
 *
 * <ol>
 *   <li>Full 16-byte MISB ST 0601 Universal Key — standard; no transformation needed.
 *   <li>Stripped SMPTE OID prefix — bytes [5..15] of the Universal Key present at packet start,
 *       first 5 bytes ({@code 06 0E 2B 34 02}) absent. Some senders rely on the RTP session
 *       description to convey the Universal Label and omit the SMPTE label prefix per packet. The 5
 *       missing bytes are prepended.
 *   <li>Bare local-set payload — no key at all; the entire packet is a complete ST 0601 local set.
 *       A synthetic Universal Key and BER-encoded length are prepended.
 * </ol>
 */
public final class KlvPacketFramer {

  // MISB ST 0601.8 section 6.4, requirement ST 0601.8-18 defines the UAS Local
  // Set 16-byte Universal Key. RidgeRun's public ST0601 GStreamer example uses
  // the same key as a practical cross-check:
  // https://upload.wikimedia.org/wikipedia/commons/1/19/MISB_Standard_0601.pdf#page=5
  // https://developer.ridgerun.com/wiki/index.php/RidgeRun_Metadata/Use_Cases/UAV_metadata_streaming
  public static final byte[] ST_0601_UNIVERSAL_KEY =
      new byte[] {
        0x06, 0x0e, 0x2b, 0x34, 0x02, 0x0b, 0x01, 0x01,
        0x0e, 0x01, 0x03, 0x01, 0x01, 0x00, 0x00, 0x00
      };

  // The 5-byte SMPTE UL designator prefix that some senders strip from each RTP packet.
  static final byte[] ST_0601_SMPTE_LABEL_PREFIX = {0x06, 0x0e, 0x2b, 0x34, 0x02};

  // Bytes [5..15] of the Universal Key — the MISB-specific portion that remains when
  // the SMPTE label prefix is stripped.
  public static final byte[] ST_0601_PARTIAL_KEY =
      Arrays.copyOfRange(
          ST_0601_UNIVERSAL_KEY, ST_0601_SMPTE_LABEL_PREFIX.length, ST_0601_UNIVERSAL_KEY.length);

  private static final int DEFAULT_MAX_BUFFERED_BYTES = 1024 * 1024;

  private final int maxBufferedBytes;
  private byte[] buffer = new byte[0];

  public KlvPacketFramer() {
    this(DEFAULT_MAX_BUFFERED_BYTES);
  }

  public KlvPacketFramer(int maxBufferedBytes) {
    if (maxBufferedBytes <= ST_0601_UNIVERSAL_KEY.length) {
      throw new IllegalArgumentException("maxBufferedBytes must exceed universal key length");
    }
    this.maxBufferedBytes = maxBufferedBytes;
  }

  /**
   * Normalises a single raw RTP KLV payload to full-key form and then appends it.
   *
   * <p>Use this when each call corresponds to one complete RTP payload packet. Normalisation is
   * skipped when a partial unit is already buffered from a previous packet (continuation fragment),
   * because continuation bytes carry no key of their own.
   *
   * <p>The three recognised forms are described in the class-level Javadoc.
   */
  public List<byte[]> appendPacket(byte[] packet) {
    if (packet == null || packet.length == 0) {
      return List.of();
    }
    byte[] normalized = buffer.length == 0 ? normalizeToFullKey(packet) : packet;
    return append(normalized);
  }

  public List<byte[]> append(byte[] packet) {
    if (packet == null || packet.length == 0) {
      return List.of();
    }

    appendToBuffer(packet);
    enforceMaxBuffer();

    var messages = new ArrayList<byte[]>();
    while (true) {
      var keyIndex = findKey(buffer, 0);
      if (keyIndex < 0) {
        keepPotentialKeySuffix();
        return messages;
      }
      if (keyIndex > 0) {
        buffer = Arrays.copyOfRange(buffer, keyIndex, buffer.length);
      }

      var parsed = parseCompleteLength();
      if (parsed == ParseResult.NEED_MORE_BYTES) {
        return messages;
      }
      if (parsed == ParseResult.MALFORMED) {
        dropFirstByte();
        continue;
      }

      int totalLength = parsed.totalLength();
      if (totalLength > maxBufferedBytes) {
        dropFirstByte();
        continue;
      }
      if (buffer.length < totalLength) {
        return messages;
      }

      messages.add(Arrays.copyOfRange(buffer, 0, totalLength));
      buffer = Arrays.copyOfRange(buffer, totalLength, buffer.length);
    }
  }

  public int bufferedBytes() {
    return buffer.length;
  }

  public void reset() {
    buffer = new byte[0];
  }

  private static byte[] normalizeToFullKey(byte[] packet) {
    // Form 1: full universal key present. Strip any leading junk before the key, then fix
    // non-compliant raw single-byte BER length (value > 127) if needed.
    int keyIndex = findKey(packet, 0);
    if (keyIndex >= 0) {
      byte[] keyStart =
          keyIndex == 0 ? packet : Arrays.copyOfRange(packet, keyIndex, packet.length);
      return normalizeForm1BerIfNeeded(keyStart);
    }
    // Form 2: SMPTE OID prefix stripped; bytes [5..15] of the universal key at packet start
    if (startsWith(packet, 0, ST_0601_PARTIAL_KEY)) {
      return normalizeForm2(packet);
    }
    // Guard: packet whose bytes all match a prefix of the universal key is a fragmented
    // full-key unit in flight — pass it to the buffer unchanged so the stream framer can
    // reassemble it with the continuation packet(s).
    if (couldBeKeyFragment(packet)) {
      return packet;
    }
    // Form 3: bare local-set payload — wrap only when the packet is plausibly a valid ST 0601
    // local set: at least 10 bytes (one meaningful TLV field), a non-zero first tag in the
    // single-byte range (1-127), and a first value length that fits within the packet.
    // Junk or truncated continuation fragments are passed through unchanged so the stream
    // framer can handle (and ultimately discard) them normally.
    if (looksLikeBareLocalSet(packet)) {
      return wrapAsKlvUnit(packet);
    }
    return packet;
  }

  private static byte[] normalizeForm1BerIfNeeded(byte[] packet) {
    if (packet.length < ST_0601_UNIVERSAL_KEY.length + 1) {
      return packet;
    }
    int berByte = Byte.toUnsignedInt(packet[ST_0601_UNIVERSAL_KEY.length]);
    // Detect raw (non-BER-compliant) single-byte length > 127.
    // Guard: only rewrite when (berByte & 0x7F) > 4, i.e. the byte cannot be a valid BER
    // long-form indicator (parseCompleteLength supports 1–4 extra bytes). Values 0x81–0x84 are
    // valid long-form BER and must be left unchanged. Values 0x85 and above would be MALFORMED
    // under standard BER; for those, treat the byte as the literal payload length.
    boolean rawBer =
        berByte > 0x7F
            && (berByte & 0x7F) > 4
            && (packet.length == ST_0601_UNIVERSAL_KEY.length + 1 + berByte);
    if (!rawBer) {
      return packet;
    }
    int payloadLen = berByte;
    byte[] normalized = new byte[ST_0601_UNIVERSAL_KEY.length + 2 + payloadLen];
    System.arraycopy(packet, 0, normalized, 0, ST_0601_UNIVERSAL_KEY.length);
    normalized[ST_0601_UNIVERSAL_KEY.length] = (byte) 0x81;
    normalized[ST_0601_UNIVERSAL_KEY.length + 1] = (byte) berByte;
    System.arraycopy(
        packet,
        ST_0601_UNIVERSAL_KEY.length + 1,
        normalized,
        ST_0601_UNIVERSAL_KEY.length + 2,
        payloadLen);
    updateMisbChecksum(normalized);
    return normalized;
  }

  private static byte[] normalizeForm2(byte[] packet) {
    if (packet.length <= ST_0601_PARTIAL_KEY.length) {
      // Partial key with no BER length byte — cannot normalise.
      // Pass through; the stream framer will discard it as junk.
      return packet;
    }
    int berByte = Byte.toUnsignedInt(packet[ST_0601_PARTIAL_KEY.length]);
    // Detect raw (non-BER-compliant) single-byte length > 127. Same guard as normalizeForm1:
    // only rewrite when (berByte & 0x7F) > 4, i.e. a valid long-form BER indicator (0x81–0x84)
    // is left unchanged and handled by the stream framer.
    boolean rawBer =
        berByte > 0x7F
            && (berByte & 0x7F) > 4
            && (packet.length == ST_0601_PARTIAL_KEY.length + 1 + berByte);
    byte[] normalized;
    if (rawBer) {
      // Rewrite as proper BER minimal long form: 0x81 <length-byte>
      int payloadLen = berByte;
      normalized = new byte[ST_0601_UNIVERSAL_KEY.length + 2 + payloadLen];
      System.arraycopy(ST_0601_UNIVERSAL_KEY, 0, normalized, 0, ST_0601_UNIVERSAL_KEY.length);
      normalized[ST_0601_UNIVERSAL_KEY.length] = (byte) 0x81;
      normalized[ST_0601_UNIVERSAL_KEY.length + 1] = (byte) berByte;
      System.arraycopy(
          packet,
          ST_0601_PARTIAL_KEY.length + 1,
          normalized,
          ST_0601_UNIVERSAL_KEY.length + 2,
          payloadLen);
    } else {
      // Standard short-form or already-correct long-form BER: just prepend the SMPTE prefix.
      normalized = new byte[ST_0601_SMPTE_LABEL_PREFIX.length + packet.length];
      System.arraycopy(
          ST_0601_SMPTE_LABEL_PREFIX, 0, normalized, 0, ST_0601_SMPTE_LABEL_PREFIX.length);
      System.arraycopy(packet, 0, normalized, ST_0601_SMPTE_LABEL_PREFIX.length, packet.length);
    }
    // The MISB ST 0601 checksum (tag 1, value = 16-bit sum of all prior bytes) was computed
    // over the original stripped-prefix bytes. After adding the SMPTE prefix (and fixing the
    // BER length), those bytes are different, so we recompute the checksum in-place.
    updateMisbChecksum(normalized);
    return normalized;
  }

  // Overwrites the last 2 bytes of a MISB ST 0601 unit when the unit ends with the
  // standard checksum TLV (tag=0x01, length=0x02). BCC-16 is the low 16 bits of the
  // sum of all 16-bit big-endian words from unit[0] through unit[unit.length-3] inclusive
  // (the checksum length byte). If the covered region is an odd number of bytes the last
  // byte is zero-extended into the high byte of a 16-bit word per MISB spec.
  private static void updateMisbChecksum(byte[] unit) {
    if (unit.length < 4) {
      return;
    }
    if ((unit[unit.length - 4] & 0xFF) != 0x01 || (unit[unit.length - 3] & 0xFF) != 0x02) {
      return;
    }
    int coveredLen = unit.length - 2; // covers unit[0..unit.length-3] inclusive
    int sum = 0;
    for (int i = 0; i < coveredLen - 1; i += 2) {
      sum += ((unit[i] & 0xFF) << 8) | (unit[i + 1] & 0xFF);
    }
    if ((coveredLen & 1) != 0) {
      sum += (unit[coveredLen - 1] & 0xFF) << 8;
    }
    unit[unit.length - 2] = (byte) (sum >> 8);
    unit[unit.length - 1] = (byte) sum;
  }

  private static boolean looksLikeBareLocalSet(byte[] packet) {
    if (packet.length < 10) {
      return false;
    }
    int firstTag = Byte.toUnsignedInt(packet[0]);
    int firstLength = Byte.toUnsignedInt(packet[1]);
    return firstTag >= 1 && firstTag <= 127 && firstLength > 0 && 2 + firstLength <= packet.length;
  }

  private static boolean couldBeKeyFragment(byte[] packet) {
    if (packet.length == 0 || packet.length >= ST_0601_UNIVERSAL_KEY.length) {
      return false;
    }
    for (int i = 0; i < packet.length; i++) {
      if (packet[i] != ST_0601_UNIVERSAL_KEY[i]) {
        return false;
      }
    }
    return true;
  }

  private static byte[] wrapAsKlvUnit(byte[] localSet) {
    byte[] berLength = encodeBerLength(localSet.length);
    byte[] unit = new byte[ST_0601_UNIVERSAL_KEY.length + berLength.length + localSet.length];
    int pos = 0;
    System.arraycopy(ST_0601_UNIVERSAL_KEY, 0, unit, pos, ST_0601_UNIVERSAL_KEY.length);
    pos += ST_0601_UNIVERSAL_KEY.length;
    System.arraycopy(berLength, 0, unit, pos, berLength.length);
    pos += berLength.length;
    System.arraycopy(localSet, 0, unit, pos, localSet.length);
    return unit;
  }

  private static byte[] encodeBerLength(int n) {
    if (n < 0x80) return new byte[] {(byte) n};
    if (n <= 0xFF) return new byte[] {(byte) 0x81, (byte) n};
    if (n <= 0xFFFF) return new byte[] {(byte) 0x82, (byte) (n >> 8), (byte) n};
    if (n <= 0xFFFFFF) return new byte[] {(byte) 0x83, (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    return new byte[] {(byte) 0x84, (byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n};
  }

  private void appendToBuffer(byte[] packet) {
    var combined = Arrays.copyOf(buffer, buffer.length + packet.length);
    System.arraycopy(packet, 0, combined, buffer.length, packet.length);
    buffer = combined;
  }

  private void enforceMaxBuffer() {
    if (buffer.length <= maxBufferedBytes) {
      return;
    }

    var searchStart = Math.max(0, buffer.length - maxBufferedBytes);
    var keyIndex = findKey(buffer, searchStart);
    if (keyIndex >= 0) {
      buffer = Arrays.copyOfRange(buffer, keyIndex, buffer.length);
      return;
    }

    keepPotentialKeySuffix();
  }

  private ParseResult parseCompleteLength() {
    if (buffer.length < ST_0601_UNIVERSAL_KEY.length + 1) {
      return ParseResult.NEED_MORE_BYTES;
    }

    int firstLengthByte = Byte.toUnsignedInt(buffer[ST_0601_UNIVERSAL_KEY.length]);
    if ((firstLengthByte & 0x80) == 0) {
      return ParseResult.complete(ST_0601_UNIVERSAL_KEY.length + 1 + firstLengthByte);
    }

    int lengthByteCount = firstLengthByte & 0x7f;
    if (lengthByteCount == 0 || lengthByteCount > 4) {
      return ParseResult.MALFORMED;
    }
    if (buffer.length < ST_0601_UNIVERSAL_KEY.length + 1 + lengthByteCount) {
      return ParseResult.NEED_MORE_BYTES;
    }

    long payloadLength = 0;
    for (int i = 0; i < lengthByteCount; i++) {
      payloadLength =
          (payloadLength << 8) | Byte.toUnsignedInt(buffer[ST_0601_UNIVERSAL_KEY.length + 1 + i]);
    }

    long totalLength = ST_0601_UNIVERSAL_KEY.length + 1L + lengthByteCount + payloadLength;
    if (totalLength > Integer.MAX_VALUE) {
      return ParseResult.MALFORMED;
    }
    return ParseResult.complete((int) totalLength);
  }

  private void keepPotentialKeySuffix() {
    int suffixLength = longestKeyPrefixSuffix(buffer);
    if (suffixLength == 0) {
      buffer = new byte[0];
      return;
    }
    buffer = Arrays.copyOfRange(buffer, buffer.length - suffixLength, buffer.length);
  }

  private static int findKey(byte[] bytes, int start) {
    int maxStart = bytes.length - ST_0601_UNIVERSAL_KEY.length;
    for (int i = Math.max(0, start); i <= maxStart; i++) {
      if (startsWith(bytes, i, ST_0601_UNIVERSAL_KEY)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
    if (offset + prefix.length > bytes.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (bytes[offset + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static int longestKeyPrefixSuffix(byte[] bytes) {
    int max = Math.min(bytes.length, ST_0601_UNIVERSAL_KEY.length - 1);
    for (int length = max; length > 0; length--) {
      var start = bytes.length - length;
      var matches = true;
      for (int i = 0; i < length; i++) {
        if (bytes[start + i] != ST_0601_UNIVERSAL_KEY[i]) {
          matches = false;
          break;
        }
      }
      if (matches) {
        return length;
      }
    }
    return 0;
  }

  private void dropFirstByte() {
    buffer = Arrays.copyOfRange(buffer, 1, buffer.length);
  }

  private record ParseResult(Integer totalLength) {
    private static final ParseResult NEED_MORE_BYTES = new ParseResult(null);
    private static final ParseResult MALFORMED = new ParseResult(-1);

    private static ParseResult complete(int totalLength) {
      return new ParseResult(totalLength);
    }
  }
}

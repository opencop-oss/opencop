// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class KlvPacketFramerTest {

  // ---------------------------------------------------------------------------
  // appendPacket — form 1: full universal key (existing behaviour, now via packet API)
  // ---------------------------------------------------------------------------

  @Test
  void appendPacket_emitsFullKeyUnitUnchanged() {
    var unit = klv(3, 0x11);
    var framer = new KlvPacketFramer();

    assertThat(framer.appendPacket(unit)).containsExactly(unit);
  }

  // ---------------------------------------------------------------------------
  // appendPacket — form 2: SMPTE OID prefix stripped
  // ---------------------------------------------------------------------------

  @Test
  void appendPacket_emitsFullKeyUnitFromStrippedPrefixPacket() {
    // Sender omits bytes [0..4] of the universal key and sends only the 11-byte MISB suffix.
    var payload = repeat(4, 0xAA);
    var strippedPacket =
        concat(KlvPacketFramer.ST_0601_PARTIAL_KEY, new byte[] {(byte) payload.length}, payload);
    var framer = new KlvPacketFramer();

    var messages = framer.appendPacket(strippedPacket);

    assertThat(messages).containsExactly(klv(payload.length, 0xAA));
  }

  @Test
  void appendPacket_emitsMultipleUnitsFromConsecutiveStrippedPrefixPackets() {
    var first = concat(KlvPacketFramer.ST_0601_PARTIAL_KEY, new byte[] {0x02}, repeat(2, 0xBB));
    var second = concat(KlvPacketFramer.ST_0601_PARTIAL_KEY, new byte[] {0x03}, repeat(3, 0xCC));
    var framer = new KlvPacketFramer();

    assertThat(framer.appendPacket(first)).containsExactly(klv(2, 0xBB));
    assertThat(framer.appendPacket(second)).containsExactly(klv(3, 0xCC));
  }

  @Test
  void appendPacket_handlesStreamMixingFullKeyAndStrippedPrefixPackets() {
    var fullKeyUnit = klv(2, 0x11);
    var strippedPacket =
        concat(KlvPacketFramer.ST_0601_PARTIAL_KEY, new byte[] {0x03}, repeat(3, 0x22));
    var framer = new KlvPacketFramer();

    assertThat(framer.appendPacket(fullKeyUnit)).containsExactly(fullKeyUnit);
    assertThat(framer.appendPacket(strippedPacket)).containsExactly(klv(3, 0x22));
  }

  // ---------------------------------------------------------------------------
  // appendPacket — form 2 edge cases
  // ---------------------------------------------------------------------------

  @Test
  void appendPacket_exactlyPartialKeyBytes_doesNotThrow() {
    // An 11-byte packet that equals ST_0601_PARTIAL_KEY with no BER length byte.
    // Must not throw ArrayIndexOutOfBoundsException when reading the (absent) BER byte.
    var framer = new KlvPacketFramer();

    var result = framer.appendPacket(KlvPacketFramer.ST_0601_PARTIAL_KEY.clone());

    assertThat(result).isEmpty();
    assertThat(framer.bufferedBytes()).isZero();
  }

  @Test
  void appendPacket_strippedPrefixFragment_partialKeyAloneThenPayload_safelyDiscarded() {
    // Fragmented stripped-prefix stream where the 11-byte partial key arrives in packet 1
    // and the BER length + payload arrive in packet 2. Neither packet is a recognisable
    // KLV unit; both are discarded without throwing.
    var berAndPayload = concat(new byte[] {0x04}, repeat(4, 0xAA));
    var framer = new KlvPacketFramer();

    assertThat(framer.appendPacket(KlvPacketFramer.ST_0601_PARTIAL_KEY.clone())).isEmpty();
    assertThat(framer.appendPacket(berAndPayload)).isEmpty();
    assertThat(framer.bufferedBytes()).isZero();
  }

  // ---------------------------------------------------------------------------
  // appendPacket — form 2 checksum: BCC-16 recomputed after SMPTE prefix prepend
  // ---------------------------------------------------------------------------

  @Test
  void appendPacket_strippedPrefixWithChecksumTlv_evenNormalizedLength_recomputesBcc16() {
    // Payload (11 bytes): tag=2 len=5 value=[AA BB CC DD EE] + checksum TLV zeroed.
    // Normalized unit = 5 (SMPTE prefix) + 11 (partial key) + 1 (BER 0x0B) + 11 (payload) = 28
    // bytes.
    // 28-byte covered region is even: all 16-bit pairs complete without zero-padding.
    // Expected BCC-16 computed independently over 13 pairs = low 16 bits of 0x1F1B8 = 0xF1B8.
    var payload =
        new byte[] {
          0x02,
          0x05,
          (byte) 0xAA,
          (byte) 0xBB,
          (byte) 0xCC,
          (byte) 0xDD,
          (byte) 0xEE,
          0x01,
          0x02,
          0x00,
          0x00
        };
    var stripped =
        concat(KlvPacketFramer.ST_0601_PARTIAL_KEY, new byte[] {(byte) payload.length}, payload);
    var framer = new KlvPacketFramer();

    var result = framer.appendPacket(stripped);

    assertThat(result).hasSize(1);
    var unit = result.get(0);
    assertThat(unit[unit.length - 2]).isEqualTo((byte) 0xF1);
    assertThat(unit[unit.length - 1]).isEqualTo((byte) 0xB8);
  }

  @Test
  void appendPacket_strippedPrefixWithChecksumTlv_oddNormalizedLength_recomputesBcc16() {
    // Payload (10 bytes): tag=2 len=4 value=[12 34 56 78] + checksum TLV zeroed.
    // Normalized unit = 5 + 11 + 1 (BER 0x0A) + 10 = 27 bytes.
    // 27-byte covered region is odd (25 bytes): last covered byte (checksum length 0x02) is
    // zero-extended as the high byte of the final 16-bit word per MISB BCC-16 spec.
    // Expected BCC-16 = low 16 bits of (0x100BB + 0x200) = 0x02BB.
    var payload = new byte[] {0x02, 0x04, 0x12, 0x34, 0x56, 0x78, 0x01, 0x02, 0x00, 0x00};
    var stripped =
        concat(KlvPacketFramer.ST_0601_PARTIAL_KEY, new byte[] {(byte) payload.length}, payload);
    var framer = new KlvPacketFramer();

    var result = framer.appendPacket(stripped);

    assertThat(result).hasSize(1);
    var unit = result.get(0);
    assertThat(unit[unit.length - 2]).isEqualTo((byte) 0x02);
    assertThat(unit[unit.length - 1]).isEqualTo((byte) 0xBB);
  }

  // ---------------------------------------------------------------------------
  // appendPacket — form 3: bare local-set payload (no key)
  // ---------------------------------------------------------------------------

  @Test
  void appendPacket_wrapsLocalSetPayloadWithSyntheticKey() {
    // Minimum plausible local set: tag=2 (PrecisionTimeStamp), length=8, value=8 bytes.
    // Total 10 bytes — exactly the minimum that passes the bare-local-set heuristic.
    var localSet = concat(new byte[] {0x02, 0x08}, repeat(8, 0xDD));
    var framer = new KlvPacketFramer();

    var messages = framer.appendPacket(localSet);

    // Emitted unit: [16-byte universal key] [0x0a short BER length] [localSet bytes]
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0))
        .startsWith(KlvPacketFramer.ST_0601_UNIVERSAL_KEY)
        .endsWith(localSet);
    assertThat(messages.get(0)[KlvPacketFramer.ST_0601_UNIVERSAL_KEY.length])
        .isEqualTo((byte) localSet.length);
  }

  @Test
  void appendPacket_wrapsLargeLocalSetWithMultiByteBerLength() {
    // 200-byte local set: tag=2, length=8, value=8 bytes, then 190 filler bytes.
    // Synthetic wrapper uses minimal 2-byte BER: 0x81 0xC8 for length 200.
    var localSet = concat(new byte[] {0x02, 0x08}, repeat(198, 0xEE));
    var framer = new KlvPacketFramer();

    var messages = framer.appendPacket(localSet);

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0))
        .startsWith(KlvPacketFramer.ST_0601_UNIVERSAL_KEY)
        .endsWith(localSet);
    // Verify 2-byte minimal BER: 0x81 followed by 200 = 0xC8
    int keyLen = KlvPacketFramer.ST_0601_UNIVERSAL_KEY.length;
    assertThat(messages.get(0)[keyLen]).isEqualTo((byte) 0x81);
    assertThat(messages.get(0)[keyLen + 1]).isEqualTo((byte) 200);
  }

  @Test
  void appendPacket_doesNotWrapShortOrJunkPacketsAsLocalSet() {
    // Packets shorter than 10 bytes or lacking a valid first TLV are not bare local sets
    // and must not be wrapped — they pass through unchanged (stream framer discards them).
    var framer = new KlvPacketFramer();

    assertThat(framer.appendPacket(new byte[] {0x01, 0x02, 0x03})).isEmpty();
    assertThat(framer.appendPacket(new byte[] {0x01})).isEmpty();
    assertThat(framer.bufferedBytes()).isZero();
  }

  @Test
  void appendPacket_handlesStreamMixingAllThreeForms() {
    var fullKey = klv(1, 0x11);
    var stripped = concat(KlvPacketFramer.ST_0601_PARTIAL_KEY, new byte[] {0x02}, repeat(2, 0x22));
    // Form 3: minimum plausible local set — tag=2 (PrecisionTimeStamp), length=8, value=8 bytes
    var bare = concat(new byte[] {0x02, 0x08}, repeat(8, 0x33));
    var framer = new KlvPacketFramer();

    assertThat(framer.appendPacket(fullKey)).containsExactly(fullKey);
    assertThat(framer.appendPacket(stripped)).containsExactly(klv(2, 0x22));
    var bareResult = framer.appendPacket(bare);
    assertThat(bareResult).hasSize(1);
    assertThat(bareResult.get(0)).startsWith(KlvPacketFramer.ST_0601_UNIVERSAL_KEY).endsWith(bare);
  }

  // ---------------------------------------------------------------------------
  // appendPacket — fragmentation: continuation packets bypass normalisation
  // ---------------------------------------------------------------------------

  @Test
  void appendPacket_assemblesFragmentedFullKeyUnitAcrossPackets() {
    var unit = klv(20, 0x55);
    var framer = new KlvPacketFramer();

    assertThat(framer.appendPacket(slice(unit, 0, 10))).isEmpty();
    assertThat(framer.appendPacket(slice(unit, 10, unit.length))).containsExactly(unit);
  }

  // ---------------------------------------------------------------------------
  // append (stream mode) — existing behaviour preserved unchanged
  // ---------------------------------------------------------------------------

  @Test
  void emitsOneCompleteKlvUnitFromSinglePacket() {
    var unit = klv(3, 0x11);
    var framer = new KlvPacketFramer();

    var messages = framer.append(unit);

    assertThat(messages).containsExactly(unit);
    assertThat(framer.bufferedBytes()).isZero();
  }

  @Test
  void emitsMultipleCompleteKlvUnitsFromSinglePacket() {
    var first = klv(1, 0x11);
    var second = klv(130, 0x22);
    var framer = new KlvPacketFramer();

    var messages = framer.append(concat(first, second));

    assertThat(messages).containsExactly(first, second);
  }

  @Test
  void buffersPartialTrailingUnitAcrossPackets() {
    var unit = klv(5, 0x33);
    var framer = new KlvPacketFramer();

    assertThat(framer.append(slice(unit, 0, 10))).isEmpty();
    assertThat(framer.append(slice(unit, 10, unit.length))).containsExactly(unit);
  }

  @Test
  void dropsLeadingJunkBeforeUniversalKey() {
    var unit = klv(2, 0x44);
    var framer = new KlvPacketFramer();

    var messages = framer.append(concat(new byte[] {0x55, 0x66, 0x77}, unit));

    assertThat(messages).containsExactly(unit);
  }

  @Test
  void keepsPartialUniversalKeySuffixAcrossJunkPackets() {
    var unit = klv(1, 0x55);
    var key = KlvPacketFramer.ST_0601_UNIVERSAL_KEY;
    var framer = new KlvPacketFramer();

    assertThat(framer.append(concat(new byte[] {0x01, 0x02}, slice(key, 0, 8)))).isEmpty();
    assertThat(framer.append(slice(unit, 8, unit.length))).containsExactly(unit);
  }

  @Test
  void enforcesMaxBufferOnMalformedStreams() {
    var framer = new KlvPacketFramer(32);

    assertThat(framer.append(new byte[64])).isEmpty();

    assertThat(framer.bufferedBytes()).isLessThanOrEqualTo(15);
  }

  @Test
  void dropsMalformedIndefiniteLengthAndResynchronizes() {
    var valid = klv(2, 0x66);
    var malformed = concat(KlvPacketFramer.ST_0601_UNIVERSAL_KEY, new byte[] {(byte) 0x80, 0x55});
    var framer = new KlvPacketFramer();

    var messages = framer.append(concat(malformed, valid));

    assertThat(messages).containsExactly(valid);
  }

  @Test
  void dropsUnsupportedBerLengthByteCountAndResynchronizes() {
    var valid = klv(3, 0x77);
    var malformed =
        concat(KlvPacketFramer.ST_0601_UNIVERSAL_KEY, new byte[] {(byte) 0x85, 0, 0, 0, 0, 1});
    var framer = new KlvPacketFramer();

    var messages = framer.append(concat(malformed, valid));

    assertThat(messages).containsExactly(valid);
  }

  private static byte[] klv(int payloadLength, int fill) {
    var out = new ByteArrayOutputStream();
    out.writeBytes(KlvPacketFramer.ST_0601_UNIVERSAL_KEY);
    // Minimal BER encoding — matches encodeBerLength() in the production framer.
    if (payloadLength < 0x80) {
      out.write(payloadLength);
    } else if (payloadLength <= 0xFF) {
      out.write(0x81);
      out.write(payloadLength);
    } else {
      out.write(0x82);
      out.write((payloadLength >>> 8) & 0xff);
      out.write(payloadLength & 0xff);
    }
    out.writeBytes(repeat(payloadLength, fill));
    return out.toByteArray();
  }

  private static byte[] repeat(int length, int value) {
    var bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte) value;
    }
    return bytes;
  }

  private static byte[] concat(byte[]... parts) {
    var out = new ByteArrayOutputStream();
    for (var part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }

  private static byte[] slice(byte[] bytes, int from, int to) {
    var length = to - from;
    var result = new byte[length];
    System.arraycopy(bytes, from, result, 0, length);
    return result;
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper.zip;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class GzipUtilTest {

  @Test
  void is_gzip_returns_true_for_valid_gzip_magic_bytes() {
    assertTrue(GzipUtil.isGzip(new byte[] {(byte) 0x1F, (byte) 0x8B, 0x08}));
  }

  @Test
  void is_gzip_returns_false_for_null_short_or_non_gzip_payloads() {
    assertFalse(GzipUtil.isGzip(null));
    assertFalse(GzipUtil.isGzip(new byte[] {(byte) 0x1F}));
    assertFalse(GzipUtil.isGzip("plain".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void decompress_returns_original_bytes() throws IOException {
    byte[] original = "compressed text".getBytes(StandardCharsets.UTF_8);

    byte[] decompressed = GzipUtil.decompress(gzip(original), 1024);

    assertArrayEquals(original, decompressed);
  }

  @Test
  void decompress_throws_when_uncompressed_payload_exceeds_max_bytes() throws IOException {
    byte[] original = "0123456789".getBytes(StandardCharsets.UTF_8);
    byte[] compressed = gzip(original);

    IOException exception =
        assertThrows(IOException.class, () -> GzipUtil.decompress(compressed, 5));

    assertEquals("Gzip exceeded maximum allowed size", exception.getMessage());
  }

  @Test
  void decompress_throws_for_invalid_gzip_stream() {
    byte[] invalid = new byte[] {(byte) 0x1F, (byte) 0x8B, 0x00, 0x00};

    assertThrows(IOException.class, () -> GzipUtil.decompress(invalid, 1024));
  }

  private static byte[] gzip(byte[] payload) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOut = new GZIPOutputStream(out)) {
      gzipOut.write(payload);
    }
    return out.toByteArray();
  }
}

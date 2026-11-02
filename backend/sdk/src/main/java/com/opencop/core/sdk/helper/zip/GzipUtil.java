// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper.zip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/** Utility for decompressing gzip-encoded payloads. */
public final class GzipUtil {

  private static final byte GZIP_MAGIC_1 = (byte) 0x1F;
  private static final byte GZIP_MAGIC_2 = (byte) 0x8B;

  private GzipUtil() {}

  public static boolean isGzip(byte[] payload) {
    return payload != null
        && payload.length >= 2
        && payload[0] == GZIP_MAGIC_1
        && payload[1] == GZIP_MAGIC_2;
  }

  /** Decompresses a gzip-encoded payload. */
  public static byte[] decompress(byte[] payload, int maxBytes) throws IOException {
    try (var gis = new GZIPInputStream(new ByteArrayInputStream(payload));
        var out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int total = 0;
      int n;
      while ((n = gis.read(buf)) != -1) {
        total += n;
        if (total > maxBytes) {
          throw new IOException("Gzip exceeded maximum allowed size");
        }
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    }
  }
}

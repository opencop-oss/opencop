// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper.zip;

import com.opencop.core.sdk.technical.gateway.extractor.ZipSiphon;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class KmzUtil {
  private static final byte ZIP_MAGIC_1 = 0x50; // 'P'
  private static final byte ZIP_MAGIC_2 = 0x4B; // 'K'

  private KmzUtil() {}

  public static boolean isZip(byte[] payload) {
    return payload != null
        && payload.length >= 2
        && payload[0] == ZIP_MAGIC_1
        && payload[1] == ZIP_MAGIC_2;
  }

  public static List<byte[]> unwrapToKmlIfNeeded(byte[] payload) throws MapperEngineException {
    if (!isZip(payload)) {
      return List.of(payload);
    }

    try {
      ZipSiphon.Result result =
          ZipSiphon.siphon(
              payload,
              path -> path.toLowerCase(Locale.ROOT).endsWith(".kml"),
              Integer.MAX_VALUE,
              Long.MAX_VALUE);

      List<byte[]> kmlFiles =
          result.extractedByPath().values().stream().map(ZipSiphon.ZipAsset::bytes).toList();

      if (kmlFiles.isEmpty()) {
        throw new MapperEngineException("KMZ_ERROR", "KMZ contains no .kml file", null);
      }

      return kmlFiles;
    } catch (IOException e) {
      throw new MapperEngineException("KMZ_ERROR", "Failed to unzip KMZ payload", e);
    }
  }
}

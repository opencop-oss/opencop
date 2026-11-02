// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Chains ZipSiphon → KmzAssetResolver into a single reusable pipeline. Used by tests to exercise
 * the full KMZ icon resolution flow without duplicating orchestration.
 */
public final class KmzPipeline {

  private static final int MAX_EXTRACTED_FILES = 2_000;
  private static final long DEFAULT_MAX_TOTAL_EXTRACTED_BYTES = 50L * 1_024 * 1_024; // 50MB

  public record Result(ZipSiphon.Result siphoned, List<KmzIconRef> manifest) {}

  /**
   * Siphon images from a KMZ, assign media IDs, and resolve KML icon hrefs.
   *
   * @param kmzBytes raw KMZ bytes
   * @param mediaIdSupplier called for each extracted image path to assign a media ID
   * @param maxTotalExtractedBytes maximum total bytes to extract from the ZIP
   * @return the siphoned result and manifest
   */
  public static Result process(
      byte[] kmzBytes, Function<String, UUID> mediaIdSupplier, long maxTotalExtractedBytes)
      throws IOException {

    ZipSiphon.Result siphoned =
        ZipSiphon.siphon(
            kmzBytes, ZipSiphon.imagesOnly(), MAX_EXTRACTED_FILES, maxTotalExtractedBytes);

    Map<String, KmzAssetResolver.MediaInfo> pathToMediaInfo = new HashMap<>();
    for (String path : siphoned.extractedByPath().keySet()) {
      pathToMediaInfo.put(
          path, new KmzAssetResolver.MediaInfo(mediaIdSupplier.apply(path), null, null));
    }

    List<KmzIconRef> manifest = KmzAssetResolver.resolve(siphoned.strippedZip(), pathToMediaInfo);

    return new Result(siphoned, manifest);
  }

  /**
   * Siphon images from a KMZ using the default 50 MB extraction limit.
   *
   * @param kmzBytes raw KMZ bytes
   * @param mediaIdSupplier called for each extracted image path to assign a media ID
   * @return the siphoned result and manifest
   */
  public static Result process(byte[] kmzBytes, Function<String, UUID> mediaIdSupplier)
      throws IOException {
    return process(kmzBytes, mediaIdSupplier, DEFAULT_MAX_TOTAL_EXTRACTED_BYTES);
  }

  /** Convenience: assign random UUIDs to all extracted images. */
  public static Result process(byte[] kmzBytes) throws IOException {
    return process(kmzBytes, path -> UUID.randomUUID());
  }

  private KmzPipeline() {}
}

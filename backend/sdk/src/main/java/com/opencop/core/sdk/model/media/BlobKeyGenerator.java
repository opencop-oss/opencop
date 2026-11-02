// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Generates blob storage keys with a configurable prefix.
 *
 * <p>Key format: {@code {prefix}/{YYYY}/{MM}/{DD}/{id}/{slug}}. The date prefix enables
 * prefix-based listing queries (S3-style storage only supports key-prefix filtering) and aids human
 * readability in storage explorer. The UUID provides uniqueness. The slugified filename is a
 * debugging aid.
 *
 * @see MediaFileSlug
 */
public final class BlobKeyGenerator {

  private BlobKeyGenerator() {}

  /**
   * Generates a blob key using today's date.
   *
   * @param prefix the storage prefix (e.g. MEDIA, IMPORT)
   * @param id the record UUID (used as directory name in the key)
   * @param originalFilename the original uploaded filename (slugified for the key)
   * @return the full blob storage key
   */
  public static String generate(BlobKeyPrefix prefix, UUID id, String originalFilename) {
    return generate(prefix, id, originalFilename, LocalDate.now());
  }

  /**
   * Generates a blob key using an explicit date (overload exposed for testing).
   *
   * @param prefix the storage prefix (e.g. MEDIA, IMPORT)
   * @param id the record UUID
   * @param originalFilename the original uploaded filename
   * @param date the date to use in the key prefix
   * @return the full blob storage key
   */
  public static String generate(
      BlobKeyPrefix prefix, UUID id, String originalFilename, LocalDate date) {
    var slug = resolveSlug(originalFilename);
    return "%s/%d/%02d/%02d/%s/%s"
        .formatted(
            prefix.getValue(),
            date.getYear(),
            date.getMonthValue(),
            date.getDayOfMonth(),
            id,
            slug);
  }

  static String resolveSlug(String originalFilename) {
    var slug = MediaFileSlug.slugify(originalFilename);
    if (!slug.isEmpty()) return slug;
    var ext = MediaFileSlug.extractExtension(originalFilename);
    return ext != null ? "unknown." + ext : "unknown";
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import java.util.regex.Pattern;

/**
 * Slugifies an original filename for use in blob storage keys.
 *
 * <p>The slug is a debugging aid (human-readable in Azure Storage Explorer), not a contract.
 * Uniqueness is guaranteed by the mediaId UUID in the blob key, not the filename slug.
 *
 * @see com.opencop.core.sdk.technical.gateway.broker.kafka.TopicSlug
 */
public final class MediaFileSlug {

  private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9-]");
  private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
  private static final Pattern VALID_EXTENSION = Pattern.compile("[a-z0-9]{1,10}");
  private static final int MAX_STEM_LENGTH = 100;

  private MediaFileSlug() {}

  /**
   * Converts an original filename to a slug safe for blob storage keys.
   *
   * <p>Rules: lowercase, only {@code [a-z0-9-]} in the stem, collapse runs of {@code -}, trim
   * leading/trailing {@code -}, truncate stem to 100 chars. Extension split on last {@code .} of
   * the original input and reattached only if it matches {@code [a-z0-9]{1,10}}.
   *
   * <p>Returns empty string for null, blank, or names whose stem sanitizes to empty. The caller
   * (e.g. {@link MediaBlobKey}) handles the fallback.
   */
  public static String slugify(String filename) {
    if (filename == null || filename.isBlank()) {
      return "";
    }

    String name = filename.strip();

    // Split stem and extension on last dot of the original input.
    // Extension must match [a-z0-9]{1,10}; if not, treat entire input as stem.
    String stem;
    String extension = null;
    int lastDot = name.lastIndexOf('.');
    if (lastDot >= 0 && lastDot < name.length() - 1) {
      String candidate = name.substring(lastDot + 1).toLowerCase();
      if (VALID_EXTENSION.matcher(candidate).matches()) {
        stem = name.substring(0, lastDot);
        extension = candidate;
      } else {
        stem = name;
      }
    } else {
      stem = name;
    }

    // Slugify the stem: lowercase, only [a-z0-9-], collapse dashes, trim
    stem = stem.toLowerCase();
    stem = NON_SLUG.matcher(stem).replaceAll("-");
    stem = MULTI_DASH.matcher(stem).replaceAll("-");
    stem = stem.replaceAll("^-+|-+$", "");

    if (stem.length() > MAX_STEM_LENGTH) {
      stem = stem.substring(0, MAX_STEM_LENGTH);
      stem = stem.replaceAll("-+$", "");
    }

    if (stem.isEmpty()) {
      return "";
    }

    return extension != null ? stem + "." + extension : stem;
  }

  /**
   * Extracts a validated extension from the original filename, or {@code null} if none.
   *
   * <p>Package-private — used by {@link MediaBlobKey} to build the upload fallback slug.
   */
  static String extractExtension(String filename) {
    if (filename == null || filename.isBlank()) return null;
    String name = filename.strip();
    int lastDot = name.lastIndexOf('.');
    if (lastDot < 0 || lastDot >= name.length() - 1) return null;
    String candidate = name.substring(lastDot + 1).toLowerCase();
    return VALID_EXTENSION.matcher(candidate).matches() ? candidate : null;
  }
}

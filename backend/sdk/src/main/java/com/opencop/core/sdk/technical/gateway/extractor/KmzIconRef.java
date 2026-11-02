// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import java.util.List;
import java.util.UUID;

public record KmzIconRef(
    String kmlPath,
    String rawHref,
    String resolvedPath,
    UUID mediaId,
    Status status,
    Integer widthPx,
    Integer heightPx) {

  public enum Status {
    RESOLVED,
    EXTERNAL,
    UNRESOLVED
  }

  /**
   * Look up a manifest entry by KML path and raw href.
   *
   * @return the matching ref, or null if not found
   */
  public static KmzIconRef lookup(List<KmzIconRef> manifest, String kmlPath, String rawHref) {
    for (KmzIconRef ref : manifest) {
      if (ref.kmlPath().equals(kmlPath) && ref.rawHref().equals(rawHref)) {
        return ref;
      }
    }
    return null;
  }

  /**
   * Look up a manifest entry by raw href only (ignoring kmlPath). Used by FeatureBuilder in the
   * Processor where the kmlPath is not available. For single-KML KMZs (the common case) this is
   * unambiguous. For multi-KML KMZs, returns the first match.
   *
   * @return the matching ref, or null if not found
   */
  public static KmzIconRef lookupByHref(List<KmzIconRef> manifest, String rawHref) {
    for (KmzIconRef ref : manifest) {
      if (ref.rawHref().equals(rawHref)) {
        return ref;
      }
    }
    return null;
  }

  /**
   * Resolves an iconHref to the symbol value that FeatureBuilder should use. Returns
   * "media_{mediaId}" for resolved refs, the raw href for external URLs, or null for unresolved
   * refs.
   */
  public String toSymbolValue() {
    return switch (status) {
      case RESOLVED -> "opencop:media:" + mediaId;
      case EXTERNAL -> rawHref; // http URLs pass through as-is
      case UNRESOLVED -> null;
    };
  }
}

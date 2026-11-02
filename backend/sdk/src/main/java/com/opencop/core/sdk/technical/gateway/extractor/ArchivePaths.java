// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import java.nio.file.Path;

public final class ArchivePaths {

  /**
   * Canonicalizes an archive entry path or a relative href. Returns null if the path is blank,
   * unsafe (escapes root via ..), or otherwise invalid.
   */
  public static String normalize(String raw) {
    if (raw == null) return null;

    String s = raw.replace('\\', '/').trim();
    while (s.startsWith("/")) s = s.substring(1);
    if (s.isBlank()) return null;

    String result = Path.of(s).normalize().toString().replace('\\', '/');
    if (result.isEmpty() || result.startsWith("..")) return null;

    return result;
  }

  /**
   * Resolves a relative href against the directory of a base entry path. For example, base
   * "sub/doc.kml" and href "../icons/marker.png" resolves to "icons/marker.png".
   */
  public static String resolve(String basePath, String href) {
    if (href == null) return null;

    String trimmed = href.trim();
    if (trimmed.isEmpty()) return null;

    // Absolute URIs / external refs are not archive-relative
    if (trimmed.startsWith("http://")
        || trimmed.startsWith("https://")
        || trimmed.startsWith("data:")) {
      return null;
    }

    // Find the directory of the base path
    String baseDir = "";
    if (basePath != null) {
      int lastSlash = basePath.lastIndexOf('/');
      if (lastSlash >= 0) {
        baseDir = basePath.substring(0, lastSlash + 1);
      }
    }

    return normalize(baseDir + trimmed);
  }

  private ArchivePaths() {}
}

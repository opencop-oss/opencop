// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ArchivePathsTest {

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
      # Clean paths — pass through
      icons/marker.png,          icons/marker.png
      files/UPPERCASE-MARKER.GIF, files/UPPERCASE-MARKER.GIF
      doc.kml,                   doc.kml

      # Leading slash stripped
      /icons/marker.png,         icons/marker.png

      # Dot-slash stripped
      ./icons/marker.png,        icons/marker.png

      # Double slashes collapsed
      icons//marker.png,         icons/marker.png

      # Backslashes converted
      icons\\marker.png,         icons/marker.png

      # Parent traversal (valid — within archive)
      sub/../icons/marker.png,   icons/marker.png
      a/b/../../icons/marker.png, icons/marker.png

      # Double dots in filenames (NOT traversal)
      icon..png,                 icon..png
      v1..final/marker.png,      v1..final/marker.png
      """)
  void normalize_handles_common_cases(String input, String expected) {
    assertEquals(expected, ArchivePaths.normalize(input));
  }

  @Test
  void normalize_rejects_root_escape() {
    assertNull(ArchivePaths.normalize("../escape.png"));
    assertNull(ArchivePaths.normalize("../../etc/passwd"));
    assertNull(ArchivePaths.normalize("a/../../escape.png"));
  }

  @Test
  void normalize_rejects_blank_and_null() {
    assertNull(ArchivePaths.normalize(null));
    assertNull(ArchivePaths.normalize(""));
    assertNull(ArchivePaths.normalize("   "));
    assertNull(ArchivePaths.normalize("/"));
  }

  @Test
  void resolve_relative_to_root_kml() {
    assertEquals("icons/marker.png", ArchivePaths.resolve("doc.kml", "icons/marker.png"));
    assertEquals("icons/marker.png", ArchivePaths.resolve("doc.kml", "./icons/marker.png"));
  }

  @Test
  void resolve_relative_to_nested_kml() {
    // KML at sub/doc.kml, href ../icons/marker.png → icons/marker.png
    assertEquals("icons/marker.png", ArchivePaths.resolve("sub/doc.kml", "../icons/marker.png"));
    // KML at sub/doc.kml, href icons/marker.png → sub/icons/marker.png
    assertEquals("sub/icons/marker.png", ArchivePaths.resolve("sub/doc.kml", "icons/marker.png"));
  }

  @Test
  void resolve_returns_null_for_external_urls() {
    assertNull(ArchivePaths.resolve("doc.kml", "https://example.com/icon.png"));
    assertNull(ArchivePaths.resolve("doc.kml", "http://example.com/icon.png"));
    assertNull(ArchivePaths.resolve("doc.kml", "data:image/png;base64,abc"));
  }

  @Test
  void resolve_returns_null_for_root_escape() {
    assertNull(ArchivePaths.resolve("doc.kml", "../../etc/passwd"));
  }
}

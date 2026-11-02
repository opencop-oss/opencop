// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

class MediaFileSlugTest {

  @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
  @MethodSource("slugifyCases")
  void slugify(String input, String expected) {
    assertEquals(expected, MediaFileSlug.slugify(input));
  }

  @ParameterizedTest(name = "null → \"\"")
  @NullSource
  void slugify_null(String input) {
    assertEquals("", MediaFileSlug.slugify(input));
  }

  static Stream<Arguments> slugifyCases() {
    return Stream.of(
        // Normal filenames
        Arguments.of("icon.png", "icon.png"),
        Arguments.of("my-image.jpg", "my-image.jpg"),
        Arguments.of("photo_2026.jpeg", "photo-2026.jpeg"),

        // Uppercase → lowercase
        Arguments.of("ICON.PNG", "icon.png"),
        Arguments.of("MyPhoto.JPEG", "myphoto.jpeg"),

        // Spaces and special chars → dash
        Arguments.of("my file (1).png", "my-file-1.png"),
        Arguments.of("hello@world!.gif", "hello-world.gif"),

        // Path separators stripped — dots in stem become dashes, no traversal possible
        Arguments.of("../../etc/passwd", "etc-passwd"),
        Arguments.of("sub/dir/icon.png", "sub-dir-icon.png"),
        Arguments.of("a\\b\\c.png", "a-b-c.png"),

        // Runs of dashes collapsed
        Arguments.of("a---b.png", "a-b.png"),
        Arguments.of("a   b.png", "a-b.png"),

        // Leading/trailing dashes on stem trimmed
        Arguments.of("---test---.png", "test.png"),
        Arguments.of("  test  .jpg", "test.jpg"),

        // Multiple dots — split on last dot, earlier dots in stem become dashes
        Arguments.of("file.backup.tar.gz", "file-backup-tar.gz"),
        Arguments.of("my.report.v2.pdf", "my-report-v2.pdf"),
        Arguments.of("foo.bar.png", "foo-bar.png"),

        // No extension
        Arguments.of("readme", "readme"),
        Arguments.of("MAKEFILE", "makefile"),

        // Dot at start — stem is empty → empty result
        Arguments.of(".gitignore", ""),
        Arguments.of(".png", ""),
        Arguments.of(".", ""),

        // Trailing dot — no valid extension, dot in stem becomes dash, trimmed
        Arguments.of("foo.", "foo"),

        // Extension too long (>10 chars) → not a valid extension, entire input is stem
        Arguments.of("file.longextension", "file-longextension"),
        Arguments.of("data.reallyreallylongext", "data-reallyreallylongext"),

        // Extension with valid split on last dot
        Arguments.of("file.tar.gz", "file-tar.gz"),

        // Non-ASCII in stem
        Arguments.of("données.pdf", "donn-es.pdf"),
        Arguments.of("日本語.png", ""),
        Arguments.of("café-icon.svg", "caf-icon.svg"),

        // Edge cases → empty (caller handles fallback)
        Arguments.of("", ""),
        Arguments.of("   ", ""),
        Arguments.of("!!!", ""),
        Arguments.of("---", ""),
        Arguments.of("!!.png", ""),

        // Truncation at 100 chars (stem only)
        Arguments.of("a".repeat(120) + ".png", "a".repeat(100) + ".png"),

        // Truncation doesn't end on dash
        Arguments.of("a".repeat(99) + "-bcd.png", "a".repeat(99) + ".png"));
  }
}

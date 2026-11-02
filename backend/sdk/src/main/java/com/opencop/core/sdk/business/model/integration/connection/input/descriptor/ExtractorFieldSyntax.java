// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Shared syntax rules for declared field values, so every field kind rejects the same malformed
 * value with the same sentence.
 */
final class ExtractorFieldSyntax {

  private static final Pattern EXTENSION = Pattern.compile("\\.[a-z0-9]+");
  private static final Pattern MEDIA_TYPE = Pattern.compile("[^\\s/]+/[^\\s/]+");

  private ExtractorFieldSyntax() {}

  /**
   * Rejects a standard key on a field that differs from the standard declaration, so a client
   * rendering the field from its key never shows some other slot or format.
   */
  static void requireMatchingStandard(
      Optional<StandardExtractorField> standard,
      String name,
      String label,
      ExtractorFieldKind kind,
      Set<String> extensions,
      Set<String> mediaTypes) {
    Objects.requireNonNull(standard, "standard");
    standard.ifPresent(
        key -> {
          if (!key.isDeclaredAs(name, label, kind, extensions, mediaTypes)) {
            throw new IllegalArgumentException(
                "Field '"
                    + name
                    + "' ("
                    + kind
                    + ", label '"
                    + label
                    + "') is not the standard field "
                    + key.describe()
                    + "; declare it through StandardExtractorField."
                    + key
                    + ".declare");
          }
        });
  }

  static String requireText(String value, String argument) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(argument + " must not be blank");
    }
    return value;
  }

  /**
   * Copies accepted extensions, which a file-bearing field must declare at least one of because a
   * client has nothing to put in its file picker otherwise.
   */
  static Set<String> copyExtensions(Set<String> values, String fieldName) {
    var copied = copyFormats(values, "extensions", EXTENSION, "lowercase and dot-prefixed");
    if (copied.isEmpty()) {
      throw new IllegalArgumentException(
          "Field '" + fieldName + "' must declare at least one extension");
    }
    return copied;
  }

  static Set<String> copyMediaTypes(Set<String> values) {
    return copyFormats(values, "mediaTypes", MEDIA_TYPE, "of the form type/subtype");
  }

  /**
   * Returns an ascending copy, so a serialized descriptor renders the same accept list on every
   * run. The immutable sets from {@link Set#of} randomize iteration order per JVM run, which would
   * reorder the rendered list across restarts and defeat response caching.
   */
  private static Set<String> copyFormats(
      Set<String> values, String argument, Pattern syntax, String description) {
    Objects.requireNonNull(values, argument);
    for (var value : values) {
      if (value == null || !syntax.matcher(value).matches()) {
        throw new IllegalArgumentException(
            "Declared " + argument + " entry '" + value + "' must be " + description);
      }
    }
    return Collections.unmodifiableSet(new TreeSet<>(values));
  }
}

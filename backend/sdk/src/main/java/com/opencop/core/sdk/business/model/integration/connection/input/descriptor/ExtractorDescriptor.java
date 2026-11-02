// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Declarative description of one way an integration accepts connection input, so a client can
 * render a setup form for an integration it has no hardcoded knowledge of.
 *
 * <p>One descriptor describes one form. Acceptance paths that differ in <em>which fields exist</em>
 * are separate extractors; a single field declares several formats only when they are alternative
 * encodings of the same slot. A descriptor declares at least one field: an extractor recognizes,
 * validates and extracts submitted input, so one that asks for nothing has no form to render and
 * nothing to discriminate on.
 *
 * <p>Everything declared here, formats included, is a <strong>client hint only</strong>. The
 * extractor's {@code validate} remains the sole authority on whether supplied input is acceptable.
 *
 * @param id stable extractor identifier a client may send back to select this extractor; opaque, so
 *     a readable name may change without breaking a stored selection
 * @param label display text for the form
 * @param fields ordered input slots, with names unique within the descriptor
 */
public record ExtractorDescriptor(UUID id, String label, List<ExtractorField> fields) {

  public ExtractorDescriptor {
    Objects.requireNonNull(id, "id");
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
    Objects.requireNonNull(fields, "fields");
    fields = List.copyOf(fields);
    if (fields.isEmpty()) {
      throw new IllegalArgumentException(
          "Extractor descriptor '" + id + "' must declare at least one field");
    }
    var names = new HashSet<String>();
    for (var field : fields) {
      if (!names.add(field.name())) {
        throw new IllegalArgumentException(
            "Duplicate field name '" + field.name() + "' in extractor descriptor: " + id);
      }
    }
  }
}

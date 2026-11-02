// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A named group of settings fields owned by one concern of an integration. */
public record SettingsSection(String name, String label, List<SettingsField> fields) {

  public SettingsSection {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Settings section name must not be blank");
    }
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("Settings section label must not be blank: " + name);
    }
    fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    var names = new HashSet<String>();
    for (var field : fields) {
      if (!names.add(field.name())) {
        throw new IllegalArgumentException("Duplicate settings field " + name + "." + field.name());
      }
    }
  }

  public Optional<SettingsField> field(String fieldName) {
    return fields.stream().filter(field -> field.name().equals(fieldName)).findFirst();
  }
}

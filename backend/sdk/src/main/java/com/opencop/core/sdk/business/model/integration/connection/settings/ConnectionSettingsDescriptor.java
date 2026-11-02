// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a connection's settings may contain for one connection schema version: its sections,
 * their fields, each field's constraint and deployment default. Section and field order is the
 * order clients render and documents are normalised to.
 */
public record ConnectionSettingsDescriptor(List<SettingsSection> sections) {

  private static final ConnectionSettingsDescriptor EMPTY =
      new ConnectionSettingsDescriptor(List.of());

  public ConnectionSettingsDescriptor {
    sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    var names = new HashSet<String>();
    for (var section : sections) {
      if (!names.add(section.name())) {
        throw new IllegalArgumentException("Duplicate settings section " + section.name());
      }
    }
  }

  /** Returns the descriptor of a schema that declares no connection settings. */
  public static ConnectionSettingsDescriptor empty() {
    return EMPTY;
  }

  public Optional<SettingsSection> section(String sectionName) {
    return sections.stream().filter(section -> section.name().equals(sectionName)).findFirst();
  }

  /** Returns a document holding every field's deployment default. */
  public SettingsDocument defaults() {
    var values = new LinkedHashMap<String, Map<String, Object>>();
    for (var section : sections) {
      var fields = new LinkedHashMap<String, Object>();
      section.fields().forEach(field -> fields.put(field.name(), field.defaultValue()));
      values.put(section.name(), fields);
    }
    return new SettingsDocument(values);
  }
}

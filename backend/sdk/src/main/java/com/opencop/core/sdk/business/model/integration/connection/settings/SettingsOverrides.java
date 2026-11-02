// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A connection's integration settings overrides as stored: keyed by section then field, with plain
 * JSON values, in the order they were stored.
 *
 * <p>Unlike a {@link SettingsDocument}, nothing here is checked against the integration's
 * declaration. A stored value can stop resolving, after the integration changed for instance, so
 * every value is kept as stored, nulls included, for resolving to report. Overrides always override
 * something: a connection that overrides nothing stores none at all.
 */
public final class SettingsOverrides {

  private final Map<String, Map<String, Object>> sections;

  /**
   * @throws IllegalArgumentException when there is no section
   */
  public SettingsOverrides(Map<String, ? extends Map<String, ?>> sections) {
    Objects.requireNonNull(sections, "sections");
    if (sections.isEmpty()) {
      throw new IllegalArgumentException("Stored settings overrides must override something");
    }
    var copied = new LinkedHashMap<String, Map<String, Object>>();
    sections.forEach(
        (section, fields) ->
            copied.put(
                section,
                Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(fields, section)))));
    this.sections = Collections.unmodifiableMap(copied);
  }

  /** The overrides to store for resolved ones, or none when they override nothing. */
  public static Optional<SettingsOverrides> of(SettingsDocument resolved) {
    return resolved.isEmpty()
        ? Optional.empty()
        : Optional.of(new SettingsOverrides(resolved.plainValues()));
  }

  /** Returns the sections and their field values as stored, in order. */
  public Map<String, Map<String, Object>> sections() {
    return sections;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SettingsOverrides overrides && sections.equals(overrides.sections);
  }

  @Override
  public int hashCode() {
    return sections.hashCode();
  }

  @Override
  public String toString() {
    return "SettingsOverrides" + sections;
  }
}

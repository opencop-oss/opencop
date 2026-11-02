// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * One optional field of a connection settings section: its name, display label, constraint and
 * deployment default. Omitting the field from a connection's settings inherits the default.
 *
 * <p>The host enforces the declared constraint on every override. Whether the default itself
 * satisfies it is checked when the integration registers, so a field never inherits a value a user
 * could not set.
 */
public sealed interface SettingsField {

  /** Returns the field name, unique within its section. */
  String name();

  /** Returns the human-readable label a client shows for the field. */
  String label();

  SettingsFieldKind kind();

  /** Returns the deployment default in the field's value type. */
  Object defaultValue();

  /** Returns the location of a field in a settings document, as violations report it. */
  static String path(String section, String field) {
    return section + "." + field;
  }

  /**
   * Non-blank text with at most {@code maxLength} characters once surrounding whitespace is
   * stripped, which the default is as well.
   */
  record TextField(String name, String label, int maxLength, String defaultValue)
      implements SettingsField {

    public TextField {
      requireIdentity(name, label);
      if (maxLength <= 0) {
        throw new IllegalArgumentException("maxLength must be positive for field " + name);
      }
      defaultValue = Objects.requireNonNull(defaultValue, "defaultValue").strip();
    }

    @Override
    public SettingsFieldKind kind() {
      return SettingsFieldKind.TEXT;
    }
  }

  /** A finite number between {@code min} and {@code max} inclusive, whole when {@code integer}. */
  record NumberField(
      String name, String label, double min, double max, boolean integer, Double defaultValue)
      implements SettingsField {

    public NumberField {
      requireIdentity(name, label);
      if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
        throw new IllegalArgumentException("Invalid bounds for field " + name);
      }
      Objects.requireNonNull(defaultValue, "defaultValue");
    }

    @Override
    public SettingsFieldKind kind() {
      return SettingsFieldKind.NUMBER;
    }
  }

  record BooleanField(String name, String label, Boolean defaultValue) implements SettingsField {

    public BooleanField {
      requireIdentity(name, label);
      Objects.requireNonNull(defaultValue, "defaultValue");
    }

    @Override
    public SettingsFieldKind kind() {
      return SettingsFieldKind.BOOLEAN;
    }
  }

  /** A duration between {@code min} and {@code max} inclusive. */
  record DurationField(String name, String label, Duration min, Duration max, Duration defaultValue)
      implements SettingsField {

    public DurationField {
      requireIdentity(name, label);
      Objects.requireNonNull(min, "min");
      Objects.requireNonNull(max, "max");
      if (min.compareTo(max) > 0) {
        throw new IllegalArgumentException("Invalid bounds for field " + name);
      }
      Objects.requireNonNull(defaultValue, "defaultValue");
    }

    @Override
    public SettingsFieldKind kind() {
      return SettingsFieldKind.DURATION;
    }
  }

  /** One of {@code options}, compared exactly. */
  record ChoiceField(String name, String label, List<String> options, String defaultValue)
      implements SettingsField {

    public ChoiceField {
      requireIdentity(name, label);
      options = List.copyOf(Objects.requireNonNull(options, "options"));
      if (options.isEmpty()) {
        throw new IllegalArgumentException("options must not be empty for field " + name);
      }
      Objects.requireNonNull(defaultValue, "defaultValue");
    }

    @Override
    public SettingsFieldKind kind() {
      return SettingsFieldKind.CHOICE;
    }
  }

  private static void requireIdentity(String name, String label) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Settings field name must not be blank");
    }
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("Settings field label must not be blank: " + name);
    }
  }
}

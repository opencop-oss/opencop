// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed connection settings values keyed by section then field, in insertion order.
 *
 * <p>Each value is a {@link String} (text and choice), {@link Double} (number), {@link Boolean} or
 * {@link Duration}, already checked against its field's declaration. A document handed to an
 * integration is complete: every declared field is present.
 */
public final class SettingsDocument {

  private static final SettingsDocument EMPTY = new SettingsDocument(Map.of());

  private final Map<String, Map<String, Object>> sections;

  public SettingsDocument(Map<String, ? extends Map<String, ?>> sections) {
    Objects.requireNonNull(sections, "sections");
    var copied = new LinkedHashMap<String, Map<String, Object>>();
    sections.forEach(
        (section, fields) -> {
          var copiedFields = new LinkedHashMap<String, Object>();
          fields.forEach(
              (field, value) -> copiedFields.put(field, requireSupported(section, field, value)));
          copied.put(section, Collections.unmodifiableMap(copiedFields));
        });
    this.sections = Collections.unmodifiableMap(copied);
  }

  public static SettingsDocument empty() {
    return EMPTY;
  }

  /** Returns the sections and their field values, in order. */
  public Map<String, Map<String, Object>> sections() {
    return sections;
  }

  /**
   * Returns the document in its wire form, the inverse of reading a settings JSON object: text,
   * booleans and numbers as they are, a whole number without a fraction, and a duration as its
   * ISO-8601 text.
   */
  public Map<String, Map<String, Object>> plainValues() {
    var plain = new LinkedHashMap<String, Map<String, Object>>();
    sections.forEach(
        (section, fields) -> {
          var plainFields = new LinkedHashMap<String, Object>();
          fields.forEach((field, value) -> plainFields.put(field, plain(value)));
          plain.put(section, Collections.unmodifiableMap(plainFields));
        });
    return Collections.unmodifiableMap(plain);
  }

  public boolean isEmpty() {
    return sections.isEmpty();
  }

  public Optional<Object> value(String section, String field) {
    return Optional.ofNullable(sections.getOrDefault(section, Map.of()).get(field));
  }

  public String text(String section, String field) {
    return required(section, field, String.class);
  }

  public double number(String section, String field) {
    return required(section, field, Double.class);
  }

  public boolean flag(String section, String field) {
    return required(section, field, Boolean.class);
  }

  public Duration duration(String section, String field) {
    return required(section, field, Duration.class);
  }

  private <T> T required(String section, String field, Class<T> type) {
    var value =
        value(section, field)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Missing settings field " + SettingsField.path(section, field)));
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException(
          "Settings field "
              + SettingsField.path(section, field)
              + " is not a "
              + type.getSimpleName());
    }
    return type.cast(value);
  }

  /**
   * Returns a settings number as written in JSON and messages: a {@code long} when it is whole and
   * fits one, so integer values read as integers, otherwise the double itself.
   */
  public static Number wireNumber(double value) {
    return value == Math.rint(value) && Math.abs(value) < Long.MAX_VALUE
        ? (Number) (long) value
        : value;
  }

  private static Object plain(Object value) {
    return switch (value) {
      case Duration duration -> duration.toString();
      case Double number -> wireNumber(number);
      default -> value;
    };
  }

  private static Object requireSupported(String section, String field, Object value) {
    if (value instanceof String
        || value instanceof Double
        || value instanceof Boolean
        || value instanceof Duration) {
      return value;
    }
    throw new IllegalArgumentException(
        "Unsupported value for settings field " + SettingsField.path(section, field));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SettingsDocument document && sections.equals(document.sections);
  }

  @Override
  public int hashCode() {
    return sections.hashCode();
  }

  @Override
  public String toString() {
    return "SettingsDocument" + sections;
  }
}

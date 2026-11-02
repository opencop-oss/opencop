// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.connection.settings;

import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.BooleanField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.ChoiceField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.DurationField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.NumberField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.TextField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsFieldReading;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolation;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolationCode;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides whether one value is acceptable for a declared settings field: whether its wire form
 * matches the field's kind, and whether it satisfies the field's constraint.
 */
public final class SettingsFieldPolicy {

  /** The two noncharacters at the end of the Basic Multilingual Plane. */
  private static final int BMP_NONCHARACTER_FFFE = 0xFFFE;

  private static final int BMP_NONCHARACTER_FFFF = 0xFFFF;

  private SettingsFieldPolicy() {}

  /**
   * Reads a value in its wire form, as a JSON parser yields it ({@link String}, {@link Number} or
   * {@link Boolean}), into the field's value type and checks the field's constraint. Text is
   * stripped of surrounding whitespace.
   */
  public static SettingsFieldReading read(SettingsField field, String path, Object raw) {
    Objects.requireNonNull(field, "field");
    if (raw == null) {
      return SettingsFieldReading.rejected(
          violation(path, SettingsViolationCode.NULL_VALUE, "must be omitted rather than null"));
    }
    return typed(field, path, raw)
        .map(
            value ->
                check(field, path, value)
                    .map(SettingsFieldReading::rejected)
                    .orElse(SettingsFieldReading.accepted(value)))
        .orElseGet(() -> SettingsFieldReading.rejected(invalidType(field, path, raw)));
  }

  /**
   * Checks an already typed value against the field's constraint, returning the violation when it
   * breaks it. Used for overrides once read, and for deployment defaults as they register.
   */
  public static Optional<SettingsViolation> check(SettingsField field, String path, Object value) {
    return switch (field) {
      case TextField text -> checkText(text, path, (String) value);
      case NumberField number -> checkNumber(number, path, (Double) value);
      case BooleanField _ -> Optional.empty();
      case DurationField duration -> checkDuration(duration, path, (Duration) value);
      case ChoiceField choice -> checkChoice(choice, path, (String) value);
    };
  }

  private static Optional<Object> typed(SettingsField field, String path, Object raw) {
    return switch (field) {
      case TextField _ -> raw instanceof String text ? Optional.of(text.strip()) : Optional.empty();
      case ChoiceField _ -> raw instanceof String text ? Optional.of(text) : Optional.empty();
      case NumberField _ ->
          isFiniteNumber(raw) ? Optional.of(((Number) raw).doubleValue()) : Optional.empty();
      case BooleanField _ -> raw instanceof Boolean flag ? Optional.of(flag) : Optional.empty();
      case DurationField _ -> raw instanceof String text ? parseDuration(text) : Optional.empty();
    };
  }

  /**
   * A duration field holding text that does not parse is reported as {@code INVALID_DURATION}, not
   * as a type mismatch, so a client can tell a wrong JSON type from a malformed ISO-8601 value.
   */
  private static SettingsViolation invalidType(SettingsField field, String path, Object raw) {
    if (field instanceof DurationField && raw instanceof String) {
      return violation(
          path,
          SettingsViolationCode.INVALID_DURATION,
          "must be an ISO-8601 duration such as PT30S");
    }
    return violation(path, SettingsViolationCode.INVALID_TYPE, "must be " + expectedType(field));
  }

  private static String expectedType(SettingsField field) {
    return switch (field) {
      case TextField _, ChoiceField _ -> "a string";
      case NumberField _ -> "a finite number";
      case BooleanField _ -> "a boolean";
      case DurationField _ -> "an ISO-8601 duration string";
    };
  }

  private static Optional<SettingsViolation> checkText(TextField field, String path, String value) {
    if (value.isBlank()) {
      return Optional.of(violation(path, SettingsViolationCode.BLANK_TEXT, "must not be blank"));
    }
    if (!value.codePoints().allMatch(SettingsFieldPolicy::isTextCharacter)) {
      return Optional.of(
          violation(
              path,
              SettingsViolationCode.INVALID_CHARACTER,
              "must not contain control characters, unpaired surrogates or noncharacters"));
    }
    if (value.length() > field.maxLength()) {
      return Optional.of(
          violation(
              path,
              SettingsViolationCode.TOO_LONG,
              "must be at most " + field.maxLength() + " characters"));
    }
    return Optional.empty();
  }

  private static Optional<SettingsViolation> checkNumber(
      NumberField field, String path, double value) {
    // An override is already finite once read; a deployment default reaches here unread, and NaN
    // would pass both bound comparisons.
    if (!Double.isFinite(value)) {
      return Optional.of(invalidType(field, path, value));
    }
    if (field.integer() && value != Math.rint(value)) {
      return Optional.of(
          violation(path, SettingsViolationCode.NOT_INTEGER, "must be a whole number"));
    }
    if (value < field.min()) {
      return Optional.of(
          violation(
              path,
              SettingsViolationCode.BELOW_MINIMUM,
              "must be at least " + render(field.min())));
    }
    if (value > field.max()) {
      return Optional.of(
          violation(
              path, SettingsViolationCode.ABOVE_MAXIMUM, "must be at most " + render(field.max())));
    }
    return Optional.empty();
  }

  private static Optional<SettingsViolation> checkDuration(
      DurationField field, String path, Duration value) {
    if (value.compareTo(field.min()) < 0) {
      return Optional.of(
          violation(path, SettingsViolationCode.BELOW_MINIMUM, "must be at least " + field.min()));
    }
    if (value.compareTo(field.max()) > 0) {
      return Optional.of(
          violation(path, SettingsViolationCode.ABOVE_MAXIMUM, "must be at most " + field.max()));
    }
    return Optional.empty();
  }

  private static Optional<SettingsViolation> checkChoice(
      ChoiceField field, String path, String value) {
    if (!field.options().contains(value)) {
      return Optional.of(
          violation(
              path,
              SettingsViolationCode.NOT_AN_OPTION,
              "must be one of " + String.join(", ", field.options())));
    }
    return Optional.empty();
  }

  /**
   * Settings text ends up in integration messages such as XML, which cannot carry control
   * characters, lone surrogates or the BMP noncharacters. A surrogate pair reads as one code point.
   */
  private static boolean isTextCharacter(int codePoint) {
    return !Character.isISOControl(codePoint)
        && Character.getType(codePoint) != Character.SURROGATE
        && codePoint != BMP_NONCHARACTER_FFFE
        && codePoint != BMP_NONCHARACTER_FFFF;
  }

  /** JSON booleans are not numbers, and a non-finite value has no JSON form to round-trip to. */
  private static boolean isFiniteNumber(Object raw) {
    return raw instanceof Number number && Double.isFinite(number.doubleValue());
  }

  private static Optional<Object> parseDuration(String text) {
    try {
      return Optional.of(Duration.parse(text));
    } catch (DateTimeParseException _) {
      return Optional.empty();
    }
  }

  private static String render(double value) {
    return SettingsDocument.wireNumber(value).toString();
  }

  private static SettingsViolation violation(
      String path, SettingsViolationCode code, String message) {
    return new SettingsViolation(path, code, message);
  }
}

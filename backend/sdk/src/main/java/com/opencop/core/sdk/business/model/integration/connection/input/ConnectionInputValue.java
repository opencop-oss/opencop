// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input;

import java.util.Objects;

/**
 * One scalar value supplied for an extractor descriptor field of a text or secret kind.
 *
 * <p>Deliberately a class rather than a record: a record's generated {@code toString} prints every
 * component, which would place a supplied secret into any message, log line or diagnostic that
 * renders the enclosing input. This type prints only the name and whether a value is present.
 */
public final class ConnectionInputValue {

  private final String name;
  private final String value;

  /**
   * @param name descriptor field name the value was supplied under, matching the declared spelling
   * @param value supplied value, which may be empty but never null
   */
  public ConnectionInputValue(String name, String value) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    this.name = name;
    this.value = Objects.requireNonNull(value, "value");
  }

  public String name() {
    return name;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ConnectionInputValue supplied
        && name.equals(supplied.name)
        && value.equals(supplied.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }

  @Override
  public String toString() {
    return "ConnectionInputValue[name=" + name + ", valuePresent=" + !value.isEmpty() + "]";
  }
}

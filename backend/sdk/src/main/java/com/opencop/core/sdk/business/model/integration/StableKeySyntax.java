// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration;

import java.util.regex.Pattern;

/**
 * Lowercase kebab-case syntax shared by every stable, client-visible integration key, such as
 * capability stable keys.
 */
public final class StableKeySyntax {

  /** Human-readable name of the syntax, for use in rejection messages. */
  public static final String DESCRIPTION = "lowercase kebab-case";

  private static final Pattern PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  private StableKeySyntax() {}

  /** Returns whether the value is a non-null lowercase kebab-case key. */
  public static boolean isValid(String value) {
    return value != null && PATTERN.matcher(value).matches();
  }
}

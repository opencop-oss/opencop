// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import com.opencop.core.sdk.ports.exception.ErrorDetail;

/**
 * One reason a connection's settings were rejected.
 *
 * @param path the rejected location, {@code section} or {@code section.field}; empty when the
 *     settings as a whole are rejected
 * @param code stable code a client branches on: a {@link SettingsViolationCode} for host-enforced
 *     rules, or an integration-owned code for its cross-field rules
 * @param message safe, human-readable explanation
 */
public record SettingsViolation(String path, String code, String message) {

  /** The path of a violation that rejects the settings as a whole. */
  public static final String WHOLE_DOCUMENT = "";

  public SettingsViolation {
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
  }

  public SettingsViolation(String path, SettingsViolationCode code, String message) {
    this(path, code.name(), message);
  }

  /**
   * Returns this violation as an API error detail, targeting its path; a violation of the settings
   * as a whole targets no location within them.
   */
  public ErrorDetail toErrorDetail() {
    return new ErrorDetail(code, message, path.equals(WHOLE_DOCUMENT) ? null : path, null);
  }

  /** Rejects settings supplied for a connection that takes none. */
  public static SettingsViolation notSupported() {
    return new SettingsViolation(
        WHOLE_DOCUMENT,
        SettingsViolationCode.SETTINGS_NOT_SUPPORTED,
        "This connection takes no integration settings");
  }
}

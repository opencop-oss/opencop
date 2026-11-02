// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.extension;

/**
 * Declarative display metadata for a connection extension.
 *
 * @param label short name a client shows for the extension, such as a tab title
 * @param description one sentence on what the extension offers
 */
public record ExtensionDescriptor(String label, String description) {

  public ExtensionDescriptor {
    requireNonBlank(label, "label");
    requireNonBlank(description, "description");
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}

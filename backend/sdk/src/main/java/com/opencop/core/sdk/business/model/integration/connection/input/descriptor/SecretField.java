// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import java.util.Optional;
import java.util.Set;

/**
 * A field carrying free text that must never be echoed back or logged.
 *
 * @param name payload key the value is supplied under
 * @param label display text for the field
 * @param required whether the field must be supplied
 * @param standard the shared field this declares, when it is one; a client localizes it by its key
 */
public record SecretField(
    String name, String label, boolean required, Optional<StandardExtractorField> standard)
    implements ExtractorField {

  public SecretField {
    name = ExtractorFieldSyntax.requireText(name, "name");
    label = ExtractorFieldSyntax.requireText(label, "label");
    ExtractorFieldSyntax.requireMatchingStandard(
        standard, name, label, ExtractorFieldKind.SECRET, Set.of(), Set.of());
  }

  /** Declares a field no other integration shares. */
  public SecretField(String name, String label, boolean required) {
    this(name, label, required, Optional.empty());
  }

  @Override
  public ExtractorFieldKind kind() {
    return ExtractorFieldKind.SECRET;
  }
}

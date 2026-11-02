// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.setup;

import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The non-secret part of a submitted connection setup, stored to fill a later setup form with: the
 * extractor it chose and the values it supplied for the fields that extractor declares as text.
 * Their order is not kept: a form lays its fields out from the extractor's own declaration.
 *
 * <p>A field is kept for its declared kind, never for its name, so a secret, a file or a file list
 * is left out whatever it is called.
 *
 * @param extractorId the extractor the setup chose
 * @param values the supplied text values keyed by field name
 */
public record SetupPrefill(UUID extractorId, Map<String, String> values) {

  public SetupPrefill {
    Objects.requireNonNull(extractorId, "extractorId");
    values = Map.copyOf(values);
  }

  /** What a setup supplied for its extractor's text fields. */
  public static SetupPrefill of(ExtractorDescriptor extractor, ConnectionInput input) {
    var values = new HashMap<String, String>();
    extractor.fields().stream()
        .filter(TextField.class::isInstance)
        .forEach(
            field -> input.value(field.name()).ifPresent(value -> values.put(field.name(), value)));
    return new SetupPrefill(extractor.id(), values);
  }
}

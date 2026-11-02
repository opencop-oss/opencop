// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import java.util.Map;
import java.util.UUID;

/**
 * How a {@link SetupPrefill} is stored in {@code profiles.data_source.setup_prefill}. Its component
 * names are the stored JSON keys, so the business record can change without breaking stored rows.
 *
 * @param extractorId the extractor the setup chose
 * @param values the supplied text values keyed by field name
 */
record SetupPrefillDocument(UUID extractorId, Map<String, String> values) {

  static SetupPrefillDocument of(SetupPrefill prefill) {
    return new SetupPrefillDocument(prefill.extractorId(), prefill.values());
  }

  SetupPrefill toPrefill() {
    return new SetupPrefill(extractorId, values == null ? Map.of() : values);
  }
}

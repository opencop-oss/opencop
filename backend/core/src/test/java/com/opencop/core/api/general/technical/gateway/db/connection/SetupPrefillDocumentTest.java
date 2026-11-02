// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Rows already store these keys, so a renamed component would no longer read them back. */
class SetupPrefillDocumentTest {

  private static final UUID EXTRACTOR_ID = UUID.fromString("3f1d2c4b-5a6e-4f70-8b9c-0d1e2f3a4b5c");
  private static final String STORED =
      """
      {"extractorId": "3f1d2c4b-5a6e-4f70-8b9c-0d1e2f3a4b5c", "values": {"host": "relay.example.test", "port": "8087"}}
      """;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void storesAPrefillUnderTheExtractorIdAndValuesKeys() {
    var prefill =
        new SetupPrefill(EXTRACTOR_ID, Map.of("host", "relay.example.test", "port", "8087"));

    var stored = objectMapper.writeValueAsString(SetupPrefillDocument.of(prefill));

    assertThat(objectMapper.readTree(stored)).isEqualTo(objectMapper.readTree(STORED));
  }

  @Test
  void readsAStoredPrefillBack() {
    var prefill = objectMapper.readValue(STORED, SetupPrefillDocument.class).toPrefill();

    assertThat(prefill)
        .isEqualTo(
            new SetupPrefill(EXTRACTOR_ID, Map.of("host", "relay.example.test", "port", "8087")));
  }

  @Test
  void readsAStoredPrefillWithoutValuesAsHavingNone() {
    var prefill =
        objectMapper
            .readValue("{\"extractorId\": \"" + EXTRACTOR_ID + "\"}", SetupPrefillDocument.class)
            .toPrefill();

    assertThat(prefill.values()).isEmpty();
  }
}

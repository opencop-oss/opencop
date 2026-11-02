// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.dataimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ImportEnvelopeTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void roundTrip_allFields() throws Exception {
    var envelope =
        new ImportEnvelope(
            "imports/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/data.csv",
            "data.csv",
            "text/csv",
            1_048_576L);

    var json = objectMapper.writeValueAsBytes(envelope);
    var deserialized = objectMapper.readValue(json, ImportEnvelope.class);

    assertEquals(envelope, deserialized);
  }

  @Test
  void roundTrip_nullableFieldsNull() throws Exception {
    var envelope =
        new ImportEnvelope(
            "imports/2026/03/12/a1b2c3d4-e5f6-7890-abcd-ef1234567890/unknown", null, null, null);

    var json = objectMapper.writeValueAsBytes(envelope);
    var deserialized = objectMapper.readValue(json, ImportEnvelope.class);

    assertEquals(envelope.blobKey(), deserialized.blobKey());
    assertNull(deserialized.filename());
    assertNull(deserialized.contentType());
    assertNull(deserialized.sizeBytes());
  }

  @Test
  void deserialize_ignoresUnknownFields() throws Exception {
    var json =
        """
        {"blobKey":"key","filename":"f.csv","contentType":"text/csv","sizeBytes":100,"extra":"ignored"}
        """;

    var deserialized = objectMapper.readValue(json, ImportEnvelope.class);

    assertEquals("key", deserialized.blobKey());
    assertEquals("f.csv", deserialized.filename());
  }
}

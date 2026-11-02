// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.profile.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MapperProfileFullMetaRequestTest {

  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440203");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void mapsRequestFieldsIntoAMapperNoIntegrationOwns() throws Exception {
    var request =
        objectMapper.readValue(
            """
            {
              "profile": {
                "id": "550e8400-e29b-41d4-a716-446655440203",
                "name": "User mapper",
                "description": "User-authored mapper",
                "editable": true,
                "version": 4,
                "archived": false
              },
              "filename": "sample.xml",
              "payload": "AQI="
            }
            """,
            MapperProfileFullMetaRequest.class);

    var meta = request.toBusinessModel();

    assertThat(meta.profile())
        .satisfies(
            profile -> {
              assertThat(profile.id()).isEqualTo(MAPPER_ID);
              assertThat(profile.name()).isEqualTo("User mapper");
              assertThat(profile.description()).isEqualTo("User-authored mapper");
              assertThat(profile.editable()).isTrue();
              assertThat(profile.integrationId()).isNull();
              assertThat(profile.version()).isEqualTo(4);
              assertThat(profile.archived()).isFalse();
            });
    assertThat(meta.filename()).isEqualTo("sample.xml");
    assertThat(meta.payload()).containsExactly(1, 2);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Pins the /v1 JSON a connection's integration reference travels as. */
class ConnectionPatchRequestJsonTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("714e8096-8832-4486-ad47-5422f86f918a");

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void anIntegrationReferenceIsItsIntegrationIdAndSchemaVersion() {
    var request =
        mapper.readValue(
            "{\"integration\":{\"integrationId\":\"" + INTEGRATION_ID + "\",\"schemaVersion\":1}}",
            ConnectionPatchRequest.class);

    assertThat(request.integration())
        .isEqualTo(new IntegrationConnectionSchemaReference(INTEGRATION_ID, 1));
    assertThat(mapper.writeValueAsString(request.integration()))
        .isEqualTo("{\"integrationId\":\"" + INTEGRATION_ID + "\",\"schemaVersion\":1}");
  }

  @Test
  void anIntegrationReferenceWithoutItsIntegrationIdIsRejected() {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    "{\"integration\":{\"id\":\"" + INTEGRATION_ID + "\",\"schemaVersion\":1}}",
                    ConnectionPatchRequest.class))
        .isInstanceOf(JacksonException.class);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorCreationContextTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441101");
  private static final IntegrationConnectionSchemaReference INTEGRATION =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("550e8400-e29b-41d4-a716-446655441103"), 7);
  private static final ConnectionSpec SPEC =
      new ConnectionSpec(
          new TcpConfig("127.0.0.1", 8087, 3000, 1000, true, null, new NoSecurity()));

  @Test
  void retainsTheExactOptionalIntegrationReference() {
    var withIntegration =
        new ConnectorCreationContext(SOURCE_ID, SPEC, INTEGRATION, Optional.empty());
    var withoutIntegration = new ConnectorCreationContext(SOURCE_ID, SPEC, null, Optional.empty());

    assertThat(withIntegration.integration()).isEqualTo(INTEGRATION);
    assertThat(withoutIntegration.integration()).isNull();
  }

  @Test
  void rejectsMissingRequiredValues() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ConnectorCreationContext(null, SPEC, null, Optional.empty()))
        .withMessage("sourceId");
    assertThatNullPointerException()
        .isThrownBy(() -> new ConnectorCreationContext(SOURCE_ID, null, null, Optional.empty()))
        .withMessage("spec");
  }

  @Test
  void retainsTheOverridesTheSourceStores() {
    var overrides = new SettingsOverrides(Map.of("presence", Map.of("callsign", "Alpha")));

    var context =
        new ConnectorCreationContext(SOURCE_ID, SPEC, INTEGRATION, Optional.of(overrides));

    assertThat(context.overrides()).contains(overrides);
  }

  @Test
  void rejectsMissingOverrides() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ConnectorCreationContext(SOURCE_ID, SPEC, INTEGRATION, null))
        .withMessage("overrides");
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.observability.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MicrometerDeleteHookTelemetryTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2");
  private static final UUID OTHER_INTEGRATION_ID =
      UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91");
  private static final String EXTENSION_ID = "sync";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MicrometerDeleteHookTelemetry telemetry =
      new MicrometerDeleteHookTelemetry(registry);

  /** Extension identifiers are unique only within an integration, so the integration is a tag. */
  @Test
  void twoIntegrationsExtensionsOfOneNameAreCountedApart() {
    telemetry.recordFailure(INTEGRATION_ID, EXTENSION_ID);
    telemetry.recordFailure(OTHER_INTEGRATION_ID, EXTENSION_ID);
    telemetry.recordFailure(OTHER_INTEGRATION_ID, EXTENSION_ID);

    assertThat(count(INTEGRATION_ID, MicrometerDeleteHookTelemetry.FAILED)).isEqualTo(1);
    assertThat(count(OTHER_INTEGRATION_ID, MicrometerDeleteHookTelemetry.FAILED)).isEqualTo(2);
  }

  @Test
  void eachOutcomeIsCountedUnderItsOwnTag() {
    telemetry.recordTimeout(INTEGRATION_ID, EXTENSION_ID);
    telemetry.recordNotRun(INTEGRATION_ID, EXTENSION_ID);
    telemetry.recordSecretEntryNotDeleted(INTEGRATION_ID, EXTENSION_ID);

    assertThat(count(INTEGRATION_ID, MicrometerDeleteHookTelemetry.TIMED_OUT)).isEqualTo(1);
    assertThat(count(INTEGRATION_ID, MicrometerDeleteHookTelemetry.NOT_RUN)).isEqualTo(1);
    assertThat(count(INTEGRATION_ID, MicrometerDeleteHookTelemetry.SECRET_ENTRY_NOT_DELETED))
        .isEqualTo(1);
  }

  private double count(UUID integrationId, String outcome) {
    return registry
        .get(MicrometerDeleteHookTelemetry.INCOMPLETE_HOOKS_TOTAL)
        .tag(MicrometerDeleteHookTelemetry.INTEGRATION_TAG, integrationId.toString())
        .tag(MicrometerDeleteHookTelemetry.EXTENSION_TAG, EXTENSION_ID)
        .tag(MicrometerDeleteHookTelemetry.OUTCOME_TAG, outcome)
        .counter()
        .count();
  }
}

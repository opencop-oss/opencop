// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.observability.connection.extension;

import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MicrometerDeleteHookTelemetry implements DeleteHookTelemetry {

  public static final String INCOMPLETE_HOOKS_TOTAL =
      "opencop_connection_extension_delete_hooks_incomplete_total";
  public static final String INTEGRATION_TAG = "integration";
  public static final String EXTENSION_TAG = "extension";
  public static final String OUTCOME_TAG = "outcome";
  public static final String FAILED = "failed";
  public static final String TIMED_OUT = "timed_out";
  public static final String NOT_RUN = "not_run";
  public static final String SECRET_ENTRY_NOT_DELETED = "secret_entry_not_deleted";

  private final MeterRegistry meterRegistry;

  public MicrometerDeleteHookTelemetry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void recordFailure(UUID integrationId, String extensionId) {
    increment(integrationId, extensionId, FAILED);
  }

  @Override
  public void recordTimeout(UUID integrationId, String extensionId) {
    increment(integrationId, extensionId, TIMED_OUT);
  }

  @Override
  public void recordNotRun(UUID integrationId, String extensionId) {
    increment(integrationId, extensionId, NOT_RUN);
  }

  @Override
  public void recordSecretEntryNotDeleted(UUID integrationId, String extensionId) {
    increment(integrationId, extensionId, SECRET_ENTRY_NOT_DELETED);
  }

  private void increment(UUID integrationId, String extensionId, String outcome) {
    meterRegistry
        .counter(
            INCOMPLETE_HOOKS_TOTAL,
            INTEGRATION_TAG,
            integrationId.toString(),
            EXTENSION_TAG,
            extensionId,
            OUTCOME_TAG,
            outcome)
        .increment();
  }
}

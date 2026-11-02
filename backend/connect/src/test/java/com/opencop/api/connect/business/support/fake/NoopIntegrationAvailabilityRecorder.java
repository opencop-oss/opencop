// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.support.fake;

import com.opencop.api.connect.ports.gateway.observability.integration.IntegrationAvailabilityRecorder;
import com.opencop.core.sdk.model.connection.add.TransportKind;

public final class NoopIntegrationAvailabilityRecorder implements IntegrationAvailabilityRecorder {

  public static final NoopIntegrationAvailabilityRecorder INSTANCE =
      new NoopIntegrationAvailabilityRecorder();

  private NoopIntegrationAvailabilityRecorder() {}

  @Override
  public void recordUnavailable(TransportKind transport) {}
}

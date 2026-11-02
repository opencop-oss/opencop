// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.model;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import java.util.Objects;
import java.util.Set;

/**
 * The connection contract a Native connection binds to: a connection set up from the host's own
 * input for an open payload format, rather than through a plugin's integration.
 */
public record NativeConnectionSchema(NativeIntegration integration)
    implements IntegrationConnectionSchema {

  private static final int SCHEMA_VERSION = 1;

  public static final IntegrationConnectionSchemaReference V1_REFERENCE =
      new IntegrationConnectionSchemaReference(NativeIntegration.NATIVE_ID, SCHEMA_VERSION);

  public NativeConnectionSchema {
    Objects.requireNonNull(integration, "integration");
  }

  @Override
  public int schemaVersion() {
    return SCHEMA_VERSION;
  }

  @Override
  public Set<TransportProtocol> protocols() {
    return Set.of(
        TransportProtocol.TCP,
        TransportProtocol.UDP,
        TransportProtocol.RTSP,
        TransportProtocol.KAFKA);
  }
}

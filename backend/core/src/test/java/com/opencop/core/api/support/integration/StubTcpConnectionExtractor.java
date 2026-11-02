// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.support.integration;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import java.util.UUID;

/**
 * Declaration-only extractor for tests that read what a connection schema version accepts.
 *
 * <p>The acceptance methods throw: a test that reaches one has left the read-only surface under
 * test, so it should fail rather than record a silent no-op.
 */
public record StubTcpConnectionExtractor(
    IntegrationConnectionSchema schema, ExtractorDescriptor descriptor)
    implements TcpConnectionExtractorCapability {

  @Override
  public UUID id() {
    return descriptor.id();
  }

  @Override
  public void validate(TcpConnectionInput input) {
    throw new UnsupportedOperationException("validate");
  }

  @Override
  public TcpConnectionMaterial extract(TcpConnectionInput input) {
    throw new UnsupportedOperationException("extract");
  }
}

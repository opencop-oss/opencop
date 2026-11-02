// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.tcp;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.policy.tcp.NativeTcpConnectionInputPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Native TCP extractor for a plain connection to the host and port the request declares. */
public final class NativePlainTcpExtractor implements TcpConnectionExtractorCapability {

  public static final UUID EXTRACTOR_ID = UUID.fromString("5e8b2c47-1f3a-4d69-8b0e-6a4c9d2f7e15");

  private final NativeConnectionSchema schema;

  public NativePlainTcpExtractor(NativeConnectionSchema schema) {
    this.schema = Objects.requireNonNull(schema, "schema");
  }

  @Override
  public IntegrationConnectionSchema schema() {
    return schema;
  }

  @Override
  public UUID id() {
    return EXTRACTOR_ID;
  }

  /**
   * Builds the declaration on call rather than holding it in a constant, so a malformed field
   * reaches the registry and is rejected with the integration and extractor named.
   */
  @Override
  public ExtractorDescriptor descriptor() {
    return new ExtractorDescriptor(
        EXTRACTOR_ID,
        "Plain TCP",
        List.of(
            StandardExtractorField.HOST.declare(true), StandardExtractorField.PORT.declare(true)));
  }

  @Override
  public void validate(TcpConnectionInput input) throws ConnectionInputException {
    extract(input);
  }

  @Override
  public TcpConnectionMaterial extract(TcpConnectionInput input) throws ConnectionInputException {
    var endpoint =
        NativeTcpConnectionInputPolicy.requireEndpoint(
            StandardExtractorField.HOST.valueIn(input).orElse(null),
            StandardExtractorField.PORT.valueIn(input).orElse(null));
    NativeTcpConnectionInputPolicy.requireNoFiles(input);
    return TcpConnectionMaterial.plain(endpoint);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.udp;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpEndpointMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.policy.udp.UdpListenPortPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import com.opencop.core.sdk.ports.integration.connection.input.UdpConnectionExtractorCapability;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Native UDP extractor for a listener bound to the host and port the request declares. */
public final class NativeUdpListenerExtractor implements UdpConnectionExtractorCapability {

  public static final UUID EXTRACTOR_ID = UUID.fromString("3f1d7a52-8c4e-4b0a-9e6f-2d5b7c8a1e93");

  public static final String MISSING_HOST = "MISSING_UDP_HOST";

  private static final String MISSING_HOST_MESSAGE = "A UDP listener requires a host";

  private final NativeConnectionSchema schema;

  public NativeUdpListenerExtractor(NativeConnectionSchema schema) {
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
        "UDP listener",
        List.of(
            StandardExtractorField.HOST.declare(true),
            new TextField(UdpListenPortPolicy.LISTEN_PORT_FIELD, "Listen port", true)));
  }

  @Override
  public void validate(ConnectionInput input) throws ConnectionInputException {
    host(input);
    UdpListenPortPolicy.requireBindable(listenPort(input));
  }

  @Override
  public UdpConnectionMaterial extract(ConnectionInput input) throws ConnectionInputException {
    var port = UdpListenPortPolicy.requireBindable(listenPort(input));
    return UdpConnectionMaterial.plain(new UdpEndpointMaterial(host(input), port));
  }

  private static String host(ConnectionInput input) throws ConnectionInputException {
    var host = StandardExtractorField.HOST.valueIn(input).filter(supplied -> !supplied.isBlank());
    if (host.isEmpty()) {
      throw ConnectionInputException.rejecting(
          StandardExtractorField.HOST.fieldName(),
          ConnectionInputFailure.INVALID_INPUT,
          MISSING_HOST,
          MISSING_HOST_MESSAGE);
    }
    return host.get();
  }

  private static String listenPort(ConnectionInput input) {
    return input.value(UdpListenPortPolicy.LISTEN_PORT_FIELD).orElse(null);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog.setup.udp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.api.support.integration.StubConnectionMappingContribution;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.UdpConnectionExtractorCapability;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UdpConnectionSetupDescriberTest {

  private static final MapperDeclaration RULE = MapperDeclaration.openMapper();
  private static final IntegrationConnectionSchema SCHEMA =
      new TestConnectionSchema(
          UUID.fromString("5c2e1c2f-4c4b-4018-8d2f-0b1e1d3c8e22"),
          1,
          "Udp feed",
          "Udp feed",
          Set.of(TransportProtocol.UDP));
  private static final ExtractorDescriptor EXTRACTOR =
      new ExtractorDescriptor(
          UUID.fromString("00000000-0000-0000-0000-0000000000e1"),
          "Udp input",
          List.of(StandardExtractorField.HOST.declare(true)));

  private final ConnectionMappingRegistry mappingRegistry =
      new ConnectionMappingRegistry(
          List.of(SCHEMA),
          List.of(
              new StubConnectionMappingContribution(SCHEMA, Map.of(TransportProtocol.UDP, RULE))));

  @Test
  void describesItsOwnKindWithItsExtractorsAndRule() {
    var describer =
        new UdpConnectionSetupDescriber(registryWith(List.of(extractor())), mappingRegistry);

    assertThat(describer.protocol()).isEqualTo(TransportProtocol.UDP);
    assertThat(describer.describe(SCHEMA.reference()))
        .contains(
            new ProtocolConnectionSetup.Udp(List.of(EXTRACTOR), RULE, List.of(AccessMode.READ)));
  }

  /** A transport reading its input natively has no form to render, so it is left out. */
  @Test
  void leavesOutATransportDeclaredForItsMappingRuleAlone() {
    var describer = new UdpConnectionSetupDescriber(registryWith(List.of()), mappingRegistry);

    assertThat(describer.describe(SCHEMA.reference())).isEmpty();
  }

  /** The mapping registry refuses to start without one, so reaching this is a server fault. */
  @Test
  void treatsAMissingRuleAsAServerFault() {
    var noRule = mock(ConnectionMappingRegistry.class);
    when(noRule.findMapperOnly(any(), any())).thenReturn(Optional.empty());
    var describer = new UdpConnectionSetupDescriber(registryWith(List.of(extractor())), noRule);

    assertThatThrownBy(() -> describer.describe(SCHEMA.reference()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without a UDP mapping rule");
  }

  private static ConnectionExtractorRegistry registryWith(
      List<ConnectionExtractorCapability<?, ?>> extractors) {
    return new ConnectionExtractorRegistry(List.of(SCHEMA), extractors);
  }

  private static ConnectionExtractorCapability<?, ?> extractor() {
    var extractor = mock(UdpConnectionExtractorCapability.class);
    when(extractor.schema()).thenReturn(SCHEMA);
    when(extractor.protocol()).thenReturn(TransportProtocol.UDP);
    when(extractor.id()).thenReturn(EXTRACTOR.id());
    when(extractor.descriptor()).thenReturn(EXTRACTOR);
    return extractor;
  }
}

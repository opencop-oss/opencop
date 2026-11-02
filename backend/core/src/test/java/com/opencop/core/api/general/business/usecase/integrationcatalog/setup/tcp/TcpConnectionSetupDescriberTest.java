// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog.setup.tcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.ConnectionFraming;
import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.api.support.integration.StubConnectionMappingContribution;
import com.opencop.core.api.support.integration.StubTcpConnectionExtractor;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.EgressMapperRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingPair;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TcpConnectionSetupDescriberTest {

  private static final UUID MAPPER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID EGRESS_MAPPER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID FRAMING = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID OPEN_FRAMING = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  private static final IntegrationConnectionSchema SCHEMA =
      new TestConnectionSchema(
          UUID.fromString("4b1d0b1e-3b3a-4f07-9c1e-9a0f0c2b7d11"),
          2,
          "Tcp",
          "Tcp",
          Set.of(TransportProtocol.TCP));
  private static final ExtractorDescriptor FIRST =
      new ExtractorDescriptor(
          UUID.fromString("00000000-0000-0000-0000-0000000000e1"),
          "First",
          List.of(StandardExtractorField.HOST.declare(true)));
  private static final ExtractorDescriptor SECOND =
      new ExtractorDescriptor(
          UUID.fromString("00000000-0000-0000-0000-0000000000e2"),
          "Second",
          List.of(StandardExtractorField.PORT.declare(true)));
  private static final MapperFramingDeclaration RULE =
      MapperFramingDeclaration.of(
              new MapperFramingPair(MAPPER, FRAMING), new FramingRule.AnyMapperWith(OPEN_FRAMING))
          .withEgress(EgressMapperRule.closedTo(Set.of(EGRESS_MAPPER)));

  private final ConnectionExtractorRegistry extractorRegistry =
      new ConnectionExtractorRegistry(
          List.of(SCHEMA),
          List.<TcpConnectionExtractorCapability>of(
              new StubTcpConnectionExtractor(SCHEMA, FIRST),
              new StubTcpConnectionExtractor(SCHEMA, SECOND)));
  private final ConnectionMappingRegistry mappingRegistry =
      new ConnectionMappingRegistry(
          List.of(SCHEMA),
          List.of(
              new StubConnectionMappingContribution(SCHEMA, Map.of(TransportProtocol.TCP, RULE))));

  @Test
  void describesEveryRegisteredExtractorAndTheRuleWithItsEgress() {
    var setup = describe(new TcpConnectionSetupDescriber(extractorRegistry, mappingRegistry));

    assertThat(setup.protocol()).isEqualTo(TransportProtocol.TCP);
    assertThat(setup.extractors()).containsExactly(FIRST, SECOND);
    assertThat(setup.rule()).isEqualTo(RULE);
  }

  @Test
  void resolvesEveryFramingTheRuleReferences() {
    var setup = describe(new TcpConnectionSetupDescriber(extractorRegistry, mappingRegistry));

    assertThat(setup.framings())
        .extracting(ConnectionFraming::id)
        .containsExactlyInAnyOrder(FRAMING, OPEN_FRAMING);
  }

  /** The extractor registry refuses to start without one, so reaching this is a server fault. */
  @Test
  void treatsAMissingTcpExtractorAsAServerFault() {
    var noExtractors = mock(ConnectionExtractorRegistry.class);
    when(noExtractors.extractorsFor(any(), any())).thenReturn(List.of());

    assertThatThrownBy(
            () ->
                new TcpConnectionSetupDescriber(noExtractors, mappingRegistry)
                    .describe(SCHEMA.reference()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without a TCP extractor");
  }

  /** The mapping registry refuses to start without one, so reaching this is a server fault. */
  @Test
  void treatsAMissingTcpRuleAsAServerFault() {
    var noRule = mock(ConnectionMappingRegistry.class);
    when(noRule.findTcp(any())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new TcpConnectionSetupDescriber(extractorRegistry, noRule)
                    .describe(SCHEMA.reference()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without a TCP mapping rule");
  }

  /** The mapping registry refuses to start with an undefined framing, so this is a server fault. */
  @Test
  void treatsAnUnresolvedFramingAsAServerFault() {
    var undefinedFraming = mock(ConnectionMappingRegistry.class);
    when(undefinedFraming.findTcp(any())).thenReturn(Optional.of(RULE));
    when(undefinedFraming.findFraming(any())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new TcpConnectionSetupDescriber(extractorRegistry, undefinedFraming)
                    .describe(SCHEMA.reference()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without framing");
  }

  private static ProtocolConnectionSetup.Tcp describe(TcpConnectionSetupDescriber describer) {
    var setup = describer.describe(SCHEMA.reference());
    assertThat(setup).isPresent();
    return (ProtocolConnectionSetup.Tcp) setup.orElseThrow();
  }
}

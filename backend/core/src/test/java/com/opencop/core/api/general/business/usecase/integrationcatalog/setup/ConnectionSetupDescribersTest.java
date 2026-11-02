// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConnectionSetupDescribersTest {

  private static final ExtractorDescriptor EXTRACTOR =
      new ExtractorDescriptor(
          UUID.fromString("00000000-0000-0000-0000-0000000000e1"),
          "Endpoint",
          List.of(StandardExtractorField.HOST.declare(true)));
  private static final ProtocolConnectionSetup TCP =
      new ProtocolConnectionSetup.Tcp(
          List.of(EXTRACTOR),
          MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming()),
          List.of(),
          List.of(AccessMode.READ));
  private static final ProtocolConnectionSetup KAFKA =
      new ProtocolConnectionSetup.Kafka(
          List.of(EXTRACTOR), MapperDeclaration.openMapper(), List.of(AccessMode.READ));

  private final Map<TransportProtocol, ConnectionSetupDescriber> describers =
      new EnumMap<>(TransportProtocol.class);

  @Test
  void describesTheDeclaredTransportsInTransportOrder() {
    var registry = registry(Map.of(TransportProtocol.TCP, TCP, TransportProtocol.KAFKA, KAFKA));

    var described =
        registry.describe(schema(Set.of(TransportProtocol.KAFKA, TransportProtocol.TCP)));

    assertThat(described).containsExactly(TCP, KAFKA);
  }

  @Test
  void leavesOutATransportWithNoFormToRender() {
    var registry = registry(Map.of(TransportProtocol.TCP, TCP));

    var described = registry.describe(schema(Set.of(TransportProtocol.TCP, TransportProtocol.UDP)));

    assertThat(described).containsExactly(TCP);
  }

  @Test
  void asksNoDescriberForATransportTheSchemaDoesNotDeclare() {
    var registry = registry(Map.of(TransportProtocol.TCP, TCP));

    registry.describe(schema(Set.of(TransportProtocol.TCP)));

    verify(describers.get(TransportProtocol.RTSP), never()).describe(any());
  }

  /**
   * {@link ConnectionSetupDescribers#describe} has no fallback for a transport left undescribed.
   */
  @Test
  void refusesToStartWithATransportNobodyDescribes() {
    var withoutRtsp =
        Stream.of(TransportProtocol.values())
            .filter(protocol -> protocol != TransportProtocol.RTSP)
            .map(ConnectionSetupDescribersTest::describerFor)
            .toList();

    assertThatThrownBy(() -> new ConnectionSetupDescribers(withoutRtsp))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing connection setup describer")
        .hasMessageContaining("RTSP");
  }

  @Test
  void refusesToStartWithTwoDescribersForOneTransport() {
    var all =
        new ArrayList<>(
            Stream.of(TransportProtocol.values())
                .map(ConnectionSetupDescribersTest::describerFor)
                .toList());
    all.add(describerFor(TransportProtocol.UDP));

    assertThatThrownBy(() -> new ConnectionSetupDescribers(all))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate connection setup describer for UDP");
  }

  /** Builds a describer for every transport; those absent from {@code setups} have no form. */
  private ConnectionSetupDescribers registry(
      Map<TransportProtocol, ProtocolConnectionSetup> setups) {
    for (var protocol : TransportProtocol.values()) {
      var describer = describerFor(protocol);
      when(describer.describe(any())).thenReturn(Optional.ofNullable(setups.get(protocol)));
      describers.put(protocol, describer);
    }
    return new ConnectionSetupDescribers(List.copyOf(describers.values()));
  }

  private static ConnectionSetupDescriber describerFor(TransportProtocol protocol) {
    var describer = mock(ConnectionSetupDescriber.class);
    when(describer.protocol()).thenReturn(protocol);
    return describer;
  }

  private static IntegrationConnectionSchema schema(Set<TransportProtocol> protocols) {
    return new TestConnectionSchema(
        UUID.fromString("1ce6c2c6-2f29-4a5f-9f6e-3f9a2f7f5b21"), 1, "Name", "Name", protocols);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TransportConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SocketDataSourceTest {

  @Test
  void fromSpecRetainsTheExactIntegrationReference() {
    var integration = new IntegrationConnectionSchemaReference(UUID.randomUUID(), 7);

    var source =
        fromSpec(
            new TcpConfig("tak.example.test", 8089, null, null, null, null, new NoSecurity()),
            integration);

    assertThat(source.integration()).isEqualTo(integration);
  }

  @ParameterizedTest
  @MethodSource("transports")
  void providerIsTheKindOfTheSpecTransport(TransportConfig transport, TransportKind expected) {
    var source = fromSpec(transport, null);

    assertThat(source.provider()).isEqualTo(expected);
    assertThat(source.spec().transport()).isSameAs(transport);
    assertThat(source.enabled()).isTrue();
  }

  @Test
  void rejectsAMissingSpec() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                SocketDataSource.fromSpec(
                    UUID.randomUUID(),
                    "no-spec",
                    UUID.randomUUID(),
                    null,
                    "default",
                    null,
                    null,
                    AccessMode.READ,
                    true,
                    null))
        .withMessage("spec");
  }

  private static Stream<Arguments> transports() {
    return Stream.of(
        Arguments.of(
            new TcpConfig("tak.example.test", 8089, null, null, null, null, new NoSecurity()),
            TransportKind.TCP),
        Arguments.of(new UdpConfig("127.0.0.1", 9000, 9100), TransportKind.UDP),
        Arguments.of(new RtspConfig("rtsp://localhost:8554/klv", null), TransportKind.RTSP));
  }

  private static SocketDataSource fromSpec(
      TransportConfig transport, IntegrationConnectionSchemaReference integration) {
    return SocketDataSource.fromSpec(
        UUID.randomUUID(),
        "socket-source",
        UUID.randomUUID(),
        null,
        "default",
        new ConnectionSpec(transport),
        integration,
        AccessMode.READ,
        true,
        null);
  }
}

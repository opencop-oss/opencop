// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ExtractorProtocolTest {

  @Test
  void keysEveryTransportProtocol() {
    assertThat(Arrays.stream(TransportProtocol.values()).map(ExtractorProtocol::of))
        .extracting(ExtractorProtocol::protocol)
        .containsExactly(TransportProtocol.values());
  }

  @Test
  void resolvesEachProtocolToItsOwnKey() {
    assertThat(ExtractorProtocol.of(TransportProtocol.TCP)).isSameAs(ExtractorProtocol.TCP);
    assertThat(ExtractorProtocol.of(TransportProtocol.UDP)).isSameAs(ExtractorProtocol.UDP);
    assertThat(ExtractorProtocol.of(TransportProtocol.RTSP)).isSameAs(ExtractorProtocol.RTSP);
    assertThat(ExtractorProtocol.of(TransportProtocol.KAFKA)).isSameAs(ExtractorProtocol.KAFKA);
  }

  /** A TCP connection that selects an integration reads its input only through its extractors. */
  @Test
  void requiresAnExtractorOnlyWhereTheHostReadsNoInputItself() {
    assertThat(ExtractorProtocol.TCP.requiresExtractor()).isTrue();
    assertThat(ExtractorProtocol.UDP.requiresExtractor()).isFalse();
    assertThat(ExtractorProtocol.RTSP.requiresExtractor()).isFalse();
    assertThat(ExtractorProtocol.KAFKA.requiresExtractor()).isFalse();
  }

  /** Each capability fixes its protocol's material, so a typed lookup never needs to cast. */
  @Test
  void requiresEachProtocolsOwnCapability() {
    assertThat(ExtractorProtocol.TCP.capability())
        .isEqualTo(TcpConnectionExtractorCapability.class);
    assertThat(ExtractorProtocol.UDP.capability())
        .isEqualTo(UdpConnectionExtractorCapability.class);
    assertThat(ExtractorProtocol.RTSP.capability())
        .isEqualTo(RtspConnectionExtractorCapability.class);
    assertThat(ExtractorProtocol.KAFKA.capability())
        .isEqualTo(KafkaConnectionExtractorCapability.class);
  }
}

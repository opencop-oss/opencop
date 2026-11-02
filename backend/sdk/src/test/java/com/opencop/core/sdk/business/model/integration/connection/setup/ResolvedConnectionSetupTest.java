// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.business.model.profile.MapperReference;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResolvedConnectionSetupTest {

  private static final UUID EXTRACTOR = UUID.randomUUID();
  private static final String HOST = "tak.example.test";
  private static final int PORT = 8089;

  private final IntegrationConnectionSchemaReference integration =
      new IntegrationConnectionSchemaReference(UUID.randomUUID(), 3);
  private final TcpConnectionMaterial material =
      TcpConnectionMaterial.plain(new TcpEndpointMaterial(HOST, PORT));
  private final Optional<MapperReference> ingress =
      Optional.of(new MapperReference(UUID.randomUUID(), 7));
  private final Optional<MapperReference> egress =
      Optional.of(new MapperReference(UUID.randomUUID(), 2));
  private final ResolvedTcpFraming framing = new ResolvedTcpFraming.Integration(UUID.randomUUID());

  @Test
  void tcpSetupCarriesTheIntegrationResolvedSliceAndDeclaresItsProtocol() {
    ResolvedConnectionSetup setup = setup(AccessMode.READ, ingress, Optional.empty());

    assertThat(setup.integration()).isEqualTo(integration);
    assertThat(setup.extractorId()).isEqualTo(EXTRACTOR);
    assertThat(setup.ingressMapper()).isEqualTo(ingress);
    assertThat(setup.protocol()).isEqualTo(TransportProtocol.TCP);

    switch (setup) {
      case ResolvedTcpConnectionSetup tcp -> {
        assertThat(tcp.material()).isEqualTo(material);
        assertThat(tcp.framing()).isEqualTo(framing);
      }
    }
  }

  @Test
  void onlyTheTcpVariantCanExpressAResolvedSetup() {
    assertThat(ResolvedConnectionSetup.class.isSealed()).isTrue();
    assertThat(ResolvedConnectionSetup.class.getPermittedSubclasses())
        .containsExactly(ResolvedTcpConnectionSetup.class);
  }

  @Test
  void holdsExactlyTheMappersItsAccessUses() {
    assertThat(setup(AccessMode.WRITE, Optional.empty(), egress).egressMapper()).isEqualTo(egress);
    assertThat(setup(AccessMode.READ_WRITE, ingress, egress).ingressMapper()).isEqualTo(ingress);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> setup(AccessMode.READ, Optional.empty(), Optional.empty()));
    assertThatIllegalArgumentException().isThrownBy(() -> setup(AccessMode.READ, ingress, egress));
    assertThatIllegalArgumentException().isThrownBy(() -> setup(AccessMode.WRITE, ingress, egress));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> setup(AccessMode.READ_WRITE, ingress, Optional.empty()));
  }

  @Test
  void tcpSetupRejectsEveryMissingPart() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ResolvedTcpConnectionSetup(
                    null,
                    EXTRACTOR,
                    material,
                    AccessMode.READ,
                    ingress,
                    Optional.empty(),
                    framing));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ResolvedTcpConnectionSetup(
                    integration,
                    null,
                    material,
                    AccessMode.READ,
                    ingress,
                    Optional.empty(),
                    framing));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ResolvedTcpConnectionSetup(
                    integration,
                    EXTRACTOR,
                    null,
                    AccessMode.READ,
                    ingress,
                    Optional.empty(),
                    framing));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ResolvedTcpConnectionSetup(
                    integration,
                    EXTRACTOR,
                    material,
                    AccessMode.READ,
                    ingress,
                    Optional.empty(),
                    null));
  }

  private ResolvedTcpConnectionSetup setup(
      AccessMode mode,
      Optional<MapperReference> ingressMapper,
      Optional<MapperReference> egressMapper) {
    return new ResolvedTcpConnectionSetup(
        integration, EXTRACTOR, material, mode, ingressMapper, egressMapper, framing);
  }
}

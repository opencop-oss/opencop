// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.connection.material.tcp;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.DetachedBinaryMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.business.policy.integration.connection.material.MaterialValidationPolicy;
import com.opencop.core.sdk.business.policy.integration.connection.material.certificate.CertificateMaterialPolicy;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TcpConnectionMaterialPolicyTest {

  private final MaterialValidationPolicy<TcpConnectionMaterial> policy =
      new TcpConnectionMaterialPolicy(new CertificateMaterialPolicy());

  @Test
  void acceptsCompletePlainAndMtlsMaterial() {
    var endpoint = new TcpEndpointMaterial("tak.example.test", 8089);
    var client = certificate("client-secret");
    var ca = certificate("ca-secret");

    assertThatNoException()
        .isThrownBy(() -> policy.validate(TcpConnectionMaterial.plain(endpoint)));
    assertThatNoException()
        .isThrownBy(
            () ->
                policy.validate(
                    TcpConnectionMaterial.mtls(
                        endpoint, new MtlsCredentialMaterial(client, Optional.of(ca)))));
  }

  @Test
  void rejectsMissingMaterial() {
    assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(null));
  }

  @Test
  void rejectsBlankHostAndOutOfRangePorts() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> policy.validate(TcpConnectionMaterial.plain(new TcpEndpointMaterial(" ", 8089))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                policy.validate(
                    TcpConnectionMaterial.plain(new TcpEndpointMaterial("tak.example.test", 0))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                policy.validate(
                    TcpConnectionMaterial.plain(
                        new TcpEndpointMaterial("tak.example.test", 65_536))));
  }

  @Test
  void appliesCertificatePolicyToClientAndCaMaterial() {
    var endpoint = new TcpEndpointMaterial("tak.example.test", 8089);
    var invalidCa =
        TcpConnectionMaterial.mtls(
            endpoint,
            new MtlsCredentialMaterial(
                certificate("client-secret"), Optional.of(certificate(" "))));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                policy.validate(
                    TcpConnectionMaterial.mtls(
                        endpoint,
                        new MtlsCredentialMaterial(
                            new CertificateMaterial(
                                new DetachedBinaryMaterial(new byte[0]),
                                "secret",
                                CertificateStoreFormat.PKCS12),
                            Optional.empty()))));
    assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(invalidCa));
  }

  private static CertificateMaterial certificate(String password) {
    return new CertificateMaterial(
        new DetachedBinaryMaterial(new byte[] {1}), password, CertificateStoreFormat.PKCS12);
  }
}

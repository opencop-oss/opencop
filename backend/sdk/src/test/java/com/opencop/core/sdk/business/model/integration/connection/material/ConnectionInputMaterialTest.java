// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.DetachedBinaryMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.NoSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectionInputMaterialTest {

  @Test
  void certificateContentIsDefensivelyOwnedRepeatableAndRedacted() throws Exception {
    var source = new byte[] {1, 2, 3};
    var binary = new DetachedBinaryMaterial(source);
    var certificate = new CertificateMaterial(binary, "top-secret", CertificateStoreFormat.PKCS12);
    var mtls = new MtlsCredentialMaterial(certificate, Optional.of(certificate));
    source[0] = 99;

    assertThat(binary.openStream().readAllBytes()).containsExactly(1, 2, 3);
    assertThat(binary.openStream().readAllBytes()).containsExactly(1, 2, 3);
    assertThat(binary.toString()).doesNotContain("1, 2, 3");
    assertThat(certificate.toString()).doesNotContain("top-secret", "1, 2, 3");
    assertThat(mtls.toString()).doesNotContain("top-secret", "1, 2, 3");
  }

  @Test
  void requiredMaterialStructureMustNotBeNull() {
    var certificate =
        new CertificateMaterial(
            new DetachedBinaryMaterial(new byte[] {1}), "secret", CertificateStoreFormat.PKCS12);

    assertThatNullPointerException()
        .isThrownBy(() -> new CertificateMaterial(null, "secret", CertificateStoreFormat.PKCS12));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CertificateMaterial(
                    new DetachedBinaryMaterial(new byte[] {1}), "secret", null));
    assertThatNullPointerException().isThrownBy(() -> new MtlsCredentialMaterial(null, null));
    assertThatNullPointerException()
        .isThrownBy(() -> new MtlsCredentialMaterial(certificate, null));
    assertThatNullPointerException()
        .isThrownBy(() -> new TcpConnectionMaterial(null, new NoSecurityMaterial()));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new TcpConnectionMaterial(new TcpEndpointMaterial("tak.example.test", 8089), null));
  }
}

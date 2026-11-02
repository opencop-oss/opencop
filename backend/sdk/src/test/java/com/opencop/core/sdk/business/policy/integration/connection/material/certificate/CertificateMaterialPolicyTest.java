// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.connection.material.certificate;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.DetachedBinaryMaterial;
import com.opencop.core.sdk.business.policy.integration.connection.material.MaterialValidationPolicy;
import org.junit.jupiter.api.Test;

class CertificateMaterialPolicyTest {

  private final MaterialValidationPolicy<CertificateMaterial> policy =
      new CertificateMaterialPolicy();

  @Test
  void acceptsCompleteCertificateMaterial() {
    assertThatNoException()
        .isThrownBy(
            () ->
                policy.validate(
                    new CertificateMaterial(
                        new DetachedBinaryMaterial(new byte[] {1}),
                        "secret",
                        CertificateStoreFormat.PKCS12)));
  }

  @Test
  void rejectsEmptyCertificateContent() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                policy.validate(
                    new CertificateMaterial(
                        new DetachedBinaryMaterial(new byte[0]),
                        "secret",
                        CertificateStoreFormat.PKCS12)));
  }

  @Test
  void rejectsBlankPassword() {
    var content = new DetachedBinaryMaterial(new byte[] {1});

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                policy.validate(
                    new CertificateMaterial(content, " ", CertificateStoreFormat.PKCS12)));
  }
}

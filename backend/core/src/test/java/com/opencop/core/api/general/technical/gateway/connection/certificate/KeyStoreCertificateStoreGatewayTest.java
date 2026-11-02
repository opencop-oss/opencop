// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.connection.certificate;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.business.model.integration.connection.material.certificate.DetachedBinaryMaterial;
import com.opencop.core.sdk.testing.connection.TestCertificateStores;
import org.junit.jupiter.api.Test;

class KeyStoreCertificateStoreGatewayTest {

  private static final String PASSWORD = "store-secret";
  private static final int STRONGEST_ITERATIONS = 5_000_000;
  private static final int CRAFTED_SECTIONS = 3;

  private final KeyStoreCertificateStoreGateway gateway = new KeyStoreCertificateStoreGateway();

  @Test
  void opensAStoreWithThePasswordItWasWrittenWith() {
    assertThat(
            gateway.opensWithItsPassword(store(TestCertificateStores.pkcs12(PASSWORD), PASSWORD)))
        .isTrue();
  }

  @Test
  void doesNotOpenAStoreWithAnotherPassword() {
    assertThat(
            gateway.opensWithItsPassword(
                store(TestCertificateStores.pkcs12(PASSWORD), "another-password")))
        .isFalse();
  }

  @Test
  void doesNotOpenContentThatIsNotAStore() {
    assertThat(gateway.opensWithItsPassword(store(new byte[] {1, 2, 3}, PASSWORD))).isFalse();
  }

  /** A store repeating a costly encrypted section would open; it is refused without opening it. */
  @Test
  void doesNotOpenAStoreDeclaringMoreKeyDerivationThanTheBudget() {
    var crafted =
        TestCertificateStores.withEncryptedSectionsRepeated(
            TestCertificateStores.pkcs12WithEncryptedCertificate(PASSWORD, STRONGEST_ITERATIONS),
            CRAFTED_SECTIONS);

    assertThat(gateway.opensWithItsPassword(store(crafted, PASSWORD))).isFalse();
  }

  private static CertificateMaterial store(byte[] content, String password) {
    return new CertificateMaterial(
        new DetachedBinaryMaterial(content), password, CertificateStoreFormat.PKCS12);
  }
}

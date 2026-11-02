// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.integration.connection.material.certificate.CertificateStoreFormat;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import com.opencop.core.sdk.testing.connection.TestCertificateStores;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class CertificateStoreOpeningTest {

  private static final String FIELD = "keyStore";
  private static final String PASSWORD = "client-secret";
  private static final int STRONGEST_ITERATIONS = 5_000_000;
  private static final int CRAFTED_SECTIONS = 3;

  @Test
  void acceptsAStoreThatOpensWithItsPassword() {
    assertThatNoException()
        .isThrownBy(
            () ->
                CertificateStoreOpening.requireOpenable(
                    FIELD,
                    TestCertificateStores.pkcs12(PASSWORD),
                    PASSWORD,
                    CertificateStoreFormat.PKCS12));
  }

  @Test
  void rejectsAWrongPasswordNamingTheField() {
    assertUnreadable(TestCertificateStores.pkcs12(PASSWORD), "not-the-password");
  }

  @Test
  void rejectsContentThatIsNoCertificateStore() {
    assertUnreadable(new byte[] {1, 2, 3}, PASSWORD);
  }

  /** A store at the strongest settings the JDK opens, one encrypted section, still opens. */
  @Test
  void acceptsAStoreWithOneStronglyEncryptedSection() {
    var strongest =
        TestCertificateStores.pkcs12WithEncryptedCertificate(PASSWORD, STRONGEST_ITERATIONS);

    assertThatNoException()
        .isThrownBy(
            () ->
                CertificateStoreOpening.requireOpenable(
                    FIELD, strongest, PASSWORD, CertificateStoreFormat.PKCS12));
    assertThat(CertificateStoreOpening.opens(strongest, PASSWORD, CertificateStoreFormat.PKCS12))
        .isTrue();
  }

  /**
   * A store repeating its encrypted section opens, paying the key derivation once per copy; it is
   * refused before being opened, naming its field.
   */
  @Test
  void refusesAStoreDeclaringMoreKeyDerivationThanTheBudgetNamingTheField() {
    var crafted = craftedStore();

    assertThatThrownBy(
            () ->
                CertificateStoreOpening.requireOpenable(
                    FIELD, crafted, PASSWORD, CertificateStoreFormat.PKCS12))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.failure()).isEqualTo(ConnectionInputFailure.INVALID_INPUT);
              assertThat(exception.field()).contains(FIELD);
              assertThat(exception.providerDetailCode())
                  .contains(CertificateStoreOpening.CERTIFICATE_STORE_TOO_EXPENSIVE);
            });
  }

  @Test
  void doesNotOpenAStoreDeclaringMoreKeyDerivationThanTheBudget() {
    assertThat(
            CertificateStoreOpening.opens(craftedStore(), PASSWORD, CertificateStoreFormat.PKCS12))
        .isFalse();
  }

  @Test
  void opensAStoreOnlyWithItsPassword() {
    var store = TestCertificateStores.pkcs12(PASSWORD);

    assertThat(CertificateStoreOpening.opens(store, PASSWORD, CertificateStoreFormat.PKCS12))
        .isTrue();
    assertThat(
            CertificateStoreOpening.opens(store, "not-the-password", CertificateStoreFormat.PKCS12))
        .isFalse();
    assertThat(
            CertificateStoreOpening.opens(
                new byte[] {1, 2, 3}, PASSWORD, CertificateStoreFormat.PKCS12))
        .isFalse();
  }

  @Test
  void readsAStoreUpToTheLimit() throws Exception {
    var atLimit = new byte[CertificateStoreOpening.MAX_STORE_BYTES];

    assertThat(CertificateStoreOpening.readBounded(FIELD, new ByteArrayInputStream(atLimit)))
        .hasSize(CertificateStoreOpening.MAX_STORE_BYTES);
  }

  /** A store past the limit is refused, naming its field, without reading the rest of it. */
  @Test
  void refusesAStorePastTheLimitNamingTheField() {
    var pastLimit = new ByteArrayInputStream(new byte[CertificateStoreOpening.MAX_STORE_BYTES * 4]);

    assertThatThrownBy(() -> CertificateStoreOpening.readBounded(FIELD, pastLimit))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.failure()).isEqualTo(ConnectionInputFailure.INVALID_INPUT);
              assertThat(exception.field()).contains(FIELD);
              assertThat(exception.providerDetailCode())
                  .contains(CertificateStoreOpening.CERTIFICATE_STORE_TOO_LARGE);
            });
    assertThat(pastLimit.available()).isEqualTo(CertificateStoreOpening.MAX_STORE_BYTES * 3 - 1);
  }

  private static byte[] craftedStore() {
    return TestCertificateStores.withEncryptedSectionsRepeated(
        TestCertificateStores.pkcs12WithEncryptedCertificate(PASSWORD, STRONGEST_ITERATIONS),
        CRAFTED_SECTIONS);
  }

  private static void assertUnreadable(byte[] content, String password) {
    assertThatThrownBy(
            () ->
                CertificateStoreOpening.requireOpenable(
                    FIELD, content, password, CertificateStoreFormat.PKCS12))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.failure()).isEqualTo(ConnectionInputFailure.INVALID_INPUT);
              assertThat(exception.field()).contains(FIELD);
              assertThat(exception.providerDetailCode())
                  .contains(CertificateStoreOpening.UNREADABLE_CERTIFICATE_STORE);
            });
  }
}

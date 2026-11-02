// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.tcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.business.policy.tcp.NativeTcpConnectionInputPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import com.opencop.core.sdk.technical.gateway.extractor.CertificateStoreOpening;
import com.opencop.core.sdk.testing.connection.TestCertificateStores;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class NativeMtlsTcpExtractorTest {

  private static final String HOST = "feed.example.test";
  private static final String CLIENT_PASSWORD = "client-secret";
  private static final String TRUSTSTORE_PASSWORD = "trust-secret";

  private final NativeMtlsTcpExtractor extractor =
      new NativeMtlsTcpExtractor(new NativeConnectionSchema(new NativeIntegration()));

  @Test
  void declaresTheEndpointAndTheCertificateStores() {
    assertThat(extractor.descriptor().fields())
        .extracting(ExtractorField::name, ExtractorField::kind, ExtractorField::required)
        .containsExactly(
            tuple("host", ExtractorFieldKind.TEXT, true),
            tuple("port", ExtractorFieldKind.TEXT, true),
            tuple("keyStore", ExtractorFieldKind.FILE, true),
            tuple("clientPassword", ExtractorFieldKind.SECRET, true),
            tuple("truststore", ExtractorFieldKind.FILE, false),
            tuple("truststorePassword", ExtractorFieldKind.SECRET, false));
  }

  @Test
  void extractsAClientOnlyConnectionFromAStoreThatOpens() throws ConnectionInputException {
    var material =
        extractor.extract(
            input(
                CLIENT_PASSWORD,
                null,
                store("keyStore", TestCertificateStores.pkcs12(CLIENT_PASSWORD))));

    assertThat(material.endpoint()).isEqualTo(new TcpEndpointMaterial(HOST, 8089));
    var credentials = (MtlsCredentialMaterial) material.security();
    assertThat(credentials.clientCertificate().password()).isEqualTo(CLIENT_PASSWORD);
    assertThat(credentials.caCertificate()).isEmpty();
  }

  @Test
  void extractsTheTruststoreWhenOneIsSupplied() throws ConnectionInputException {
    var material =
        extractor.extract(
            input(
                CLIENT_PASSWORD,
                TRUSTSTORE_PASSWORD,
                store("keyStore", TestCertificateStores.pkcs12(CLIENT_PASSWORD)),
                store("truststore", TestCertificateStores.pkcs12(TRUSTSTORE_PASSWORD))));

    var credentials = (MtlsCredentialMaterial) material.security();
    assertThat(credentials.caCertificate().orElseThrow().password()).isEqualTo(TRUSTSTORE_PASSWORD);
  }

  @Test
  void rejectsAStoreThatDoesNotOpenWithItsPasswordNamingTheStore() {
    assertRejected(
        () ->
            extractor.extract(
                input(
                    "wrong-password",
                    null,
                    store("keyStore", TestCertificateStores.pkcs12(CLIENT_PASSWORD)))),
        "keyStore",
        CertificateStoreOpening.UNREADABLE_CERTIFICATE_STORE);
    assertRejected(
        () ->
            extractor.extract(
                input(
                    CLIENT_PASSWORD,
                    TRUSTSTORE_PASSWORD,
                    store("keyStore", TestCertificateStores.pkcs12(CLIENT_PASSWORD)),
                    store("truststore", new byte[] {1, 2, 3}))),
        "truststore",
        CertificateStoreOpening.UNREADABLE_CERTIFICATE_STORE);
  }

  @Test
  void rejectsAStorePastTheSizeLimitNamingTheStore() {
    assertRejected(
        () ->
            extractor.extract(
                input(
                    CLIENT_PASSWORD,
                    null,
                    store("keyStore", new byte[CertificateStoreOpening.MAX_STORE_BYTES + 1]))),
        "keyStore",
        CertificateStoreOpening.CERTIFICATE_STORE_TOO_LARGE);
  }

  @Test
  void rejectsAnIncompleteUploadBeforeReadingAnyStore() {
    assertRejected(
        () -> extractor.validate(input(CLIENT_PASSWORD, null)),
        "keyStore",
        NativeTcpConnectionInputPolicy.MISSING_CERTIFICATE_STORE);
  }

  private static void assertRejected(ThrowingCallable call, String field, String detailCode) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.failure()).isEqualTo(ConnectionInputFailure.INVALID_INPUT);
              assertThat(exception.field()).contains(field);
              assertThat(exception.providerDetailCode()).contains(detailCode);
            });
  }

  private static TcpConnectionInput input(
      String clientPassword, String truststorePassword, ConnectionInputResource... stores) {
    var values = new ArrayList<ConnectionInputValue>();
    values.add(new ConnectionInputValue("host", HOST));
    values.add(new ConnectionInputValue("port", "8089"));
    values.add(new ConnectionInputValue("clientPassword", clientPassword));
    if (truststorePassword != null) {
      values.add(new ConnectionInputValue("truststorePassword", truststorePassword));
    }
    return new TcpConnectionInput(values, List.of(stores));
  }

  private static ConnectionInputResource store(String field, byte[] content) {
    return new ConnectionInputResource(
        field,
        field + ".p12",
        "application/x-pkcs12",
        content.length,
        () -> new ByteArrayInputStream(content));
  }
}

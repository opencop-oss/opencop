// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.tcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class NativeTcpConnectionInputPolicyTest {

  private static final String HOST = "feed.example.test";
  private static final String CLIENT_PASSWORD = "client-secret";
  private static final String TRUSTSTORE_PASSWORD = "trust-secret";

  @Test
  void readsAnEndpointSuppliedAsText() throws ConnectionInputException {
    assertThat(NativeTcpConnectionInputPolicy.requireEndpoint(HOST, " 8089 "))
        .isEqualTo(new TcpEndpointMaterial(HOST, 8089));
  }

  @Test
  void rejectsAnUnusableEndpointNamingTheField() {
    assertRejected(
        () -> NativeTcpConnectionInputPolicy.requireEndpoint(" ", "8089"),
        "host",
        NativeTcpConnectionInputPolicy.MISSING_HOST);
    assertRejected(
        () -> NativeTcpConnectionInputPolicy.requireEndpoint(HOST, (String) null),
        "port",
        NativeTcpConnectionInputPolicy.MISSING_PORT);
    assertRejected(
        () -> NativeTcpConnectionInputPolicy.requireEndpoint(HOST, "eighty"),
        "port",
        NativeTcpConnectionInputPolicy.INVALID_PORT);
    assertRejected(
        () -> NativeTcpConnectionInputPolicy.requireEndpoint(HOST, "65536"),
        "port",
        NativeTcpConnectionInputPolicy.INVALID_PORT);
    assertRejected(
        () -> NativeTcpConnectionInputPolicy.requireEndpoint(HOST, "0"),
        "port",
        NativeTcpConnectionInputPolicy.INVALID_PORT);
  }

  @Test
  void rejectsEachCertificateUploadMistakeNamingTheField() {
    assertRejected(
        () -> uploads(List.of(), CLIENT_PASSWORD, null),
        "keyStore",
        NativeTcpConnectionInputPolicy.MISSING_CERTIFICATE_STORE);
    assertRejected(
        () ->
            uploads(
                List.of(resource("keyStore", 1), resource("keyStore", 1)), CLIENT_PASSWORD, null),
        "keyStore",
        NativeTcpConnectionInputPolicy.DUPLICATE_CERTIFICATE_STORE);
    assertRejected(
        () -> uploads(List.of(resource("keyStore", 0)), CLIENT_PASSWORD, null),
        "keyStore",
        NativeTcpConnectionInputPolicy.EMPTY_CERTIFICATE_STORE);
    assertRejected(
        () -> uploads(List.of(resource("keyStore", 1)), " ", null),
        "clientPassword",
        NativeTcpConnectionInputPolicy.MISSING_CERTIFICATE_PASSWORD);
    assertRejected(
        () ->
            uploads(
                List.of(resource("keyStore", 1), resource("truststore", 1)), CLIENT_PASSWORD, null),
        "truststorePassword",
        NativeTcpConnectionInputPolicy.MISSING_CERTIFICATE_PASSWORD);
    assertRejected(
        () -> uploads(List.of(resource("keyStore", 1)), CLIENT_PASSWORD, TRUSTSTORE_PASSWORD),
        "truststorePassword",
        NativeTcpConnectionInputPolicy.UNEXPECTED_CERTIFICATE_PASSWORD);
    assertRejected(
        () ->
            uploads(
                List.of(resource("keyStore", 1), resource("archive", 1)), CLIENT_PASSWORD, null),
        "archive",
        NativeTcpConnectionInputPolicy.UNEXPECTED_FILE);
  }

  @Test
  void rejectsAnyUploadOnAPlainConnection() {
    assertRejected(
        () ->
            NativeTcpConnectionInputPolicy.requireNoFiles(
                new DeclaredConnectionInput(List.of(), List.of(resource("keyStore", 1)))),
        "keyStore",
        NativeTcpConnectionInputPolicy.UNEXPECTED_FILE);
  }

  private static void uploads(
      List<ConnectionInputResource> resources, String clientPassword, String truststorePassword)
      throws ConnectionInputException {
    NativeTcpConnectionInputPolicy.requireCertificateUploads(
        new DeclaredConnectionInput(List.of(), resources), clientPassword, truststorePassword);
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

  private static ConnectionInputResource resource(String role, long byteSize) {
    return new ConnectionInputResource(
        role,
        role + ".p12",
        "application/x-pkcs12",
        byteSize,
        () -> new ByteArrayInputStream(new byte[] {1}));
  }
}

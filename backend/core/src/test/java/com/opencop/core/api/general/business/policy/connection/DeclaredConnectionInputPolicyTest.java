// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileListField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.SecretField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.TextField;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeclaredConnectionInputPolicyTest {

  private static final ExtractorDescriptor DESCRIPTOR =
      new ExtractorDescriptor(
          UUID.fromString("0f6a2c9e-7d4b-4e1f-8a3c-5b9d2e7f1a60"),
          "Mutual TLS",
          List.of(
              new TextField("host", "Host", true),
              new SecretField("clientPassword", "Client password", true),
              new FileField("keyStore", "Client store", true, Set.of(".p12"), Set.of()),
              new FileListField(
                  "caCertificates", "CA certificates", false, Set.of(".pem"), Set.of())));

  @Test
  void acceptsValuesAndFilesUnderTheirDeclaredFields() {
    var input =
        new DeclaredConnectionInput(
            List.of(value("host"), value("clientPassword")),
            List.of(file("keyStore"), file("caCertificates"), file("caCertificates")));

    assertThatNoException()
        .isThrownBy(() -> DeclaredConnectionInputPolicy.requireDeclared(DESCRIPTOR, input));
  }

  @Test
  void acceptsAnInputThatOmitsDeclaredFields() {
    assertThatNoException()
        .isThrownBy(
            () ->
                DeclaredConnectionInputPolicy.requireDeclared(
                    DESCRIPTOR, new DeclaredConnectionInput(List.of(), List.of())));
  }

  @Test
  void rejectsAValueNoFieldDeclares() {
    assertRejected(new DeclaredConnectionInput(List.of(value("hots")), List.of()), "hots");
  }

  @Test
  void rejectsAFileNoFieldDeclares() {
    assertRejected(
        new DeclaredConnectionInput(List.of(), List.of(file("truststore"))), "truststore");
  }

  /** A file field cannot be filled with text, which would skip the file the extractor reads. */
  @Test
  void rejectsAValueUnderAFileField() {
    assertRejected(new DeclaredConnectionInput(List.of(value("keyStore")), List.of()), "keyStore");
  }

  @Test
  void rejectsAFileUnderATextField() {
    assertRejected(new DeclaredConnectionInput(List.of(), List.of(file("host"))), "host");
  }

  private static void assertRejected(DeclaredConnectionInput input, String field) {
    assertThatThrownBy(() -> DeclaredConnectionInputPolicy.requireDeclared(DESCRIPTOR, input))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.CONNECTION_INPUT_INVALID.code());
              assertThat(exception.getDetailCode())
                  .isEqualTo(DeclaredConnectionInputPolicy.UNDECLARED_FIELD);
              assertThat(exception.getField()).isEqualTo(field);
            });
  }

  private static ConnectionInputValue value(String name) {
    return new ConnectionInputValue(name, "supplied");
  }

  private static ConnectionInputResource file(String role) {
    return new ConnectionInputResource(
        role,
        role + ".bin",
        "application/octet-stream",
        1,
        () -> new ByteArrayInputStream(new byte[] {1}));
  }
}

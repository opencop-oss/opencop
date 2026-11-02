// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.input.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StandardExtractorFieldTest {

  @Test
  void declaresATextFieldUnderItsStandardNameAndKey() {
    var field = StandardExtractorField.HOST.declare(true);

    assertThat(field).isInstanceOf(TextField.class);
    assertThat(field.name()).isEqualTo("host");
    assertThat(field.required()).isTrue();
    assertThat(field.label()).isNotBlank();
    assertThat(field.standard()).contains(StandardExtractorField.HOST);
  }

  @Test
  void declaresASecretFieldForAPassword() {
    var field = StandardExtractorField.CLIENT_CERTIFICATE_PASSWORD.declare(false);

    assertThat(field).isInstanceOf(SecretField.class);
    assertThat(field.name()).isEqualTo("clientPassword");
    assertThat(field.required()).isFalse();
  }

  @Test
  void declaresACertificateStoreAsAFileAcceptingPkcs12Extensions() {
    var field = StandardExtractorField.CLIENT_CERTIFICATE_STORE.declare(true);

    assertThat(field)
        .isInstanceOfSatisfying(
            FileField.class, file -> assertThat(file.extensions()).containsExactly(".p12", ".pfx"));
    assertThat(field.name()).isEqualTo("keyStore");
  }

  /** Clients already send these multipart roles, so the wire names are frozen as they are. */
  @Test
  void keepsTheWireNamesClientsAlreadySend() {
    assertThat(StandardExtractorField.values())
        .extracting(StandardExtractorField::fieldName)
        .containsExactly(
            "host", "port", "keyStore", "clientPassword", "truststore", "truststorePassword");
  }

  @Test
  void leavesAFieldDeclaredWithoutAKeyNonStandard() {
    assertThat(new TextField("endpoint", "Endpoint", true).standard()).isEmpty();
    assertThat(new SecretField("token", "Token", true).standard()).isEmpty();
    assertThat(new FileField("upload", "Upload", true, Set.of(".zip"), Set.of()).standard())
        .isEmpty();
    assertThat(new FileListField("uploads", "Uploads", true, Set.of(".zip"), Set.of()).standard())
        .isEmpty();
  }

  @Test
  void rejectsAStandardKeyOnAFieldOfAnotherName() {
    assertThatThrownBy(
            () ->
                new TextField("address", "Address", true, Optional.of(StandardExtractorField.HOST)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HOST")
        .hasMessageContaining("address");
  }

  @Test
  void rejectsAStandardKeyOnAFieldOfAnotherKind() {
    assertThatThrownBy(
            () ->
                new TextField(
                    "keyStore",
                    "Key store",
                    true,
                    Optional.of(StandardExtractorField.CLIENT_CERTIFICATE_STORE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CLIENT_CERTIFICATE_STORE")
        .hasMessageContaining("FILE");
  }

  /** A client shows its own translation of a standard field, so no extractor rewords it. */
  @Test
  void rejectsAStandardKeyOnAFieldWithAnotherLabel() {
    assertThatThrownBy(
            () ->
                new TextField(
                    "host", "Server host", true, Optional.of(StandardExtractorField.HOST)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HOST")
        .hasMessageContaining("Server host");
  }

  @Test
  void rejectsAStandardKeyOnAStoreAcceptingOtherFormats() {
    assertThatThrownBy(
            () ->
                new FileField(
                    "keyStore",
                    "Client certificate store",
                    true,
                    Set.of(".jks"),
                    Set.of(),
                    Optional.of(StandardExtractorField.CLIENT_CERTIFICATE_STORE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CLIENT_CERTIFICATE_STORE");
  }

  @Test
  void readsItsValueBackFromTheInputByItsStandardName() {
    var input =
        new TcpConnectionInput(List.of(new ConnectionInputValue("port", "8089")), List.of());

    assertThat(StandardExtractorField.PORT.valueIn(input)).contains("8089");
    assertThat(StandardExtractorField.HOST.valueIn(input)).isEmpty();
  }
}

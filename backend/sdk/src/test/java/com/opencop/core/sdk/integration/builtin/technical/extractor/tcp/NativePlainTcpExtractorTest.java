// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.tcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.NoSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.business.policy.tcp.NativeTcpConnectionInputPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativePlainTcpExtractorTest {

  private static final String HOST = "feed.example.test";

  private final NativePlainTcpExtractor extractor =
      new NativePlainTcpExtractor(new NativeConnectionSchema(new NativeIntegration()));

  @Test
  void belongsToNativeTcp() {
    assertThat(extractor.protocol()).isEqualTo(TransportProtocol.TCP);
    assertThat(extractor.schema().reference()).isEqualTo(NativeConnectionSchema.V1_REFERENCE);
    assertThat(extractor.descriptor().id()).isEqualTo(NativePlainTcpExtractor.EXTRACTOR_ID);
  }

  @Test
  void declaresTheStandardHostAndPort() {
    assertThat(extractor.descriptor().fields())
        .extracting(ExtractorField::name, ExtractorField::kind, ExtractorField::required)
        .containsExactly(
            tuple("host", ExtractorFieldKind.TEXT, true),
            tuple("port", ExtractorFieldKind.TEXT, true));
  }

  @Test
  void extractsAPlainEndpoint() throws ConnectionInputException {
    var material = extractor.extract(declared(List.of()));

    assertThat(material.endpoint()).isEqualTo(new TcpEndpointMaterial(HOST, 8089));
    assertThat(material.security()).isInstanceOf(NoSecurityMaterial.class);
  }

  @Test
  void rejectsAnUploadNamingIt() {
    assertThatThrownBy(() -> extractor.validate(declared(List.of(keyStore()))))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.field()).contains("keyStore");
              assertThat(exception.providerDetailCode())
                  .contains(NativeTcpConnectionInputPolicy.UNEXPECTED_FILE);
            });
  }

  private static TcpConnectionInput declared(List<ConnectionInputResource> resources) {
    return new TcpConnectionInput(
        List.of(new ConnectionInputValue("host", HOST), new ConnectionInputValue("port", "8089")),
        resources);
  }

  private static ConnectionInputResource keyStore() {
    return new ConnectionInputResource(
        "keyStore",
        "client.p12",
        "application/x-pkcs12",
        1,
        () -> new ByteArrayInputStream(new byte[] {1}));
  }
}

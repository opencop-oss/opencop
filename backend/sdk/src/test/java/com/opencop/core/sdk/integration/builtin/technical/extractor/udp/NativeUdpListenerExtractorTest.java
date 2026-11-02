// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.udp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.NoSecurityMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.business.policy.udp.UdpListenPortPolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeUdpListenerExtractorTest {

  private static final String HOST = "0.0.0.0";

  private final NativeUdpListenerExtractor extractor =
      new NativeUdpListenerExtractor(new NativeConnectionSchema(new NativeIntegration()));

  @Test
  void belongsToNativeUdp() {
    assertThat(extractor.protocol()).isEqualTo(TransportProtocol.UDP);
    assertThat(extractor.schema().reference()).isEqualTo(NativeConnectionSchema.V1_REFERENCE);
    assertThat(extractor.id()).isEqualTo(NativeUdpListenerExtractor.EXTRACTOR_ID);
    assertThat(extractor.descriptor().id()).isEqualTo(extractor.id());
  }

  @Test
  void declaresTheStandardHostAndItsOwnListenPort() {
    assertThat(extractor.descriptor().fields())
        .extracting(ExtractorField::name, ExtractorField::kind, ExtractorField::required)
        .containsExactly(
            tuple("host", ExtractorFieldKind.TEXT, true),
            tuple("listenPort", ExtractorFieldKind.TEXT, true));
    assertThat(extractor.descriptor().fields().getFirst().standard())
        .contains(StandardExtractorField.HOST);
    assertThat(extractor.descriptor().fields().get(1).standard()).isEmpty();
  }

  @Test
  void extractsAPlainListenerAtTheDeclaredHostAndPort() throws ConnectionInputException {
    var material = extractor.extract(input(HOST, "8087"));

    assertThat(material.endpoint().host()).isEqualTo(HOST);
    assertThat(material.endpoint().port()).isEqualTo(8087);
    assertThat(material.security()).isInstanceOf(NoSecurityMaterial.class);
  }

  @Test
  void rejectsAnUnusableListenPortNamingTheField() {
    assertRejected(input(HOST, "65536"), "listenPort", UdpListenPortPolicy.INVALID_PORT);
    assertRejected(input(HOST, null), "listenPort", UdpListenPortPolicy.MISSING_PORT);
  }

  @Test
  void rejectsAMissingHostNamingTheField() {
    assertRejected(input(null, "8087"), "host", NativeUdpListenerExtractor.MISSING_HOST);
    assertRejected(input(" ", "8087"), "host", NativeUdpListenerExtractor.MISSING_HOST);
  }

  private void assertRejected(DeclaredConnectionInput input, String field, String detailCode) {
    assertThatThrownBy(() -> extractor.validate(input))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.failure()).isEqualTo(ConnectionInputFailure.INVALID_INPUT);
              assertThat(exception.field()).contains(field);
              assertThat(exception.providerDetailCode()).contains(detailCode);
            });
  }

  private static DeclaredConnectionInput input(String host, String listenPort) {
    var values = new ArrayList<ConnectionInputValue>();
    if (host != null) {
      values.add(new ConnectionInputValue("host", host));
    }
    if (listenPort != null) {
      values.add(new ConnectionInputValue("listenPort", listenPort));
    }
    return new DeclaredConnectionInput(values, List.of());
  }
}

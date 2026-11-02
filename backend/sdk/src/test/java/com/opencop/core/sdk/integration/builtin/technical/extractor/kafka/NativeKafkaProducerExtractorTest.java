// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.extractor.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.input.DeclaredConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.ScramSecurityMaterial;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.business.policy.kafka.KafkaProducerByteRatePolicy;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeKafkaProducerExtractorTest {

  private final NativeKafkaProducerExtractor extractor =
      new NativeKafkaProducerExtractor(new NativeConnectionSchema(new NativeIntegration()));

  @Test
  void belongsToNativeKafkaAndDeclaresAnOptionalThrottle() {
    assertThat(extractor.protocol()).isEqualTo(TransportProtocol.KAFKA);
    assertThat(extractor.descriptor().id()).isEqualTo(NativeKafkaProducerExtractor.EXTRACTOR_ID);
    assertThat(extractor.descriptor().fields())
        .extracting(ExtractorField::name, ExtractorField::kind, ExtractorField::required)
        .containsExactly(tuple("producerByteRate", ExtractorFieldKind.TEXT, false));
  }

  /** The broker provisions the producer's credentials; the request only sets its throttle. */
  @Test
  void asksForBrokerProvisionedCredentialsAtTheSuppliedThrottle() throws ConnectionInputException {
    var material = extractor.extract(input("2048"));

    assertThat(material.security()).isEqualTo(new ScramSecurityMaterial(2048L));
  }

  @Test
  void leavesTheThrottleToTheBrokerWhenNoneIsSupplied() throws ConnectionInputException {
    var material = extractor.extract(new DeclaredConnectionInput(List.of(), List.of()));

    assertThat(material.security()).isEqualTo(new ScramSecurityMaterial(null));
  }

  @Test
  void rejectsAThrottleThatIsNotAWholePositiveNumber() {
    assertThatThrownBy(() -> extractor.validate(input("0")))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception ->
                assertThat(exception.providerDetailCode())
                    .contains(KafkaProducerByteRatePolicy.INVALID_RATE));
  }

  private static DeclaredConnectionInput input(String producerByteRate) {
    return new DeclaredConnectionInput(
        List.of(new ConnectionInputValue("producerByteRate", producerByteRate)), List.of());
  }
}

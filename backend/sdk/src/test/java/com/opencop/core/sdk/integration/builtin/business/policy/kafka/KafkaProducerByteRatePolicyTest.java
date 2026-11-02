// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaProducerByteRatePolicyTest {

  @Test
  void readsAWholePositiveRate() throws ConnectionInputException {
    assertThat(KafkaProducerByteRatePolicy.producerByteRate(" 1048576 ")).isEqualTo(1_048_576L);
  }

  /** Without a rate the broker's default throttle applies. */
  @Test
  void leavesTheRateToTheBrokerWhenNoneIsSupplied() throws ConnectionInputException {
    assertThat(KafkaProducerByteRatePolicy.producerByteRate(null)).isNull();
    assertThat(KafkaProducerByteRatePolicy.producerByteRate(" ")).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1.5", "fast", "99999999999999999999"})
  void rejectsARateThatIsNotAWholePositiveNumber(String rate) {
    assertThatThrownBy(() -> KafkaProducerByteRatePolicy.producerByteRate(rate))
        .isInstanceOfSatisfying(
            ConnectionInputException.class,
            exception -> {
              assertThat(exception.providerDetailCode())
                  .contains(KafkaProducerByteRatePolicy.INVALID_RATE);
              assertThat(exception.field())
                  .contains(KafkaProducerByteRatePolicy.PRODUCER_BYTE_RATE_FIELD);
            });
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WatchboxAlertPropertiesTest {

  @Test
  void defaultsMatchTheRuntimeContract() {
    var properties = new WatchboxAlertProperties();

    assertThat(properties.getDrain().getBatchSize()).isEqualTo(128);
    assertThat(properties.getDrain().getPollInterval()).isEqualTo(Duration.ofSeconds(5));
    assertThat(validate(properties)).isEmpty();
  }

  @Test
  void rejectsNonPositiveDrainValues() {
    var properties = new WatchboxAlertProperties();
    properties.getDrain().setBatchSize(0);
    properties.getDrain().setPollInterval(Duration.ZERO);

    assertThat(validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("drain.batchSize", "drain.pollIntervalPositive");
  }

  private static jakarta.validation.ConstraintViolation<WatchboxAlertProperties>[] validate(
      WatchboxAlertProperties properties) {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(properties);
      @SuppressWarnings("unchecked")
      var result = violations.toArray(jakarta.validation.ConstraintViolation[]::new);
      return result;
    }
  }
}

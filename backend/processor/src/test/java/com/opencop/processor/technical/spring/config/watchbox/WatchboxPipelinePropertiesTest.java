// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WatchboxPipelinePropertiesTest {

  @Test
  void defaultsMatchTheRuntimeContract() {
    var properties = new WatchboxPipelineProperties();

    assertThat(properties.getCacheTtl()).isEqualTo(Duration.ofSeconds(5));
    assertThat(properties.getFutureTolerance()).isEqualTo(Duration.ofMinutes(5));
    assertThat(validate(properties)).isEmpty();
  }

  @Test
  void rejectsNonPositiveDurationsAndCacheTtlAboveOneMinute() {
    var properties = new WatchboxPipelineProperties();
    properties.setCacheTtl(Duration.ofMinutes(2));
    properties.setFutureTolerance(Duration.ZERO);

    assertThat(validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("durationsValid");
  }

  private static jakarta.validation.ConstraintViolation<WatchboxPipelineProperties>[] validate(
      WatchboxPipelineProperties properties) {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(properties);
      @SuppressWarnings("unchecked")
      var result = violations.toArray(jakarta.validation.ConstraintViolation[]::new);
      return result;
    }
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WatchboxSourceStatusPropertiesTest {

  @Test
  void defaultsMatchTheRuntimeContract() {
    var properties = new WatchboxSourceStatusProperties();

    assertThat(properties.getGc().isEnabled()).isTrue();
    assertThat(properties.getGc().getInterval()).isEqualTo(Duration.ofHours(1));
    assertThat(properties.getGc().getInitialDelay()).isEqualTo(Duration.ofMinutes(15));
    assertThat(properties.getGc().getBatchSize()).isEqualTo(5_000);
    assertThat(properties.getGc().getMaxBatchesPerTick()).isEqualTo(20);
    assertThat(properties.getGc().getRetention()).isEqualTo(Duration.ofHours(72));
    assertThat(validate(properties)).isEmpty();
  }

  @Test
  void rejectsNonPositiveGcValues() {
    var properties = new WatchboxSourceStatusProperties();
    properties.getGc().setInterval(Duration.ZERO);
    properties.getGc().setInitialDelay(Duration.ofSeconds(-1));
    properties.getGc().setBatchSize(0);
    properties.getGc().setMaxBatchesPerTick(0);
    properties.getGc().setRetention(Duration.ZERO);

    assertThat(validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains(
            "gc.durationsPositive", "gc.batchSize", "gc.maxBatchesPerTick", "gc.retentionPositive");
  }

  private static jakarta.validation.ConstraintViolation<WatchboxSourceStatusProperties>[] validate(
      WatchboxSourceStatusProperties properties) {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(properties);
      @SuppressWarnings("unchecked")
      var result = violations.toArray(jakarta.validation.ConstraintViolation[]::new);
      return result;
    }
  }
}

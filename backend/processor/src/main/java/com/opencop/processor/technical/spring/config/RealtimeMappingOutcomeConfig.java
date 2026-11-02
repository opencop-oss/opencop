// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import com.opencop.processor.business.policy.mapping.RealtimeMappingOutcomeClassifier;
import com.opencop.processor.technical.observability.mapping.GlobalMappingWarningLimiter;
import com.opencop.processor.technical.observability.mapping.MappingObservationFailureReporter;
import com.opencop.processor.technical.observability.mapping.ProblemResourceTagLimiter;
import com.opencop.processor.technical.observability.mapping.RealtimeMappingOutcomeObservability;
import com.opencop.processor.technical.observability.mapping.RealtimeMappingProblemLogLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RealtimeMappingOutcomeProperties.class)
public class RealtimeMappingOutcomeConfig {

  @Bean
  RealtimeMappingOutcomeClassifier realtimeMappingOutcomeClassifier() {
    return new RealtimeMappingOutcomeClassifier();
  }

  @Bean
  ProblemResourceTagLimiter problemResourceTagLimiter(RealtimeMappingOutcomeProperties properties) {
    return new ProblemResourceTagLimiter(properties.getMaxProblemResourceTags());
  }

  @Bean
  RealtimeMappingProblemLogLimiter realtimeMappingProblemLogLimiter(
      RealtimeMappingOutcomeProperties properties, GlobalMappingWarningLimiter globalLimiter) {
    return new RealtimeMappingProblemLogLimiter(
        properties.getWarningStateMax(), properties.getWarningSummaryInterval(), globalLimiter);
  }

  @Bean
  GlobalMappingWarningLimiter globalMappingWarningLimiter(
      RealtimeMappingOutcomeProperties properties) {
    return new GlobalMappingWarningLimiter(
        properties.getWarningGlobalMaxPerInterval(), properties.getWarningSummaryInterval());
  }

  @Bean
  MappingObservationFailureReporter mappingObservationFailureReporter(
      RealtimeMappingOutcomeProperties properties) {
    return new MappingObservationFailureReporter(properties.getWarningSummaryInterval());
  }

  @Bean
  RealtimeMappingOutcomeObservability realtimeMappingOutcomeObservability(
      MeterRegistry meterRegistry,
      RealtimeMappingOutcomeProperties properties,
      RealtimeMappingOutcomeClassifier classifier,
      ProblemResourceTagLimiter resourceTagLimiter,
      RealtimeMappingProblemLogLimiter problemLogLimiter,
      MappingObservationFailureReporter failureReporter) {
    return new RealtimeMappingOutcomeObservability(
        meterRegistry,
        properties,
        classifier,
        resourceTagLimiter,
        problemLogLimiter,
        failureReporter);
  }
}

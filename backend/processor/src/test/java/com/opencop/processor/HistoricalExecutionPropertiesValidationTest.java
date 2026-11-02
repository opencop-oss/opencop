// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Spring-context tests that confirm {@link HistoricalExecutionProperties} validation annotations
 * are actually wired through the {@code @ConfigurationProperties} + {@code @Validated}
 * post-processor.
 */
class HistoricalExecutionPropertiesValidationTest {

  private static final int EXPECTED_DEFAULT_CONSECUTIVE = 5;
  private static final int EXPECTED_CODE_DEFAULT_WORKER_POOL_SIZE = 8;
  private static final int EXPECTED_APPLICATION_DEFAULT_WORKER_POOL_SIZE = 8;
  private static final int EXPECTED_DEFAULT_CONCURRENT_SUBJECTS = 2;
  private static final double EXPECTED_DEFAULT_RATIO = 0.10;
  private static final Duration EXPECTED_DEFAULT_JOB_STATE_TTL = Duration.ofHours(24);
  private static final Duration EXPECTED_DEFAULT_ADMISSION_TTL = Duration.ofMinutes(45);
  private static final Duration EXPECTED_DEFAULT_ACTIVE_SNAPSHOT_QUERY_TIMEOUT =
      Duration.ofMinutes(5);
  private static final double DOUBLE_TOLERANCE = 1e-9;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);
  private final ApplicationContextRunner applicationYamlContextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(TestConfig.class);

  @Test
  void rejectsNegativeRatio() {
    contextRunner
        .withPropertyValues("opencop.history.execution.max-failure-ratio=-0.5")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("maxFailureRatio"));
  }

  @Test
  void acceptsDefaults() {
    contextRunner.run(
        context -> {
          HistoricalExecutionProperties properties =
              context.getBean(HistoricalExecutionProperties.class);
          assertThat(properties.getMaxConsecutiveChunkFailures())
              .isEqualTo(EXPECTED_DEFAULT_CONSECUTIVE);
          assertThat(properties.getWorkerPoolSize())
              .isEqualTo(EXPECTED_CODE_DEFAULT_WORKER_POOL_SIZE);
          assertThat(properties.getMaxConcurrentSubjectsPerJob())
              .isEqualTo(EXPECTED_DEFAULT_CONCURRENT_SUBJECTS);
          assertThat(properties.getMaxFailureRatio())
              .isCloseTo(
                  EXPECTED_DEFAULT_RATIO, org.assertj.core.data.Offset.offset(DOUBLE_TOLERANCE));
          assertThat(properties.getJobStateTtl()).isEqualTo(EXPECTED_DEFAULT_JOB_STATE_TTL);
          assertThat(properties.getAdmissionTtl()).isEqualTo(EXPECTED_DEFAULT_ADMISSION_TTL);
          assertThat(properties.getActiveSnapshotQueryTimeout())
              .isEqualTo(EXPECTED_DEFAULT_ACTIVE_SNAPSHOT_QUERY_TIMEOUT);
        });
  }

  @Test
  void rejectsAdmissionTtlThatCannotCoverExecutionWindow() {
    contextRunner
        .withPropertyValues(
            "opencop.history.execution.max-runtime=30m",
            "opencop.history.execution.ttl-refresh-interval=5m",
            "opencop.history.execution.admission-ttl=39m")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("admissionTtl"));
  }

  @Test
  void rejectsActiveSnapshotQueryTimeoutAboveMaxRuntime() {
    contextRunner
        .withPropertyValues(
            "opencop.history.execution.max-runtime=30m",
            "opencop.history.execution.active-snapshot-query-timeout=31m")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("activeSnapshotQueryTimeout"));
  }

  @Test
  void rejectsMaxRuntimeBelowDefaultActiveSnapshotQueryTimeout() {
    contextRunner
        .withPropertyValues("opencop.history.execution.max-runtime=2m")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("activeSnapshotQueryTimeout"));
  }

  @Test
  void rejectsNonPositiveActiveSnapshotQueryTimeout() {
    contextRunner
        .withPropertyValues("opencop.history.execution.active-snapshot-query-timeout=0s")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("activeSnapshotQueryTimeout"));
  }

  @Test
  void rejectsActiveSnapshotQueryTimeoutOutsideJdbcRange() {
    contextRunner
        .withPropertyValues(
            "opencop.history.execution.max-runtime=3000000000s",
            "opencop.history.execution.active-snapshot-query-timeout=3000000000s")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("activeSnapshotQueryTimeout"));
  }

  @Test
  void rejectsConcurrentSubjectsPerJobBelowOne() {
    contextRunner
        .withPropertyValues("opencop.history.execution.max-concurrent-subjects-per-job=0")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("maxConcurrentSubjectsPerJob"));
  }

  @Test
  void rejectsWorkerPoolSizeBelowConcurrentSubjects() {
    contextRunner
        .withPropertyValues(
            "opencop.history.execution.worker-pool-size=1",
            "opencop.history.execution.max-concurrent-subjects-per-job=2")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("workerPoolSize"));
  }

  @Test
  void acceptsWorkerPoolSizeEqualToConcurrentSubjects() {
    contextRunner
        .withPropertyValues(
            "opencop.history.execution.worker-pool-size=2",
            "opencop.history.execution.max-concurrent-subjects-per-job=2")
        .run(
            context ->
                assertThat(context.getBean(HistoricalExecutionProperties.class).getWorkerPoolSize())
                    .isEqualTo(2));
  }

  @Test
  void applicationYamlBindsWorkerPoolDefaultOfEight() {
    applicationYamlContextRunner.run(
        context -> {
          HistoricalExecutionProperties properties =
              context.getBean(HistoricalExecutionProperties.class);
          assertThat(
                  context
                      .getEnvironment()
                      .getProperty("opencop.history.execution.worker-pool-size"))
              .isEqualTo(Integer.toString(EXPECTED_APPLICATION_DEFAULT_WORKER_POOL_SIZE));
          assertThat(properties.getWorkerPoolSize())
              .isEqualTo(EXPECTED_APPLICATION_DEFAULT_WORKER_POOL_SIZE);
          assertThat(properties.getAdmissionTtl()).isEqualTo(EXPECTED_DEFAULT_ADMISSION_TTL);
          assertThat(properties.getActiveSnapshotQueryTimeout())
              .isEqualTo(EXPECTED_DEFAULT_ACTIVE_SNAPSHOT_QUERY_TIMEOUT);
          assertThat(
                  context.getEnvironment().getProperty("opencop.history.execution.admission-ttl"))
              .isEqualTo("45m");
          assertThat(
                  context
                      .getEnvironment()
                      .getProperty("opencop.history.execution.active-snapshot-query-timeout"))
              .isEqualTo("5m");
        });
  }

  @Test
  void rejectsRatioAboveOne() {
    // @DecimalMax("1.0") on maxFailureRatio must reject values greater than 1.0
    contextRunner
        .withPropertyValues("opencop.history.execution.max-failure-ratio=1.1")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("maxFailureRatio"));
    // Positive control: ratio exactly at the boundary (1.0) must be accepted
    contextRunner
        .withPropertyValues("opencop.history.execution.max-failure-ratio=1.0")
        .run(
            context ->
                assertThat(
                        context.getBean(HistoricalExecutionProperties.class).getMaxFailureRatio())
                    .isCloseTo(1.0, org.assertj.core.data.Offset.offset(DOUBLE_TOLERANCE)));
  }

  @Test
  void rejectsNegativeConsecutiveFailures() {
    // @Min(0) on maxConsecutiveChunkFailures must reject negative values
    contextRunner
        .withPropertyValues("opencop.history.execution.max-consecutive-chunk-failures=-1")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(HistoricalExecutionProperties.class))
                    .hasStackTraceContaining("maxConsecutiveChunkFailures"));
    // Positive control: value of 0 (boundary) must be accepted
    contextRunner
        .withPropertyValues("opencop.history.execution.max-consecutive-chunk-failures=0")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(HistoricalExecutionProperties.class)
                            .getMaxConsecutiveChunkFailures())
                    .isEqualTo(0));
  }

  @Configuration
  @EnableConfigurationProperties(HistoricalExecutionProperties.class)
  static class TestConfig {}
}

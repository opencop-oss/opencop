// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisGeoDirectoryRepair;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class GeoDirectoryRepairConfigurationTest {
  private final ch.qos.logback.classic.Logger logger =
      (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GeoDirectoryRepairJob.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void captureJobLogs() {
    appender.list = new CopyOnWriteArrayList<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void stopCapturingJobLogs() {
    logger.detachAppender(appender);
    appender.stop();
  }

  private ApplicationContextRunner context(StringRedisTemplate redis) {
    var factory = mock(LettuceConnectionFactory.class);
    when(factory.isClusterAware()).thenReturn(true);
    return new ApplicationContextRunner()
        .withUserConfiguration(GeoDirectoryRepairConfiguration.class)
        .withBean(LettuceConnectionFactory.class, () -> factory)
        .withBean(StringRedisTemplate.class, () -> redis)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
  }

  @Test
  void configuration_standalone_createsNoRepairOrLeader() {
    context(mock(StringRedisTemplate.class))
        .withPropertyValues("redis.mode=STANDALONE")
        .run(
            c -> {
              assertThat(c)
                  .hasNotFailed()
                  .doesNotHaveBean(GeoDirectoryRepairJob.class)
                  .doesNotHaveBean(WebSocketStartupLeader.class)
                  .doesNotHaveBean(ThreadPoolTaskScheduler.class);
            });
  }

  @Test
  void configuration_disabledCron_validatesPropertiesButSchedulesNothing() {
    var redis = mock(StringRedisTemplate.class);
    context(redis)
        .withPropertyValues("redis.mode=CLUSTER", "opencop.geo.directory-repair.cron=-")
        .run(
            c -> {
              assertThat(c).hasNotFailed().hasSingleBean(GeoDirectoryRepairJob.class);
              assertThat(
                      c.getBean(ThreadPoolTaskScheduler.class)
                          .getScheduledThreadPoolExecutor()
                          .getQueue())
                  .isEmpty();
              assertThat(c.getBean(GeoDirectoryRepairProperties.class).zone())
                  .isEqualTo("America/New_York");
              verify(redis, never()).opsForValue();
              assertThat(appender.list)
                  .extracting(ILoggingEvent::getFormattedMessage)
                  .containsExactly("Geo directory repair scheduling: status=DISABLED");
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void configuration_nonLeader_schedulesNothing_andElectionFailureFailsOpen() {
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock();
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
        .thenReturn(false);
    var runner =
        context(redis)
            .withPropertyValues(
                "redis.mode=CLUSTER", "opencop.websocket.startup-leader.enabled=true");
    runner.run(
        c -> {
          assertThat(c).hasNotFailed();
          assertThat(c.getBean(WebSocketStartupLeader.class).isLeader()).isFalse();
          assertThat(
                  c.getBean(ThreadPoolTaskScheduler.class)
                      .getScheduledThreadPoolExecutor()
                      .getQueue())
              .isEmpty();
          assertThat(appender.list)
              .extracting(ILoggingEvent::getFormattedMessage)
              .containsExactly("Geo directory repair scheduling: status=NOT_LEADER");
        });
    appender.list.clear();
    when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
        .thenThrow(new IllegalStateException("sensitive value"));
    runner.run(
        c -> {
          assertThat(c).hasNotFailed();
          assertThat(c.getBean(WebSocketStartupLeader.class).isLeader()).isTrue();
          c.getBean(GeoDirectoryRepairJob.class).start();
          assertThat(
                  c.getBean(ThreadPoolTaskScheduler.class)
                      .getScheduledThreadPoolExecutor()
                      .getQueue())
              .hasSize(1);
          assertThat(appender.list)
              .extracting(ILoggingEvent::getFormattedMessage)
              .containsExactly("Geo directory repair scheduling: status=SCHEDULED");
        });
  }

  @Test
  void configuration_invalidSettings_failStartup() {
    for (var property :
        List.of(
            "cron=invalid",
            "zone=invalid",
            "scan-count=0",
            "scan-pause=-1ms",
            "registration-batch-size=0")) {
      context(mock(StringRedisTemplate.class))
          .withPropertyValues("redis.mode=CLUSTER", "opencop.geo.directory-repair." + property)
          .run(c -> assertThat(c).hasFailed());
    }
  }

  @Test
  void run_redisFailures_logsOnlyTypeAndAllowsLaterSuccess() throws Exception {
    var sensitive = "opencop:event:geo:{private-source}:event:private-object private-value";
    for (var failure :
        List.of(
            new QueryTimeoutException(sensitive),
            new DataAccessResourceFailureException(
                sensitive, new IllegalStateException("private-cause")))) {
      var repair = mock(RedisGeoDirectoryRepair.class);
      when(repair.repair(anyInt(), anyInt(), any(), any()))
          .thenThrow(failure)
          .thenReturn(RedisGeoDirectoryRepair.Outcome.COMPLETE);
      appender.list.clear();
      var metrics = new SimpleMeterRegistry();
      try {
        var job =
            new GeoDirectoryRepairJob(
                repair,
                new GeoDirectoryRepairProperties(
                    "-", "America/New_York", 1_000, java.time.Duration.ofMillis(10), 500),
                mock(WebSocketStartupLeader.class),
                mock(org.springframework.scheduling.TaskScheduler.class),
                metrics);
        job.run();
        var warnings =
            appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().getFormattedMessage())
            .contains(
                "outcome=FAILED",
                "stage=INITIALIZING",
                "failureType=" + failure.getClass().getName(),
                "scans=0, restored=0, malformed=0");
        assertThat(
                metrics
                    .get("opencop_geo_directory_repair_duration_seconds")
                    .tag("outcome", "FAILED")
                    .timer()
                    .count())
            .isEqualTo(1);
        var lastSuccess =
            metrics.get("opencop_geo_directory_repair_last_success_timestamp_seconds").gauge();
        assertThat(lastSuccess.value()).isZero();

        job.run();

        assertThat(lastSuccess.value()).isPositive();
        assertThat(
                metrics
                    .get("opencop_geo_directory_repair_duration_seconds")
                    .tag("outcome", "COMPLETE")
                    .timer()
                    .count())
            .isEqualTo(1);
        assertThat(appender.list)
            .anySatisfy(
                event ->
                    assertThat(event.getFormattedMessage())
                        .startsWith("Geo directory repair complete:"));
        assertThat(appender.list)
            .allSatisfy(
                event -> {
                  assertThat(event.getFormattedMessage()).doesNotContain("private-", sensitive);
                  assertThat(event.getThrowableProxy()).isNull();
                });
      } finally {
        metrics.close();
      }
    }
  }

  @Test
  void productionScheduler_blockedRepair_doesNotOverlapOrBlockHeartbeat_andShutdownInterrupts()
      throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var interrupted = new java.util.concurrent.CountDownLatch(1);
    var active = new java.util.concurrent.atomic.AtomicInteger();
    var maximum = new java.util.concurrent.atomic.AtomicInteger();
    var controlled = mock(RedisGeoDirectoryRepair.class);
    when(controlled.repair(anyInt(), anyInt(), any(), any()))
        .thenAnswer(
            invocation -> {
              maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
              entered.countDown();
              try {
                new java.util.concurrent.CountDownLatch(1).await();
                throw new AssertionError("Expected cancellation");
              } catch (InterruptedException exception) {
                interrupted.countDown();
                throw exception;
              } finally {
                active.decrementAndGet();
              }
            });
    var closingAt = new java.util.concurrent.atomic.AtomicLong();
    context(mock(StringRedisTemplate.class))
        .withUserConfiguration(
            com.opencop.websocket.technical.spring.config.WebSocketSchedulers.class)
        .withPropertyValues("redis.mode=CLUSTER", "opencop.geo.directory-repair.cron=*/1 * * * * *")
        .withInitializer(
            c ->
                c.getBeanFactory()
                    .addBeanPostProcessor(
                        new org.springframework.beans.factory.config.BeanPostProcessor() {
                          @Override
                          public Object postProcessAfterInitialization(Object bean, String name) {
                            // Controlled blocking adapter; the production job and scheduler wiring
                            // remain real.
                            return bean instanceof RedisGeoDirectoryRepair ? controlled : bean;
                          }
                        }))
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
              var heartbeat = c.getBean("wsHeartbeatScheduler", ThreadPoolTaskScheduler.class);
              var ticks = new java.util.concurrent.atomic.AtomicInteger();
              var future =
                  heartbeat.scheduleAtFixedRate(
                      ticks::incrementAndGet, java.time.Duration.ofMillis(10));
              org.awaitility.Awaitility.await()
                  .atMost(java.time.Duration.ofSeconds(4))
                  .untilAsserted(() -> assertThat(ticks.get()).isGreaterThan(210));
              assertThat(maximum.get()).isEqualTo(1);
              verify(controlled, times(1)).repair(anyInt(), anyInt(), any(), any());
              assertThat(
                      c.getBean(MeterRegistry.class)
                          .get("opencop_geo_directory_repair_last_success_timestamp_seconds")
                          .gauge()
                          .value())
                  .isZero();
              future.cancel(true);
              closingAt.set(System.nanoTime());
            });
    assertThat(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    assertThat(System.nanoTime() - closingAt.get())
        .isLessThan(java.time.Duration.ofSeconds(2).toNanos());
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisGeoCacheAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(
              context -> {
                // Default-mode assertions must not inherit a developer's runtime Redis selection.
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
              })
          .withConfiguration(
              AutoConfigurations.of(
                  RedisConnectionAutoConfiguration.class,
                  RedisGeoCacheAutoConfiguration.class,
                  MetricsAutoConfiguration.class,
                  SimpleMetricsExportAutoConfiguration.class));

  @Test
  void geoAutoConfiguration_backsOffWithoutBootRedisDataSupport() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(EventGeoCacheKeyLayout.class);
          assertThat(context).doesNotHaveBean(RedisEventGeoSourceDirectory.class);
          assertThat(context).doesNotHaveBean(StringRedisTemplate.class);
          assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
        });
  }

  @Test
  void geoAutoConfiguration_contributesOneDirectoryBesideOneBootOwnedFactoryInStandalone() {
    runner
        .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
        .withPropertyValues("spring.data.redis.host=redis.example.test")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RedisConnectionFactory.class);
              assertThat(context).hasSingleBean(StringRedisTemplate.class);
              assertThat(context).hasSingleBean(RedisEventGeoSourceDirectory.class);
              assertThat(context.getBean(EventGeoCacheKeyLayout.class))
                  .isEqualTo(EventGeoCacheKeyLayout.STANDALONE);
              assertThat(context.getBean(RedisEventGeoSourceDirectory.class).layout())
                  .isEqualTo(EventGeoCacheKeyLayout.STANDALONE);
              assertThat(
                      context
                          .getBean(MeterRegistry.class)
                          .get("opencop_redis_source_directory_removals_total")
                          .counter()
                          .count())
                  .isZero();

              var factory =
                  (LettuceConnectionFactory) context.getBean(RedisConnectionFactory.class);
              assertThat(factory.isClusterAware()).isFalse();
              assertThat(factory.getStandaloneConfiguration().getHostName())
                  .isEqualTo("redis.example.test");
            });
  }

  /**
   * {@link ApplicationContextRunner} does not run {@code EnvironmentPostProcessor}s the way {@link
   * SpringApplication} does, so this cell drives the real production bridge explicitly against the
   * runner's environment. The fully assembled bridge-plus-factory path is proved against real Redis
   * in {@code RedisConnectionModesIT}.
   */
  @Test
  void geoAutoConfiguration_selectsClusterFromTheExistingOpenCopModeBridge() {
    runner
        .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
        .withInitializer(
            context -> {
              TestPropertyValues.of(
                      "redis.mode=CLUSTER", "redis.host=redis.example.test", "redis.port=6379")
                  .applyTo(context);
              new RedisTopologyEnvironmentPostProcessor()
                  .postProcessEnvironment(context.getEnvironment(), new SpringApplication());
            })
        .run(
            context -> {
              assertThat(context).hasSingleBean(RedisConnectionFactory.class);
              assertThat(context).hasSingleBean(RedisEventGeoSourceDirectory.class);
              assertThat(context.getBean(EventGeoCacheKeyLayout.class))
                  .isEqualTo(EventGeoCacheKeyLayout.CLUSTER);
              assertThat(context.getBean(RedisEventGeoSourceDirectory.class).layout())
                  .isEqualTo(EventGeoCacheKeyLayout.CLUSTER);

              var factory =
                  (LettuceConnectionFactory) context.getBean(RedisConnectionFactory.class);
              assertThat(factory.isClusterAware()).isTrue();
            });
  }

  @Test
  void geoAutoConfiguration_withoutApplicationRegistry_failsInsteadOfLosingTelemetry() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisGeoCacheAutoConfiguration.class))
        .withBean(EventGeoCacheKeyLayout.class, () -> EventGeoCacheKeyLayout.STANDALONE)
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(
                      org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
            });
  }

  @Test
  void bothSdkAutoConfigurations_areDiscoverableThroughProductionMetadata() {
    var candidates =
        ImportCandidates.load(
            org.springframework.boot.autoconfigure.AutoConfiguration.class,
            getClass().getClassLoader());

    assertThat(candidates)
        .contains(
            RedisConnectionAutoConfiguration.class.getName(),
            RedisGeoCacheAutoConfiguration.class.getName());
  }
}

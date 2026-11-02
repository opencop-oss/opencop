// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisConnectionModesIT {

  private static final String AUTO_CONFIGURATION_EXCLUSIONS =
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration";

  @Test
  void standaloneMode_usesOneNonClusterFactoryAndRetainsReadWriteParity() {
    var application = new SpringApplication(RedisClusterCandidateContext.ProbeApplication.class);
    application.setLogStartupInfo(false);
    try (var context =
        application.run(
            "--spring.main.web-application-type=none",
            "--spring.main.banner-mode=off",
            "--spring.autoconfigure.exclude=" + AUTO_CONFIGURATION_EXCLUSIONS,
            "--spring.data.redis.host=" + LocalRedisSupport.host(),
            "--spring.data.redis.port=" + LocalRedisSupport.port(),
            "--redis.mode=STANDALONE")) {
      var factory = requireOneLettuceFactory(context);
      assertThat(factory.isClusterAware()).isFalse();
      assertThat(factory.getStandaloneConfiguration().getHostName())
          .isEqualTo(LocalRedisSupport.host());
      assertThat(factory.getStandaloneConfiguration().getPort())
          .isEqualTo(LocalRedisSupport.port());

      assertOneSelectedLayoutAndDirectory(context, EventGeoCacheKeyLayout.STANDALONE);

      var redis = context.getBean(StringRedisTemplate.class);
      var directory = context.getBean(RedisEventGeoSourceDirectory.class);
      var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(UUID.randomUUID());
      var removals =
          context
              .getBean(MeterRegistry.class)
              .get("opencop_redis_source_directory_removals_total")
              .counter();
      var key = "opencop:test:redis-modes:standalone:" + UUID.randomUUID();
      try {
        redis.opsForValue().set(key, "standalone");
        assertThat(redis.opsForValue().get(key)).isEqualTo("standalone");
        assertThat(removals.count()).isZero();
        redis.opsForZSet().add(EventGeoCacheKeys.SOURCE_INDEX, sourceKeys.sourcePrefix(), 0.0d);
        assertThat(directory.unregisterIfEmpty(sourceKeys)).isTrue();
        assertThat(directory.unregisterIfEmpty(sourceKeys)).isFalse();
        assertThat(removals.count()).isEqualTo(1.0d);
        redis.opsForZSet().add(EventGeoCacheKeys.SOURCE_INDEX, sourceKeys.sourcePrefix(), 0.0d);
        assertThat(directory.unregisterIfEmpty(sourceKeys)).isTrue();
        assertThat(removals.count()).isEqualTo(2.0d);
      } finally {
        redis.delete(key);
        redis.opsForZSet().remove(EventGeoCacheKeys.SOURCE_INDEX, sourceKeys.sourcePrefix());
      }
    }
  }

  @Test
  void clusterMode_usesOneSeedToDiscoverAndRouteToNonSeedPrimary() {
    try (var fixture = LocalRedisClusterSupport.start();
        var candidate = RedisClusterCandidateContext.start(fixture)) {
      var key = "opencop:test:redis-modes:cluster:{a}:" + UUID.randomUUID();
      fixture.assertKeyOwnedByNonSeedPrimary(key);
      assertThat(candidate.bean(EventGeoCacheKeyLayout.class))
          .isEqualTo(EventGeoCacheKeyLayout.CLUSTER);
      assertThat(candidate.bean(RedisEventGeoSourceDirectory.class).layout())
          .isEqualTo(EventGeoCacheKeyLayout.CLUSTER);

      var redis = candidate.stringTemplate();
      try {
        redis.opsForValue().set(key, "cluster");
        assertThat(redis.opsForValue().get(key)).isEqualTo("cluster");
      } finally {
        redis.delete(key);
      }
    }
  }

  @Test
  void bootOwnedSentinel_withoutOpenCopMode_usesOneSentinelFactory() {
    var application = new SpringApplication(RedisClusterCandidateContext.ProbeApplication.class);
    application.setLogStartupInfo(false);
    try (var context =
        application.run(
            "--spring.main.web-application-type=none",
            "--spring.main.banner-mode=off",
            "--spring.autoconfigure.exclude=" + AUTO_CONFIGURATION_EXCLUSIONS,
            "--spring.data.redis.sentinel.master=boot-owned-master",
            "--spring.data.redis.sentinel.nodes=sentinel.example.test:26379")) {
      var factory = requireOneLettuceFactory(context);
      assertThat(factory.isRedisSentinelAware()).isTrue();
      assertThat(factory.isClusterAware()).isFalse();
      assertThat(factory.getSentinelConfiguration().getMaster().getName())
          .isEqualTo("boot-owned-master");
      assertOneSelectedLayoutAndDirectory(context, EventGeoCacheKeyLayout.STANDALONE);
    }
  }

  @Test
  void clusterMode_withBootSentinelProperties_rejectsCompetingTopology() {
    var application = new SpringApplication(RedisClusterCandidateContext.ProbeApplication.class);
    application.setLogStartupInfo(false);

    assertThatThrownBy(
            () -> {
              try (var ignored =
                  application.run(
                      "--spring.main.web-application-type=none",
                      "--spring.main.banner-mode=off",
                      "--spring.autoconfigure.exclude=" + AUTO_CONFIGURATION_EXCLUSIONS,
                      "--redis.mode=CLUSTER",
                      "--redis.host=redis.example.test",
                      "--redis.port=6379",
                      "--spring.data.redis.sentinel.master=boot-owned-master",
                      "--spring.data.redis.sentinel.nodes=sentinel.example.test:26379")) {
                // Closing a successfully started context keeps this failure-path test leak-free.
              }
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("redis.mode=CLUSTER")
        .hasMessageContaining("spring.data.redis.sentinel.*");
  }

  private static void assertOneSelectedLayoutAndDirectory(
      ConfigurableApplicationContext context, EventGeoCacheKeyLayout expectedLayout) {
    assertThat(context.getBeansOfType(EventGeoCacheKeyLayout.class)).hasSize(1);
    assertThat(context.getBeansOfType(RedisEventGeoSourceDirectory.class)).hasSize(1);
    assertThat(context.getBean(EventGeoCacheKeyLayout.class)).isEqualTo(expectedLayout);
    assertThat(context.getBean(RedisEventGeoSourceDirectory.class).layout())
        .isEqualTo(expectedLayout);
  }

  private static LettuceConnectionFactory requireOneLettuceFactory(
      ConfigurableApplicationContext context) {
    var factories = context.getBeansOfType(RedisConnectionFactory.class);
    assertThat(factories).hasSize(1);
    assertThat(factories.values().iterator().next()).isInstanceOf(LettuceConnectionFactory.class);
    return (LettuceConnectionFactory) factories.values().iterator().next();
  }
}

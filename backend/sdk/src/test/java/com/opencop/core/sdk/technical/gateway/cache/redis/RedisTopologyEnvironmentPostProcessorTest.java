// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

class RedisTopologyEnvironmentPostProcessorTest {

  private final RedisTopologyEnvironmentPostProcessor processor =
      new RedisTopologyEnvironmentPostProcessor();

  @Test
  void getOrder_runsImmediatelyAfterConfigData() {
    assertEquals(ConfigDataEnvironmentPostProcessor.ORDER + 1, processor.getOrder());
  }

  @Test
  void postProcessEnvironment_clusterMode_bridgesExactValuesWithoutRewritingNativeEndpoint() {
    var environment =
        new MockEnvironment()
            .withProperty("redis.mode", "CLUSTER")
            .withProperty("redis.host", "redis.example.test")
            .withProperty("redis.port", "17100")
            .withProperty("redis.cluster.max-redirects", "9")
            .withProperty("redis.cluster.refresh.adaptive", "false")
            .withProperty("redis.cluster.refresh.dynamic-refresh-sources", "false")
            .withProperty("redis.cluster.refresh.period", "45s")
            .withProperty("spring.data.redis.host", "mapped-by-application-yaml")
            .withProperty("spring.data.redis.port", "6380");

    processor.postProcessEnvironment(environment, new SpringApplication());

    var bridge =
        (MapPropertySource)
            environment
                .getPropertySources()
                .get(RedisTopologyEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    assertAll(
        () ->
            assertEquals(
                List.of("redis.example.test:17100"),
                bridge.getProperty("spring.data.redis.cluster.nodes")),
        () -> assertEquals(9, bridge.getProperty("spring.data.redis.cluster.max-redirects")),
        () ->
            assertEquals(
                false, bridge.getProperty("spring.data.redis.lettuce.cluster.refresh.adaptive")),
        () ->
            assertEquals(
                false,
                bridge.getProperty(
                    "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources")),
        () ->
            assertEquals(
                Duration.ofSeconds(45),
                bridge.getProperty("spring.data.redis.lettuce.cluster.refresh.period")),
        () -> assertFalse(bridge.containsProperty("spring.data.redis.host")),
        () -> assertFalse(bridge.containsProperty("spring.data.redis.port")),
        () ->
            assertEquals(
                "mapped-by-application-yaml", environment.getProperty("spring.data.redis.host")),
        () -> assertEquals("6380", environment.getProperty("spring.data.redis.port")));
  }

  @Test
  void postProcessEnvironment_clusterDefaults_addsFirstPrioritySourceExactlyOnce() {
    var environment = clusterEnvironment();

    processor.postProcessEnvironment(environment, new SpringApplication());
    processor.postProcessEnvironment(environment, new SpringApplication());

    var names =
        StreamSupport.stream(environment.getPropertySources().spliterator(), false)
            .map(source -> source.getName())
            .filter(RedisTopologyEnvironmentPostProcessor.PROPERTY_SOURCE_NAME::equals)
            .toList();
    var bridge =
        (MapPropertySource)
            environment
                .getPropertySources()
                .get(RedisTopologyEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    assertAll(
        () ->
            assertEquals(
                List.of(RedisTopologyEnvironmentPostProcessor.PROPERTY_SOURCE_NAME), names),
        () ->
            assertEquals(
                RedisTopologyEnvironmentPostProcessor.PROPERTY_SOURCE_NAME,
                environment.getPropertySources().iterator().next().getName()),
        () ->
            assertEquals(
                "redis.example.test:17100",
                environment.getProperty("spring.data.redis.cluster.nodes")),
        () -> assertEquals("5", environment.getProperty("spring.data.redis.cluster.max-redirects")),
        () ->
            assertEquals(
                "true",
                environment.getProperty("spring.data.redis.lettuce.cluster.refresh.adaptive")),
        () ->
            assertEquals(
                "true",
                environment.getProperty(
                    "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources")),
        () ->
            assertEquals(
                Duration.ofMinutes(1),
                bridge.getProperty("spring.data.redis.lettuce.cluster.refresh.period")));
  }

  @Test
  void postProcessEnvironment_paddedClusterMode_republishesCanonicalSelector() {
    var environment = clusterEnvironment().withProperty("redis.mode", " cLuStEr ");
    var original = environment.getPropertySources().get("mockProperties");

    processor.postProcessEnvironment(environment, new SpringApplication());

    var bridge =
        (MapPropertySource)
            environment
                .getPropertySources()
                .get(RedisTopologyEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    assertAll(
        () -> assertEquals(" cLuStEr ", original.getProperty("redis.mode")),
        () -> assertEquals("CLUSTER", bridge.getProperty("redis.mode")),
        () -> assertEquals("CLUSTER", environment.getProperty("redis.mode")));
  }

  @Test
  void postProcessEnvironment_missingOrBlankMode_isNoop() {
    for (var environment :
        List.of(
            new MockEnvironment().withProperty("redis.cluster.nodes", "ignored"),
            new MockEnvironment()
                .withProperty("redis.mode", " ")
                .withProperty("redis.cluster.nodes", "ignored"))) {
      processor.postProcessEnvironment(environment, new SpringApplication());

      assertNull(
          environment
              .getPropertySources()
              .get(RedisTopologyEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
    }
  }

  @Test
  void postProcessEnvironment_paddedStandaloneMode_leavesOriginalSelectorAlone() {
    var environment = new MockEnvironment().withProperty("redis.mode", " standalone ");

    processor.postProcessEnvironment(environment, new SpringApplication());

    assertAll(
        () ->
            assertNull(
                environment
                    .getPropertySources()
                    .get(RedisTopologyEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)),
        () -> assertEquals(" standalone ", environment.getProperty("redis.mode")));
  }

  @Test
  void postProcessEnvironment_nativeClusterProperty_rejectsCompetingOwner() {
    for (var property : RedisTopologyEnvironmentPostProcessor.NATIVE_CLUSTER_PROPERTIES) {
      var environment = new MockEnvironment().withProperty(property, "configured");

      var exception =
          assertThrows(
              IllegalStateException.class,
              () -> processor.postProcessEnvironment(environment, new SpringApplication()));

      assertEquals(true, exception.getMessage().contains(property));
    }
  }

  @Test
  void postProcessEnvironment_indexedNativeClusterNode_rejectsCompetingOwner() {
    var environment =
        new MockEnvironment().withProperty("spring.data.redis.cluster.nodes[0]", "native:6379");

    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> processor.postProcessEnvironment(environment, new SpringApplication()));

    assertEquals(true, exception.getMessage().contains("spring.data.redis.cluster.nodes"));
  }

  @Test
  void postProcessEnvironment_clusterModeWithIndexedNativeSentinelNode_rejectsCompetingTopology() {
    var environment =
        clusterEnvironment()
            .withProperty("spring.data.redis.sentinel.nodes[0]", "sentinel.example.test:26379");

    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> processor.postProcessEnvironment(environment, new SpringApplication()));

    assertEquals(true, exception.getMessage().contains("spring.data.redis.sentinel.*"));
  }

  @Test
  void postProcessEnvironment_systemEnvironmentIndexedNativeClusterNode_rejectsCompetingOwner() {
    var environment = clusterEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("SPRING_DATA_REDIS_CLUSTER_NODES_0", "native:6379")));

    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> processor.postProcessEnvironment(environment, new SpringApplication()));

    assertEquals(true, exception.getMessage().contains("spring.data.redis.cluster.nodes"));
  }

  @Test
  void postProcessEnvironment_invalidOpenCopConfiguration_wrapsCause() {
    var environment = clusterEnvironment().withProperty("redis.port", "not-a-port");

    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> processor.postProcessEnvironment(environment, new SpringApplication()));

    assertEquals("Invalid OpenCOP Redis topology configuration", exception.getMessage());
  }

  private static MockEnvironment clusterEnvironment() {
    return new MockEnvironment()
        .withProperty("redis.mode", "CLUSTER")
        .withProperty("redis.host", "redis.example.test")
        .withProperty("redis.port", "17100");
  }
}

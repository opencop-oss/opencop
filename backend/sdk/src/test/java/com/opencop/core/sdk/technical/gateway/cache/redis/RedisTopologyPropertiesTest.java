// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RedisTopologyPropertiesTest {

  @Test
  void bind_missingOrBlankMode_usesStandaloneDefaultsWithoutReadingClusterInputs() {
    for (var environment :
        List.of(
            new MockEnvironment(),
            new MockEnvironment()
                .withProperty("redis.mode", " ")
                .withProperty("redis.host", " ")
                .withProperty("redis.port", "not-a-port")
                .withProperty("redis.cluster.nodes", "unpublished-input-is-ignored"))) {
      var properties = RedisTopologyProperties.bind(environment);

      assertAll(
          () -> assertEquals(RedisTopologyProperties.Mode.STANDALONE, properties.mode()),
          () -> assertEquals(new RedisTopologyProperties.Endpoint("", 0), properties.endpoint()),
          () ->
              assertEquals(
                  new RedisTopologyProperties.Cluster(
                      5,
                      RedisTopologyProperties.TopologyAddressStrategy.DIRECT,
                      new RedisTopologyProperties.Refresh(true, true, Duration.ofSeconds(60))),
                  properties.cluster()));
    }
  }

  @Test
  void bind_clusterMode_trimsEndpointAndBindsTuning() {
    var properties =
        RedisTopologyProperties.bind(
            new MockEnvironment()
                .withProperty("redis.mode", " cluster ")
                .withProperty("redis.host", " cache_name.internal ")
                .withProperty("redis.port", " 17100 ")
                .withProperty("redis.cluster.max-redirects", "9")
                .withProperty("redis.cluster.topology-address-strategy", "CONFIGURED_HOST")
                .withProperty("redis.cluster.refresh.adaptive", "false")
                .withProperty("redis.cluster.refresh.dynamic-refresh-sources", "false")
                .withProperty("redis.cluster.refresh.period", "45s")
                .withProperty("redis.cluster.nodes", "unpublished-input-is-ignored"));
    var defaultProperties = RedisTopologyProperties.bind(clusterEnvironment());

    assertAll(
        () -> assertEquals(RedisTopologyProperties.Mode.CLUSTER, properties.mode()),
        () -> assertEquals("cache_name.internal", properties.endpoint().host()),
        () -> assertEquals(17_100, properties.endpoint().port()),
        () -> assertEquals("cache_name.internal:17100", properties.endpoint().asNativeSeed()),
        () -> assertEquals(9, properties.cluster().maxRedirects()),
        () ->
            assertEquals(
                RedisTopologyProperties.TopologyAddressStrategy.CONFIGURED_HOST,
                properties.cluster().topologyAddressStrategy()),
        () ->
            assertEquals(
                RedisTopologyProperties.TopologyAddressStrategy.DIRECT,
                defaultProperties.cluster().topologyAddressStrategy()),
        () -> assertEquals(false, properties.cluster().refresh().adaptive()),
        () -> assertEquals(false, properties.cluster().refresh().dynamicRefreshSources()),
        () -> assertEquals(Duration.ofSeconds(45), properties.cluster().refresh().period()));
  }

  @Test
  void bind_nonNumericClusterPort_preservesParsingCause() {
    var exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RedisTopologyProperties.bind(
                    clusterEnvironment().withProperty("redis.port", "not-a-port")));

    assertInstanceOf(NumberFormatException.class, exception.getCause());
  }

  @Test
  void bind_invalidModeOrClusterValues_rejectsConfiguration() {
    var invalidEnvironments =
        List.of(
            new MockEnvironment().withProperty("redis.mode", "sentinel"),
            clusterEnvironment().withProperty("redis.host", " "),
            clusterEnvironment().withProperty("redis.port", "0"),
            clusterEnvironment().withProperty("redis.port", "65536"),
            clusterEnvironment().withProperty("redis.cluster.max-redirects", "0"),
            clusterEnvironment()
                .withProperty("redis.cluster.topology-address-strategy", "provider-default"),
            clusterEnvironment().withProperty("redis.cluster.refresh.period", "0s"));

    for (var environment : invalidEnvironments) {
      assertThrows(RuntimeException.class, () -> RedisTopologyProperties.bind(environment));
    }
  }

  private static MockEnvironment clusterEnvironment() {
    return new MockEnvironment()
        .withProperty("redis.mode", "CLUSTER")
        .withProperty("redis.host", "redis.example.test")
        .withProperty("redis.port", "17100");
  }
}

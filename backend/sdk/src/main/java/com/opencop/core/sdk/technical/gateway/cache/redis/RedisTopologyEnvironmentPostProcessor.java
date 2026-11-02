// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Bridges the OpenCOP topology contract before Redis auto-configuration selects a client. */
public final class RedisTopologyEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  static final String PROPERTY_SOURCE_NAME = "opencopRedisTopology";
  static final List<String> NATIVE_CLUSTER_PROPERTIES =
      List.of(
          "spring.data.redis.cluster.nodes",
          "spring.data.redis.cluster.max-redirects",
          "spring.data.redis.lettuce.cluster.refresh.adaptive",
          "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources",
          "spring.data.redis.lettuce.cluster.refresh.period");
  static final String NATIVE_SENTINEL_PREFIX = "spring.data.redis.sentinel";

  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }

  @Override
  public void postProcessEnvironment(
      @NonNull ConfigurableEnvironment environment, @NonNull SpringApplication application) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }

    var conflicts =
        NATIVE_CLUSTER_PROPERTIES.stream()
            .filter(property -> containsPropertyOrIndexedValue(environment, property))
            .toList();
    if (!conflicts.isEmpty()) {
      throw new IllegalStateException(
          "Configure Redis clustering with redis.mode and redis.cluster.* only; competing Spring Boot properties were supplied: "
              + String.join(", ", conflicts));
    }

    final RedisTopologyProperties properties;
    try {
      properties = RedisTopologyProperties.bind(environment);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Invalid OpenCOP Redis topology configuration", exception);
    }
    if (properties.mode() == RedisTopologyProperties.Mode.STANDALONE) {
      return;
    }

    if (containsPropertyOrIndexedValue(environment, NATIVE_SENTINEL_PREFIX)) {
      throw new IllegalStateException(
          "redis.mode=CLUSTER cannot be combined with Spring Boot Sentinel properties under spring.data.redis.sentinel.*");
    }

    var cluster = properties.cluster();
    Map<String, Object> bridge = new LinkedHashMap<>();
    // Parse once before bean conditions run, then republish the selected mode at this bridge's
    // highest priority. Later @ConditionalOnProperty gates must see the same Cluster choice as
    // Boot's Redis connection, even for padded or mixed-case inputs accepted by Mode.parse.
    bridge.put("redis.mode", properties.mode().name());
    bridge.put("spring.data.redis.cluster.nodes", List.of(properties.endpoint().asNativeSeed()));
    bridge.put("spring.data.redis.cluster.max-redirects", cluster.maxRedirects());
    bridge.put("spring.data.redis.lettuce.cluster.refresh.adaptive", cluster.refresh().adaptive());
    bridge.put(
        "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources",
        cluster.refresh().dynamicRefreshSources());
    bridge.put("spring.data.redis.lettuce.cluster.refresh.period", cluster.refresh().period());
    environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, bridge));
  }

  private static boolean containsPropertyOrIndexedValue(
      ConfigurableEnvironment environment, String property) {
    if (environment.containsProperty(property)) {
      return true;
    }
    var propertyName = ConfigurationPropertyName.of(property);
    for (var source : ConfigurationPropertySources.get(environment)) {
      if (source.containsDescendantOf(propertyName) == ConfigurationPropertyState.PRESENT) {
        return true;
      }
    }
    return false;
  }
}

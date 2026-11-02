// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** Immutable external connection-topology contract under the {@code redis} prefix. */
record RedisTopologyProperties(Mode mode, Endpoint endpoint, Cluster cluster) {

  RedisTopologyProperties {
    mode = mode == null ? Mode.STANDALONE : mode;
    endpoint = endpoint == null ? Endpoint.empty() : endpoint;
    cluster = cluster == null ? Cluster.defaults() : cluster;
    if (mode == Mode.CLUSTER) endpoint.assertValidClusterEndpoint();
  }

  static RedisTopologyProperties bind(Environment environment) {
    var mode = Mode.parse(environment.getProperty("redis.mode"));
    if (mode == Mode.STANDALONE) {
      return new RedisTopologyProperties(mode, Endpoint.empty(), Cluster.defaults());
    }

    var cluster =
        Binder.get(environment)
            .bind("redis.cluster", Bindable.of(Cluster.class))
            .orElseGet(Cluster::defaults);
    return new RedisTopologyProperties(mode, Endpoint.from(environment), cluster);
  }

  enum Mode {
    STANDALONE,
    CLUSTER;

    static Mode parse(String value) {
      if (!StringUtils.hasText(value)) {
        return STANDALONE;
      }
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("redis.mode must be STANDALONE or CLUSTER", exception);
      }
    }
  }

  enum TopologyAddressStrategy {
    /** Connect to each host and port exactly as the cluster advertises them. */
    DIRECT,

    /** Replace every advertised host with {@code redis.host} while preserving its port. */
    CONFIGURED_HOST
  }

  record Endpoint(String host, int port) {

    private static final String INVALID_CLUSTER_ENDPOINT_MESSAGE =
        "redis.host must be nonblank and redis.port must be an integer from 1 to 65535 when redis.mode=CLUSTER";

    Endpoint {
      host = host == null ? "" : host.trim();
    }

    static Endpoint empty() {
      return new Endpoint("", 0);
    }

    static Endpoint from(Environment environment) {
      var host = environment.getProperty("redis.host");
      var portText = environment.getProperty("redis.port");
      if (!StringUtils.hasText(portText)) {
        return new Endpoint(host, 0);
      }
      try {
        return new Endpoint(host, Integer.parseInt(portText.trim()));
      } catch (NumberFormatException exception) {
        throw invalidEndpoint(exception);
      }
    }

    String asNativeSeed() {
      return host + ":" + port;
    }

    private void assertValidClusterEndpoint() {
      if (!StringUtils.hasText(host) || port < 1 || port > 65_535) {
        throw invalidEndpoint();
      }
    }

    private static IllegalArgumentException invalidEndpoint() {
      return new IllegalArgumentException(INVALID_CLUSTER_ENDPOINT_MESSAGE);
    }

    private static IllegalArgumentException invalidEndpoint(Throwable cause) {
      return new IllegalArgumentException(INVALID_CLUSTER_ENDPOINT_MESSAGE, cause);
    }
  }

  record Cluster(
      @DefaultValue("5") int maxRedirects,
      @DefaultValue("DIRECT") TopologyAddressStrategy topologyAddressStrategy,
      @DefaultValue Refresh refresh) {

    Cluster {
      if (maxRedirects <= 0) {
        throw new IllegalArgumentException("redis.cluster.max-redirects must be positive");
      }
      topologyAddressStrategy =
          topologyAddressStrategy == null
              ? TopologyAddressStrategy.DIRECT
              : topologyAddressStrategy;
      refresh = refresh == null ? Refresh.defaults() : refresh;
    }

    static Cluster defaults() {
      return new Cluster(5, TopologyAddressStrategy.DIRECT, Refresh.defaults());
    }
  }

  record Refresh(
      @DefaultValue("true") boolean adaptive,
      @DefaultValue("true") boolean dynamicRefreshSources,
      @DefaultValue("60s") Duration period) {

    Refresh {
      if (period == null || period.isZero() || period.isNegative()) {
        throw new IllegalArgumentException("redis.cluster.refresh.period must be positive");
      }
    }

    static Refresh defaults() {
      return new Refresh(true, true, Duration.ofSeconds(60));
    }
  }
}

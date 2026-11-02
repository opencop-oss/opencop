// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import java.util.Optional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public final class LocalRedisSupport {

  private static final String HOST_PROPERTY = "opencop.test.redis.host";
  private static final String PORT_PROPERTY = "opencop.test.redis.port";
  private static final String HOST_ENV = "OPENCOP_TEST_REDIS_HOST";
  private static final String PORT_ENV = "OPENCOP_TEST_REDIS_PORT";
  private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.4-alpine");

  private LocalRedisSupport() {}

  public static String host() {
    return configuredEndpoint().map(RedisEndpoint::host).orElseGet(ContainerHolder::host);
  }

  public static int port() {
    return configuredEndpoint().map(RedisEndpoint::port).orElseGet(ContainerHolder::port);
  }

  private static Optional<RedisEndpoint> configuredEndpoint() {
    var host = configuredProperty(HOST_PROPERTY);
    var port = configuredProperty(PORT_PROPERTY);
    if (host.isPresent() || port.isPresent()) {
      return configuredEndpoint(host, port, HOST_PROPERTY + "/" + PORT_PROPERTY);
    }

    host = configuredEnvironment(HOST_ENV);
    port = configuredEnvironment(PORT_ENV);
    if (host.isPresent() || port.isPresent()) {
      return configuredEndpoint(host, port, HOST_ENV + "/" + PORT_ENV);
    }

    return Optional.empty();
  }

  private static Optional<RedisEndpoint> configuredEndpoint(
      Optional<String> host, Optional<String> port, String source) {
    if (host.isPresent() != port.isPresent()) {
      throw new IllegalStateException(
          "Configure both " + source + " for external Redis component tests");
    }
    return host.map(value -> new RedisEndpoint(value, parsePort(port.orElseThrow())));
  }

  private static Optional<String> configuredProperty(String property) {
    var value = System.getProperty(property);
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
  }

  private static Optional<String> configuredEnvironment(String environmentVariable) {
    var value = System.getenv(environmentVariable);
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
  }

  private static int parsePort(String value) {
    try {
      var port = Integer.parseInt(value);
      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("Configured Redis port is outside the valid range");
      }
      return port;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Configured Redis port is not an integer: " + value, e);
    }
  }

  private record RedisEndpoint(String host, int port) {}

  private static final class ContainerHolder {
    private static final GenericContainer<?> REDIS = startRedis();

    private ContainerHolder() {}

    private static GenericContainer<?> startRedis() {
      var redis = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
      redis.start();
      return redis;
    }

    private static String host() {
      return REDIS.getHost();
    }

    private static int port() {
      return REDIS.getMappedPort(6379);
    }
  }
}

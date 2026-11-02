// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.redis;

import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/** Production-metadata Redis Cluster context shared by focused component characterizations. */
public final class RedisClusterCandidateContext implements AutoCloseable {

  private static final String AUTO_CONFIGURATION_EXCLUSIONS =
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration";

  private final ConfigurableApplicationContext context;
  private final RedisTemplate<String, byte[]> byteTemplate;
  private final StringRedisTemplate stringTemplate;

  private RedisClusterCandidateContext(
      ConfigurableApplicationContext context, LocalRedisClusterSupport.Fixture fixture) {
    this.context = context;
    var connectionFactory = requireClusterFactory(context, fixture);
    assertPing(connectionFactory);
    this.byteTemplate = byteTemplate(connectionFactory);
    this.stringTemplate = context.getBean(StringRedisTemplate.class);
  }

  public static RedisClusterCandidateContext start(LocalRedisClusterSupport.Fixture fixture) {
    ConfigurableApplicationContext context = null;
    try {
      var application = new SpringApplication(ProbeApplication.class);
      application.setLogStartupInfo(false);
      context =
          application.run(
              "--spring.main.web-application-type=none",
              "--spring.main.banner-mode=off",
              "--spring.autoconfigure.exclude=" + AUTO_CONFIGURATION_EXCLUSIONS,
              "--redis.mode=CLUSTER",
              "--redis.host=" + fixture.seedHost(),
              "--redis.port=" + fixture.seedPort(),
              "--redis.ssl=false");
      return new RedisClusterCandidateContext(context, fixture);
    } catch (RuntimeException | Error failure) {
      if (context != null) {
        try {
          context.close();
        } catch (RuntimeException | Error closeFailure) {
          failure.addSuppressed(closeFailure);
        }
      }
      throw failure;
    }
  }

  public RedisTemplate<String, byte[]> byteTemplate() {
    return byteTemplate;
  }

  public StringRedisTemplate stringTemplate() {
    return stringTemplate;
  }

  /** Narrow typed accessor for one bean of the production-metadata candidate context. */
  public <T> T bean(Class<T> requiredType) {
    return context.getBean(requiredType);
  }

  @Override
  public void close() {
    context.close();
  }

  private static LettuceConnectionFactory requireClusterFactory(
      ConfigurableApplicationContext context, LocalRedisClusterSupport.Fixture fixture) {
    var factories = context.getBeansOfType(RedisConnectionFactory.class);
    if (factories.size() != 1) {
      throw new IllegalStateException(
          "Expected one Redis connection factory, found " + factories.keySet());
    }
    var factory = factories.values().iterator().next();
    if (!(factory instanceof LettuceConnectionFactory lettuce) || !lettuce.isClusterAware()) {
      throw new IllegalStateException("Expected one cluster-aware Lettuce connection factory");
    }
    var cluster = lettuce.getClusterConfiguration();
    if (cluster == null) {
      throw new IllegalStateException(
          "Cluster-aware Lettuce factory omitted cluster configuration");
    }
    var configuredSeeds =
        cluster.getClusterNodes().stream()
            .map(node -> node.getHost() + ":" + node.getPort())
            .toList();
    var expectedSeeds = List.of(fixture.seedHost() + ":" + fixture.seedPort());
    if (!configuredSeeds.equals(expectedSeeds)) {
      throw new IllegalStateException(
          "Expected exactly the supplied singleton Redis cluster seed, found " + configuredSeeds);
    }
    return lettuce;
  }

  private static void assertPing(LettuceConnectionFactory connectionFactory) {
    try (var connection = connectionFactory.getConnection()) {
      var response = connection.commands().ping();
      if (!"PONG".equals(response)) {
        throw new IllegalStateException("Redis Cluster PING returned " + response);
      }
    }
  }

  private static RedisTemplate<String, byte[]> byteTemplate(
      LettuceConnectionFactory connectionFactory) {
    var template = new RedisTemplate<String, byte[]>();
    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(RedisSerializer.byteArray());
    template.setHashKeySerializer(RedisSerializer.string());
    template.setHashValueSerializer(RedisSerializer.byteArray());
    template.setConnectionFactory(connectionFactory);
    template.afterPropertiesSet();
    return template;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  public static class ProbeApplication {}
}

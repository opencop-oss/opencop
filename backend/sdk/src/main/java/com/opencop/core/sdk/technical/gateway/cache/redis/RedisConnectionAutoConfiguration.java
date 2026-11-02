// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Shared Redis hardening and pure key-layout selection that retain Boot ownership of the connection
 * factory.
 *
 * <p>This configuration deliberately contributes no template, client resource, or connection
 * factory. {@link EventGeoCacheKeyLayout} is a pure value derived from the same {@code redis.mode}
 * selector that decides topology, so live-geo keys can never disagree with the connection Boot
 * actually creates.
 */
@AutoConfiguration(before = DataRedisAutoConfiguration.class)
@ConditionalOnClass({
  ClientOptions.class,
  LettuceConnectionFactory.class,
  ClientResourcesBuilderCustomizer.class,
  LettuceClientOptionsBuilderCustomizer.class
})
@Import(RedisClientHardeningConfig.class)
public class RedisConnectionAutoConfiguration {

  @Bean
  EventGeoCacheKeyLayout eventGeoCacheKeyLayout(Environment environment) {
    // Reuse the topology bridge's parser: its EnvironmentPostProcessor runs before beans exist.
    // This keeps key selection consistent with connection setup, including standalone defaults
    // and skipping cluster-only settings in standalone mode.
    return switch (RedisTopologyProperties.bind(environment).mode()) {
      case STANDALONE -> EventGeoCacheKeyLayout.STANDALONE;
      case CLUSTER -> EventGeoCacheKeyLayout.CLUSTER;
    };
  }

  @Bean
  HistoricalJobControlKeyLayout historicalJobControlKeyLayout(Environment environment) {
    return switch (RedisTopologyProperties.bind(environment).mode()) {
      case STANDALONE -> HistoricalJobControlKeyLayout.STANDALONE;
      case CLUSTER -> HistoricalJobControlKeyLayout.CLUSTER;
    };
  }
}

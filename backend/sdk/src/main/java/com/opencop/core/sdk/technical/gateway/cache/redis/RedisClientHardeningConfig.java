// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.DnsResolvers;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Redis client network hardening shared by every OpenCOP Redis application. */
@Configuration(proxyBeanMethods = false)
public class RedisClientHardeningConfig {

  @Bean
  LettuceClientOptionsBuilderCustomizer redisLettuceClientOptionsBuilderCustomizer() {
    return RedisClientOptionsFactory::applyHardening;
  }

  @Bean
  ClientResourcesBuilderCustomizer redisClientResourcesBuilderCustomizer(Environment environment) {
    var topology = RedisTopologyProperties.bind(environment);
    return switch (topology.mode()) {
      case STANDALONE -> builder -> {};
      case CLUSTER ->
          switch (topology.cluster().topologyAddressStrategy()) {
            case DIRECT -> builder -> {};
            case CONFIGURED_HOST -> {
              var configuredHost = topology.endpoint().host();
              yield builder ->
                  builder.socketAddressResolver(
                      MappingSocketAddressResolver.create(
                          DnsResolvers.UNRESOLVED,
                          address -> HostAndPort.of(configuredHost, address.getPort())));
            }
          };
    };
  }
}

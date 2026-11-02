// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.SocketAddressResolver;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RedisClientHardeningConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(RedisClientHardeningConfig.class);

  @Test
  void redisLettuceClientOptionsBuilderCustomizer_enablesKeepaliveAndReconnects() {
    contextRunner.run(
        context -> {
          var builder = ClientOptions.builder();

          assertEquals(
              1, context.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class).size());
          context.getBean(LettuceClientOptionsBuilderCustomizer.class).customize(builder);

          var clientOptions = builder.build();
          var socketOptions = clientOptions.getSocketOptions();

          assertTrue(clientOptions.isAutoReconnect());
          assertTrue(clientOptions.isPingBeforeActivateConnection());
          assertTrue(clientOptions.getTimeoutOptions().isTimeoutCommands());
          assertEquals(Duration.ofSeconds(5), socketOptions.getConnectTimeout());
          assertTrue(socketOptions.getKeepAlive().isEnabled());
          assertEquals(Duration.ofSeconds(30), socketOptions.getKeepAlive().getIdle());
          assertEquals(Duration.ofSeconds(10), socketOptions.getKeepAlive().getInterval());
          assertEquals(3, socketOptions.getKeepAlive().getCount());
        });
  }

  @Test
  void redisClientResourcesBuilderCustomizer_standaloneMode_leavesAddressResolutionUnchanged() {
    contextRunner.run(
        context -> {
          var builder = mock(ClientResources.Builder.class);

          assertEquals(1, context.getBeansOfType(ClientResourcesBuilderCustomizer.class).size());
          context.getBean(ClientResourcesBuilderCustomizer.class).customize(builder);

          verifyNoInteractions(builder);
        });
  }

  @Test
  void redisClientResourcesBuilderCustomizer_clusterDirectMode_leavesAddressResolutionUnchanged() {
    contextRunner
        .withPropertyValues(
            "redis.mode=CLUSTER", "redis.host=redis.example.test", "redis.port=6379")
        .run(
            context -> {
              var builder = mock(ClientResources.Builder.class);

              context.getBean(ClientResourcesBuilderCustomizer.class).customize(builder);

              verifyNoInteractions(builder);
            });
  }

  @Test
  void
      redisClientResourcesBuilderCustomizer_configuredHostMode_usesConfiguredHostAndDiscoveredPort() {
    contextRunner
        .withPropertyValues(
            "redis.mode=CLUSTER",
            "redis.host=managed.redis.example.test",
            "redis.port=10000",
            "redis.cluster.topology-address-strategy=CONFIGURED_HOST")
        .run(
            context -> {
              var builder = mock(ClientResources.Builder.class);
              var resolverCaptor = ArgumentCaptor.forClass(SocketAddressResolver.class);

              context.getBean(ClientResourcesBuilderCustomizer.class).customize(builder);

              verify(builder).socketAddressResolver(resolverCaptor.capture());
              var resolvedAddress =
                  assertInstanceOf(
                      InetSocketAddress.class,
                      resolverCaptor
                          .getValue()
                          .resolve(RedisURI.Builder.redis("192.0.2.10", 8500).build()));
              assertEquals("managed.redis.example.test", resolvedAddress.getHostString());
              assertEquals(8500, resolvedAddress.getPort());
              assertTrue(resolvedAddress.isUnresolved());
            });
  }
}

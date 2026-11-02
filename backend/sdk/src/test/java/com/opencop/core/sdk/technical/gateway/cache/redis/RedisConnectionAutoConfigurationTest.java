// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import io.lettuce.core.resource.ClientResources;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class RedisConnectionAutoConfigurationTest {

  @Test
  void
      autoConfiguration_contributesBothCustomizersWithoutCreatingClientResourcesOrConnectionFactory() {
    new ApplicationContextRunner()
        .withInitializer(
            context -> {
              // Default-mode assertions must not inherit a developer's runtime Redis selection.
              var sources = context.getEnvironment().getPropertySources();
              sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
              sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            })
        .withUserConfiguration(RedisConnectionAutoConfiguration.class)
        .run(
            context -> {
              assertEquals(
                  1, context.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class).size());
              assertEquals(
                  1, context.getBeansOfType(ClientResourcesBuilderCustomizer.class).size());
              assertEquals(0, context.getBeanNamesForType(ClientResources.class).length);
              assertEquals(0, context.getBeanNamesForType(RedisConnectionFactory.class).length);
              assertEquals(
                  EventGeoCacheKeyLayout.STANDALONE, context.getBean(EventGeoCacheKeyLayout.class));
              assertEquals(
                  HistoricalJobControlKeyLayout.STANDALONE,
                  context.getBean(HistoricalJobControlKeyLayout.class));
              assertEquals(
                  0, context.getBeanNamesForType(RedisEventGeoSourceDirectory.class).length);
            });
  }

  @Test
  void autoConfiguration_selectsExplicitStandaloneHistoricalControlLayout() {
    new ApplicationContextRunner()
        .withPropertyValues("redis.mode=STANDALONE")
        .withUserConfiguration(RedisConnectionAutoConfiguration.class)
        .run(
            context ->
                assertEquals(
                    HistoricalJobControlKeyLayout.STANDALONE,
                    context.getBean(HistoricalJobControlKeyLayout.class)));
  }

  @Test
  void autoConfiguration_selectsClusterHistoricalControlLayoutFromTheTopologyMode() {
    new ApplicationContextRunner()
        .withPropertyValues("redis.mode=cluster", "redis.host=127.0.0.1", "redis.port=17100")
        .withUserConfiguration(RedisConnectionAutoConfiguration.class)
        .run(
            context ->
                assertEquals(
                    HistoricalJobControlKeyLayout.CLUSTER,
                    context.getBean(HistoricalJobControlKeyLayout.class)));
  }
}

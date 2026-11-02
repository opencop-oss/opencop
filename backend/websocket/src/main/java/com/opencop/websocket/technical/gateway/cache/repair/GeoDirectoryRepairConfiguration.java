// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import com.opencop.core.sdk.technical.gateway.cache.redis.RedisGeoDirectoryRepair;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
// The early Redis topology bridge republishes its parsed Cluster mode before conditions run.
// Repair follows that canonical selector, the same one that creates Boot's Cluster connection.
@ConditionalOnProperty(name = "redis.mode", havingValue = "CLUSTER")
@EnableConfigurationProperties({
  GeoDirectoryRepairProperties.class,
  WebSocketStartupLeaderProperties.class
})
public class GeoDirectoryRepairConfiguration {
  @Bean
  RedisGeoDirectoryRepair redisGeoDirectoryRepair(LettuceConnectionFactory factory) {
    return new RedisGeoDirectoryRepair(factory);
  }

  @Bean
  WebSocketStartupLeader webSocketStartupLeader(
      StringRedisTemplate redis, WebSocketStartupLeaderProperties properties) {
    return new WebSocketStartupLeader(redis, properties);
  }

  @Bean("geoDirectoryRepairScheduler")
  ThreadPoolTaskScheduler geoDirectoryRepairScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("geo-directory-repair-");
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }

  @Bean
  GeoDirectoryRepairJob geoDirectoryRepairJob(
      RedisGeoDirectoryRepair repair,
      GeoDirectoryRepairProperties properties,
      WebSocketStartupLeader leader,
      @Qualifier("geoDirectoryRepairScheduler") ThreadPoolTaskScheduler scheduler,
      MeterRegistry metrics) {
    return new GeoDirectoryRepairJob(repair, properties, leader, scheduler, metrics);
  }
}

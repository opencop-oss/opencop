// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Focused live-geo Redis I/O assembly, ordered after Boot has created its Redis template.
 *
 * <p>It joins the selected {@link EventGeoCacheKeyLayout} from {@link
 * RedisConnectionAutoConfiguration}, Boot's own {@link StringRedisTemplate}, and the application's
 * {@link MeterRegistry}. It creates no connection, template, thread, scheduler, or retry loop, and
 * backs off entirely when an application has deliberately supplied its own directory.
 */
@AutoConfiguration(
    after = {RedisConnectionAutoConfiguration.class, DataRedisAutoConfiguration.class})
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisGeoCacheAutoConfiguration {

  @Bean
  @ConditionalOnBean({EventGeoCacheKeyLayout.class, StringRedisTemplate.class})
  @ConditionalOnMissingBean
  RedisEventGeoSourceDirectory redisEventGeoSourceDirectory(
      EventGeoCacheKeyLayout layout,
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry) {
    return new RedisEventGeoSourceDirectory(layout, redisTemplate, meterRegistry);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.opencop.core.api.general.ports.usecase.media.CachedIcon;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileCaches;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();

    manager.registerCustomCache(
        MapperProfileCaches.BY_SOURCE_ID,
        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(10_000).build());
    manager.registerCustomCache(
        MapperProfileCaches.BY_MAPPER_ID,
        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(10_000).build());
    manager.registerCustomCache(
        MapperProfileCaches.SOURCE_ONLY_WRITES,
        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(10_000).build());

    manager.registerCustomCache(
        "layerIcons",
        Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumWeight(200_000_000)
            .weigher(
                (Object key, Object value) -> {
                  if (value instanceof CachedIcon icon) {
                    return icon.bytes().length;
                  }
                  return 0;
                })
            .build());

    return manager;
  }
}

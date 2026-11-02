// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.opencop.core.sdk.ports.gateway.cache.CacheService;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisService;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileCaches;
import com.opencop.core.sdk.technical.model.mapper.FeatureMapper;
import com.opencop.core.sdk.technical.model.serializer.ProtobufSerializer;
import java.util.concurrent.TimeUnit;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import org.geojson.Feature;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

@EnableCaching
@Configuration
public class CacheConfig {

  @Bean("geoUpsertScript")
  public RedisScript<Long> geoUpsertScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setResultType(Long.class);

    var resource = new ClassPathResource("redis/geo_upsert.lua");
    script.setScriptSource(new ResourceScriptSource(resource));

    return script;
  }

  @Bean("geoEvictScript")
  public RedisScript<Long> geoEvictScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setResultType(Long.class);

    var resource = new ClassPathResource("redis/geo_evict.lua");
    script.setScriptSource(new ResourceScriptSource(resource));

    return script;
  }

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

    return manager;
  }

  @Bean
  public RedisTemplate<String, byte[]> byteRedisTemplate(LettuceConnectionFactory factory) {
    final RedisTemplate<String, byte[]> template = new RedisTemplate<>();

    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(RedisSerializer.byteArray());
    template.setHashKeySerializer(RedisSerializer.string());
    template.setHashValueSerializer(RedisSerializer.byteArray());

    template.setConnectionFactory(factory);

    return template;
  }

  @Bean
  public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }

  @Bean
  public CacheService<Feature> redisService(
      RedisTemplate<String, byte[]> byteRedisTemplate, StringRedisTemplate stringRedisTemplate) {
    return new RedisService<>(
        new ProtobufSerializer<>(new FeatureMapper(), ElementMessage.class),
        byteRedisTemplate,
        stringRedisTemplate);
  }
}

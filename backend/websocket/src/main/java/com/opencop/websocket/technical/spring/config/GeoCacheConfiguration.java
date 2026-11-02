// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import com.opencop.core.sdk.ports.gateway.cache.CacheService;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisService;
import com.opencop.core.sdk.technical.model.mapper.FeatureMapper;
import com.opencop.core.sdk.technical.model.serializer.ProtobufSerializer;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import com.opencop.websocket.technical.gateway.cache.Janitor;
import com.opencop.websocket.technical.gateway.cache.geo.RedisGeoEventReader;
import io.micrometer.core.instrument.MeterRegistry;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class GeoCacheConfiguration {
  @Bean
  Janitor janitor(
      CacheService<Feature> cacheService,
      RedisEventGeoSourceDirectory sourceDirectory,
      @Value("${janitor.pageSize:1000}") int pageSize,
      MeterRegistry meterRegistry) {
    return new Janitor(cacheService, sourceDirectory, pageSize, meterRegistry);
  }

  @Bean
  Serializer<Feature, byte[]> serializer() {
    return new ProtobufSerializer<>(new FeatureMapper(), ElementMessage.class);
  }

  @Bean("redisTemplate")
  RedisTemplate<String, byte[]> redisTemplate(final LettuceConnectionFactory factory) {
    final RedisTemplate<String, byte[]> template = new RedisTemplate<>();

    template.setKeySerializer(RedisSerializer.string());
    template.setValueSerializer(RedisSerializer.byteArray());
    template.setHashKeySerializer(RedisSerializer.string());
    template.setHashValueSerializer(RedisSerializer.byteArray());
    template.setConnectionFactory(factory);
    template.afterPropertiesSet(); // Ensure full initialization

    return template;
  }

  @Bean
  StringRedisTemplate stringRedisTemplate(final LettuceConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }

  @Bean
  GeoEventReader redisGeoEventReader(
      EventGeoCacheKeyLayout layout,
      StringRedisTemplate stringRedisTemplate,
      RedisTemplate<String, byte[]> redisTemplate) {
    return new RedisGeoEventReader(layout, stringRedisTemplate, redisTemplate);
  }

  @Bean
  CacheService<Feature> redisService(
      final Serializer<Feature, byte[]> serializer,
      final RedisTemplate<String, byte[]> redisTemplate,
      final StringRedisTemplate stringRedisTemplate) {
    return new RedisService<>(serializer, redisTemplate, stringRedisTemplate);
  }
}

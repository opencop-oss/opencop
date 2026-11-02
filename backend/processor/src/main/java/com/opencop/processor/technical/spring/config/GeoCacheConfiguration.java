// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.model.mapper.FeatureMapper;
import com.opencop.core.sdk.technical.model.serializer.ProtobufSerializer;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.technical.gateway.cache.GeoCacheWriter;
import opencop.core.proto.deckgl.ElementProtos;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration(proxyBeanMethods = false)
public class GeoCacheConfiguration {
  @Bean
  GeoCacheGateway geoCacheWriter(
      RedisTemplate<String, byte[]> redisTemplate,
      EventGeoCacheKeyLayout keyLayout,
      RedisEventGeoSourceDirectory sourceDirectory,
      RedisScript<Long> geoUpsertScript,
      RedisScript<Long> geoEvictScript,
      CachePublisher<CacheEventsNotification> publisher) {
    return new GeoCacheWriter(
        redisTemplate,
        keyLayout,
        sourceDirectory,
        geoUpsertScript,
        geoEvictScript,
        new ProtobufSerializer<>(new FeatureMapper(), ElementProtos.ElementMessage.class),
        publisher);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.realtime;

import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.core.sdk.technical.model.mapper.FeatureMapper;
import com.opencop.core.sdk.technical.model.serializer.ProtobufSerializer;
import com.opencop.core.sdk.technical.model.serializer.Serializer;
import com.opencop.processor.business.usecase.event.RealTimeGeoFeatureHandler;
import com.opencop.processor.ports.business.usecase.event.RealTimeEventHandler;
import com.opencop.processor.technical.gateway.cache.GeoCacheWriter;
import com.opencop.processor.technical.spring.consumer.RealTimeConsumer;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import opencop.core.proto.deckgl.ElementProtos;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

@TestConfiguration
@Import(RealTimeConsumer.class)
public class RealTimeConsumerComponentTestContext {

  @Bean
  RedisEvalProbe redisEvalProbe() {
    return new RedisEvalProbe();
  }

  @Bean
  RedisTemplate<String, byte[]> redisTemplate(RedisEvalProbe redisEvalProbe) {
    return new RedisTemplate<>() {
      private RedisConnection connection() {
        return (RedisConnection)
            Proxy.newProxyInstance(
                RedisConnection.class.getClassLoader(),
                new Class<?>[] {RedisConnection.class},
                (_, method, _) -> {
                  if ("scriptingCommands".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                        RedisScriptingCommands.class.getClassLoader(),
                        new Class<?>[] {RedisScriptingCommands.class},
                        (_, scriptingMethod, args) -> {
                          if ("scriptLoad".equals(scriptingMethod.getName())) {
                            redisEvalProbe.recordScriptLoad();
                            return "sha-real-time";
                          }
                          if ("evalSha".equals(scriptingMethod.getName())) {
                            redisEvalProbe.recordEval(
                                Objects.toString(args[0]), (Integer) args[2], (byte[][]) args[3]);
                            return 1L;
                          }
                          if (scriptingMethod.getReturnType().equals(Boolean.TYPE)) {
                            return false;
                          }
                          if (scriptingMethod.getReturnType().equals(Long.TYPE)
                              || scriptingMethod.getReturnType().equals(Integer.TYPE)) {
                            return 0;
                          }
                          return null;
                        });
                  }
                  if (method.getReturnType().equals(Boolean.TYPE)) {
                    return false;
                  }
                  if (method.getReturnType().equals(Long.TYPE)
                      || method.getReturnType().equals(Integer.TYPE)) {
                    return 0;
                  }
                  return null;
                });
      }

      @Override
      public <T> T execute(RedisCallback<T> action) {
        return action.doInRedis(connection());
      }

      @Override
      public List<Object> executePipelined(RedisCallback<?> action) {
        action.doInRedis(connection());
        return List.of();
      }

      @Override
      public void afterPropertiesSet() {
        // This test double does not use a real connection factory.
      }
    };
  }

  @Bean
  RedisScript<Long> geoUpsertScript() {
    return new RedisScript<>() {
      @Override
      public String getSha1() {
        return null;
      }

      @Override
      public Class<Long> getResultType() {
        return Long.class;
      }

      @Override
      public String getScriptAsString() {
        return "return 1";
      }
    };
  }

  @Bean
  RedisScript<Long> geoEvictScript() {
    return new RedisScript<>() {
      @Override
      public String getSha1() {
        return null;
      }

      @Override
      public Class<Long> getResultType() {
        return Long.class;
      }

      @Override
      public String getScriptAsString() {
        return "return 0";
      }
    };
  }

  @Bean
  EventGeoCacheKeyLayout eventGeoCacheKeyLayout() {
    return EventGeoCacheKeyLayout.STANDALONE;
  }

  @Bean
  RedisEventGeoSourceDirectory redisEventGeoSourceDirectory() {
    return mock(RedisEventGeoSourceDirectory.class);
  }

  @Bean
  Serializer<Feature, byte[]> featureSerializer() {
    return new ProtobufSerializer<>(new FeatureMapper(), ElementProtos.ElementMessage.class);
  }

  @Bean
  @Primary
  RecordingCacheEventsPublisher recordingCacheEventsPublisher() {
    return new RecordingCacheEventsPublisher();
  }

  @Bean
  CachePublisher<CacheEventsNotification> realTimeEventsPublisher(
      @Qualifier("recordingCacheEventsPublisher")
          RecordingCacheEventsPublisher recordingCacheEventsPublisher) {
    return recordingCacheEventsPublisher;
  }

  @Bean
  RealTimeEventHandler<List<Feature>> realTimeEventHandler(
      RedisTemplate<String, byte[]> redisTemplate,
      EventGeoCacheKeyLayout eventGeoCacheKeyLayout,
      RedisEventGeoSourceDirectory redisEventGeoSourceDirectory,
      RedisScript<Long> geoUpsertScript,
      RedisScript<Long> geoEvictScript,
      @Qualifier("featureSerializer") Serializer<Feature, byte[]> serializer,
      CachePublisher<CacheEventsNotification> realTimeEventsPublisher) {
    return new RealTimeGeoFeatureHandler(
        new GeoCacheWriter(
            redisTemplate,
            eventGeoCacheKeyLayout,
            redisEventGeoSourceDirectory,
            geoUpsertScript,
            geoEvictScript,
            serializer,
            realTimeEventsPublisher));
  }

  @Bean(name = "realTimeFactory")
  ConcurrentKafkaListenerContainerFactory<String, Feature> realTimeFactory(
      @Qualifier("componentFeatureBatchFactory")
          ConcurrentKafkaListenerContainerFactory<String, Feature> factory) {
    return factory;
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.gateway.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.config.SpringApplicationFactory;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.technical.spring.config.CacheConfig;
import com.opencop.processor.technical.spring.config.GeoCacheConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class GeoDirectoryWriterIT {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import({CacheConfig.class, GeoCacheConfiguration.class})
  static class ProbeApplication {
    @Bean
    CachePublisher<CacheEventsNotification> publisher() {
      return mock();
    }
  }

  @Test
  void productionWriter_registrationFailurePreventsMutation_thenMixedSourcesRegisterAndHeal() {
    try (var fixture = LocalRedisClusterSupport.start()) {
      var app = SpringApplicationFactory.create(ProbeApplication.class);
      app.setLogStartupInfo(false);
      try (var context =
          app.run(
              "--spring.main.web-application-type=none",
              "--spring.config.name=directory-proof",
              "--spring.main.banner-mode=off",
              "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
              "--redis.mode=CLUSTER",
              "--redis.host=" + fixture.seedHost(),
              "--redis.port=" + fixture.seedPort(),
              "--redis.ssl=false")) {
        var writer = context.getBean(GeoCacheGateway.class);
        var redis = context.getBean(StringRedisTemplate.class);
        var directory = context.getBean(RedisEventGeoSourceDirectory.class);
        var publisher = context.getBean(CachePublisher.class);
        var layout = EventGeoCacheKeyLayout.CLUSTER;
        var sources =
            fixture.sourceIdsByPrimaryPort(s -> layout.sourceKeys(s).registryKey()).values();
        long now = System.currentTimeMillis();
        var features = sources.stream().map(s -> feature(s, "123", now, now + 60_000)).toList();
        redis.opsForValue().set(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET, "wrong-type");
        assertThatThrownBy(() -> writer.write(features, true)).isInstanceOf(RuntimeException.class);
        for (var source : sources) {
          var keys = layout.sourceKeys(source);
          assertThat(redis.hasKey(keys.eventKey("123"))).isFalse();
          assertThat(redis.hasKey(keys.registryKey())).isFalse();
        }
        verifyNoInteractions(publisher);
        redis.delete(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET);
        writer.write(features, true);
        assertThat(directory.discoverSources()).hasSize(3);
        verify(publisher, times(3)).publish(any());
        var quiet = sources.iterator().next();
        redis.opsForSet().remove(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET, quiet);
        assertThat(directory.discoverSources()).hasSize(2);
        assertThat(redis.hasKey(layout.sourceKeys(quiet).eventKey("123"))).isTrue();
        writer.write(List.of(feature(quiet, "456", now + 1, now + 60_000)), false);
        assertThat(directory.discoverSources()).hasSize(3);
      }
    }
  }

  static Feature feature(String source, String objectId, long time, long stale) {
    return JSON.readValue(
        JSON.writeValueAsBytes(
            Map.of(
                "type",
                "Feature",
                "geometry",
                Map.of("type", "Point", "coordinates", List.of(-75.16, 39.95)),
                "properties",
                Map.of(
                    "opencop",
                    Map.of(
                        "source",
                        source,
                        "objectId",
                        objectId,
                        "id",
                        UUID.randomUUID(),
                        "kind",
                        "track",
                        "displayName",
                        "repair probe",
                        "timestampz",
                        Map.of("time", time, "start", time - 100, "stale", stale),
                        "element",
                        Map.of("type", "point"))))),
        Feature.class);
  }
}

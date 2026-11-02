// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.technical.config.SpringApplicationFactory;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import com.opencop.websocket.technical.gateway.cache.Janitor;
import com.opencop.websocket.technical.spring.config.GeoCacheConfiguration;
import com.opencop.websocket.technical.spring.config.WebSocketSchedulers;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Timeout(90)
class GeoDirectoryRepairIT {
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @Import({
    GeoCacheConfiguration.class,
    WebSocketSchedulers.class,
    GeoDirectoryRepairConfiguration.class
  })
  static class ProbeApplication {}

  @Test
  void productionBeans_scheduledRepairRestoresDiscovery_thenNormalJanitorCleansExpiredState()
      throws Exception {
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
              "--redis.ssl=false",
              "--opencop.geo.directory-repair.cron=*/1 * * * * *",
              "--opencop.geo.directory-repair.scan-count=1",
              "--opencop.geo.directory-repair.scan-pause=20ms",
              "--opencop.geo.directory-repair.registration-batch-size=2",
              "--janitor.interval.ms=100")) {
        var job = context.getBean(GeoDirectoryRepairJob.class);
        var repairScheduler =
            context.getBean("geoDirectoryRepairScheduler", ThreadPoolTaskScheduler.class);
        job.stop();
        await().atMost(Duration.ofSeconds(2)).until(() -> repairScheduler.getActiveCount() == 0);
        var redis = context.getBean(StringRedisTemplate.class);
        var directory = context.getBean(RedisEventGeoSourceDirectory.class);
        var layout = context.getBean(EventGeoCacheKeyLayout.class);
        var sources =
            fixture.sourceIdsByPrimaryPort(s -> layout.sourceKeys(s).registryKey()).values();
        for (var source : sources) {
          var keys = layout.sourceKeys(source);
          redis.opsForValue().set(keys.registryKey(), keys.sourcePrefix());
          redis.opsForValue().set(keys.eventKey("quiet"), "payload", Duration.ofMillis(500));
          redis.opsForZSet().add(keys.updatedAtKey(), keys.eventKey("quiet"), 1);
          redis
              .opsForZSet()
              .add(keys.staleAtKey(), keys.eventKey("quiet"), System.currentTimeMillis() + 500);
        }
        // No registration: models directory loss. Directly addressed payload remains visible.
        assertThat(directory.discoverSources()).isEmpty();
        for (var source : sources)
          assertThat(redis.hasKey(layout.sourceKeys(source).eventKey("quiet"))).isTrue();
        for (int i = 0; i < 150; i++) redis.opsForValue().set("unrelated:" + i, "noise");
        var heartbeat = context.getBean("wsHeartbeatScheduler", ThreadPoolTaskScheduler.class);
        var ticks = new AtomicInteger();
        var heartbeatTask =
            heartbeat.scheduleAtFixedRate(ticks::incrementAndGet, Duration.ofMillis(10));
        var metrics = context.getBean(MeterRegistry.class);
        job.start();
        await()
            .atMost(Duration.ofSeconds(25))
            .untilAsserted(
                () -> {
                  assertThat(
                          metrics
                              .get("opencop_geo_directory_repair_last_success_timestamp_seconds")
                              .gauge()
                              .value())
                      .isPositive();
                  assertThat(directory.discoverSources()).hasSize(3);
                  for (var source : sources) {
                    assertThat(redis.hasKey(layout.sourceKeys(source).staleAtKey())).isFalse();
                    assertThat(redis.hasKey(layout.sourceKeys(source).updatedAtKey())).isFalse();
                  }
                });
        assertThat(context.getBean(Janitor.class)).isNotNull();
        assertThat(ticks.get()).isGreaterThan(50);
        assertThat(
                metrics.get("opencop_geo_directory_repair_scan_requests_total").counter().count())
            .isGreaterThan(3);
        // A second pass is idempotent, even though directory members outlive local markers.
        assertThat(redis.opsForSet().size(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET))
            .isEqualTo(3);
        heartbeatTask.cancel(true);
        assertThat(repairScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
      }
    }
  }
}

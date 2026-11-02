// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisEventGeoCacheEvictionGatewayIT {

  private static final UUID SOURCE_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private RedisEventGeoSourceDirectory sourceDirectory;
  private EventGeoCacheEvictionGateway gateway;

  @BeforeEach
  void setUp() {
    connectionFactory =
        new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    var layout = EventGeoCacheKeyLayout.STANDALONE;
    sourceDirectory =
        new RedisEventGeoSourceDirectory(layout, redisTemplate, new SimpleMeterRegistry());
    gateway = new RedisEventGeoCacheEvictionGateway(redisTemplate, layout, sourceDirectory);
  }

  @AfterEach
  void tearDown() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    connectionFactory.destroy();
  }

  @Test
  void givenSourceEntriesExist_whenEvictSourceCalled_removesSourceRedisEntries() {
    seedSourceAEntries(2);

    gateway.evictGeoSource(SOURCE_A);

    var sourceAPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_A);
    var sourceB = EventGeoCacheKeys.geoSourcePrefix(SOURCE_B);

    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.event(SOURCE_A, "evt-1")));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.event(SOURCE_A, "evt-2")));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.updatedAtFromPrefix(sourceAPrefix)));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.staleAtFromPrefix(sourceAPrefix)));
    assertEquals(0L, redisTemplate.opsForZSet().size(EventGeoCacheKeys.SOURCE_INDEX));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.updatedAtFromPrefix(sourceB)));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.event(SOURCE_B, "other")));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.staleAtFromPrefix(sourceB)));
    assertEquals(0L, redisTemplate.opsForZSet().size(sourceB));
  }

  @Test
  void givenAnotherSourceEntriesExist_whenEvictSourceCalled_keepsOtherSourceIntact() {
    seedSourceAEntries(2);
    seedSourceBEntries();

    var sourceAPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_A);
    var sourceBPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_B);
    var sourceBEventKey = EventGeoCacheKeys.event(SOURCE_B, "bkt-1");

    gateway.evictGeoSource(SOURCE_A);

    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.event(SOURCE_A, "evt-1")));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.event(SOURCE_A, "evt-2")));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.updatedAtFromPrefix(sourceAPrefix)));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.staleAtFromPrefix(sourceAPrefix)));

    assertTrue(redisTemplate.hasKey(EventGeoCacheKeys.event(SOURCE_B, "bkt-1")));
    assertTrue(redisTemplate.hasKey(EventGeoCacheKeys.event(SOURCE_B, "bkt-2")));
    assertEquals(
        2, redisTemplate.opsForZSet().size(EventGeoCacheKeys.updatedAtFromPrefix(sourceBPrefix)));
    assertEquals(
        2, redisTemplate.opsForZSet().size(EventGeoCacheKeys.staleAtFromPrefix(sourceBPrefix)));

    assertNotNull(
        redisTemplate
            .opsForZSet()
            .score(EventGeoCacheKeys.updatedAtFromPrefix(sourceBPrefix), sourceBEventKey));
    assertNotNull(
        redisTemplate
            .opsForZSet()
            .score(EventGeoCacheKeys.staleAtFromPrefix(sourceBPrefix), sourceBEventKey));
    assertNotNull(redisTemplate.opsForZSet().score(EventGeoCacheKeys.SOURCE_INDEX, sourceBPrefix));
  }

  @Test
  void givenSourceZsetMemberPointsToAnotherSource_whenEvictSourceCalled_keepsOtherPayload() {
    var sourceAPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_A);
    var sourceBPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_B);
    var sourceBEventKey = EventGeoCacheKeys.event(SOURCE_B, "corrupt-member-target");
    redisTemplate.opsForValue().set(sourceBEventKey, "payload-from-source-b");
    redisTemplate.opsForZSet().add(EventGeoCacheKeys.SOURCE_INDEX, sourceAPrefix, 1.0);
    redisTemplate.opsForZSet().add(EventGeoCacheKeys.SOURCE_INDEX, sourceBPrefix, 2.0);
    redisTemplate
        .opsForZSet()
        .add(EventGeoCacheKeys.updatedAtFromPrefix(sourceAPrefix), sourceBEventKey, 1);
    redisTemplate
        .opsForZSet()
        .add(EventGeoCacheKeys.staleAtFromPrefix(sourceAPrefix), sourceBEventKey, 1);

    gateway.evictGeoSource(SOURCE_A);

    assertTrue(redisTemplate.hasKey(sourceBEventKey));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.updatedAtFromPrefix(sourceAPrefix)));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.staleAtFromPrefix(sourceAPrefix)));
    assertNotNull(redisTemplate.opsForZSet().score(EventGeoCacheKeys.SOURCE_INDEX, sourceBPrefix));
    assertEquals(1L, redisTemplate.opsForZSet().size(EventGeoCacheKeys.SOURCE_INDEX));
  }

  @Test
  void givenOnlySourceIndexMember_whenEvictSourceCalled_removesSourceMemberWithoutError() {
    var sourceAPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_A);
    redisTemplate.opsForZSet().add(EventGeoCacheKeys.SOURCE_INDEX, sourceAPrefix, 1.0);

    gateway.evictGeoSource(SOURCE_A);

    assertEquals(0L, redisTemplate.opsForZSet().size(EventGeoCacheKeys.SOURCE_INDEX));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.updatedAtFromPrefix(sourceAPrefix)));
    assertFalse(redisTemplate.hasKey(EventGeoCacheKeys.staleAtFromPrefix(sourceAPrefix)));
  }

  @Test
  void givenOrphanedSourcePayload_whenEvictSourceCalled_removesItWithoutAnIndexMember() {
    var sourceAPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_A);
    var orphanedEventKey = EventGeoCacheKeys.event(SOURCE_A, "orphaned");
    redisTemplate.opsForZSet().add(EventGeoCacheKeys.SOURCE_INDEX, sourceAPrefix, 1.0);
    redisTemplate.opsForValue().set(orphanedEventKey, "orphaned-payload");

    gateway.evictGeoSource(SOURCE_A);

    assertFalse(redisTemplate.hasKey(orphanedEventKey));
    assertFalse(redisTemplate.hasKey(sourceAPrefix + ":updatedAt"));
    assertFalse(redisTemplate.hasKey(sourceAPrefix + ":staleAt"));
    assertEquals(0L, redisTemplate.opsForZSet().size(EventGeoCacheKeys.SOURCE_INDEX));
  }

  private void seedSourceAEntries(int objectCount) {
    var sourceAPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_A);
    redisTemplate.opsForZSet().add(EventGeoCacheKeys.SOURCE_INDEX, sourceAPrefix, 1.0);
    for (int i = 1; i <= objectCount; i++) {
      var objectId = "evt-%d".formatted(i);
      var eventKey = EventGeoCacheKeys.event(SOURCE_A, objectId);
      redisTemplate.opsForValue().set(eventKey, objectId);
      redisTemplate
          .opsForZSet()
          .add(EventGeoCacheKeys.updatedAtFromPrefix(sourceAPrefix), eventKey, i);
      redisTemplate
          .opsForZSet()
          .add(EventGeoCacheKeys.staleAtFromPrefix(sourceAPrefix), eventKey, i);
    }
  }

  private void seedSourceBEntries() {
    var sourceBPrefix = EventGeoCacheKeys.geoSourcePrefix(SOURCE_B);
    var eventKey1 = EventGeoCacheKeys.event(SOURCE_B, "bkt-1");
    var eventKey2 = EventGeoCacheKeys.event(SOURCE_B, "bkt-2");
    redisTemplate.opsForZSet().add(EventGeoCacheKeys.SOURCE_INDEX, sourceBPrefix, 2.0);
    redisTemplate.opsForValue().set(eventKey1, "payload-bkt-1");
    redisTemplate.opsForValue().set(eventKey2, "payload-bkt-2");
    redisTemplate
        .opsForZSet()
        .add(EventGeoCacheKeys.updatedAtFromPrefix(sourceBPrefix), eventKey1, 11);
    redisTemplate
        .opsForZSet()
        .add(EventGeoCacheKeys.updatedAtFromPrefix(sourceBPrefix), eventKey2, 12);
    redisTemplate
        .opsForZSet()
        .add(EventGeoCacheKeys.staleAtFromPrefix(sourceBPrefix), eventKey1, 21);
    redisTemplate
        .opsForZSet()
        .add(EventGeoCacheKeys.staleAtFromPrefix(sourceBPrefix), eventKey2, 22);
  }
}

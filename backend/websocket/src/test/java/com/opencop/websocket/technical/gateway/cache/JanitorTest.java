// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.ports.gateway.cache.CacheException;
import com.opencop.core.sdk.ports.gateway.cache.CacheExceptionCodes;
import com.opencop.core.sdk.ports.gateway.cache.CacheService;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.SourceKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

class JanitorTest {

  private static final long MAX_STALE_SCORE = Instant.MAX.getEpochSecond();

  @Test
  void run_nullStalePageUsesTheLegacyRegistrationGuard() throws Exception {
    var keys = standaloneKeys("source-1");
    var cache = cache();
    var directory = directoryWith(keys);
    when(cache.getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(2)))
        .thenReturn(null);

    new Janitor(cache, directory, 2, new SimpleMeterRegistry()).run();

    verify(directory).unregisterIfEmpty(keys);
    verify(cache).getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(2));
    verify(cache, never()).deleteIfExists(any());
  }

  @Test
  void run_firstEmptyPageKeepsRegistrationWhenTheFutureGuardFindsLiveEvents() {
    var keys = standaloneKeys("source-1");
    var cache = cache();
    var directory = directoryWith(keys);
    when(cache.getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(2)))
        .thenReturn(Set.of());
    when(cache.getEventKeysByScore(
            eq(keys.staleAtKey()), anyLong(), eq(MAX_STALE_SCORE), eq(0), eq(1)))
        .thenReturn(Set.of(keys.eventKey("still-live")));

    new Janitor(cache, directory, 2, new SimpleMeterRegistry()).run();

    verify(cache).getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(2));
    verify(cache)
        .getEventKeysByScore(eq(keys.staleAtKey()), anyLong(), eq(MAX_STALE_SCORE), eq(0), eq(1));
    verify(directory, never()).unregisterIfEmpty(any());
  }

  @Test
  void run_shortFinalPageDeletesPayloadAndBothIndexReferencesWithoutUnregistering()
      throws Exception {
    var keys = standaloneKeys("source-1");
    var stale = keys.eventKey("expired");
    var cache = cache();
    var directory = directoryWith(keys);
    when(cache.getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(2)))
        .thenReturn(new LinkedHashSet<>(List.of(stale)));

    new Janitor(cache, directory, 2, new SimpleMeterRegistry()).run();

    var order = inOrder(cache);
    order.verify(cache).deleteIfExists(stale);
    order.verify(cache).removeFromSet(keys.updatedAtKey(), stale);
    order.verify(cache).removeFromSet(keys.staleAtKey(), stale);
    verify(directory, never()).unregisterIfEmpty(any());
  }

  @Test
  void run_emptyDiscoveryDoesNoCacheWork() {
    var cache = cache();
    var directory = mock(RedisEventGeoSourceDirectory.class);
    when(directory.discoverSources(any())).thenReturn(List.of());

    new Janitor(cache, directory, 2, new SimpleMeterRegistry()).run();

    verify(directory).discoverSources(any());
    verifyNoInteractions(cache);
  }

  @Test
  void run_repeatsOffsetZeroPagesUntilTheFinalShortPage() throws Exception {
    var keys = standaloneKeys("source-1");
    var first = new LinkedHashSet<>(List.of(keys.eventKey("first"), keys.eventKey("second")));
    var finalPage = new LinkedHashSet<>(List.of(keys.eventKey("third")));
    var cache = cache();
    var directory = directoryWith(keys);
    when(cache.getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(2)))
        .thenReturn(first, finalPage);

    new Janitor(cache, directory, 2, new SimpleMeterRegistry()).run();

    verify(cache, times(2))
        .getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(2));
    verify(cache, times(3)).deleteIfExists(any());
    verify(cache, times(6)).removeFromSet(any(), any());
    verify(directory, never()).unregisterIfEmpty(any());
  }

  @Test
  void run_removesForeignReferencesAndAdvancesWhenAnEntirePageIsForeign() throws Exception {
    var keys = standaloneKeys("source-1");
    var foreign = standaloneKeys("source-2").eventKey("foreign");
    var cache = cache();
    var directory = directoryWith(keys);
    when(cache.getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(1)))
        .thenReturn(new LinkedHashSet<>(List.of(foreign)), new LinkedHashSet<>());

    new Janitor(cache, directory, 1, new SimpleMeterRegistry()).run();

    verify(cache, times(2))
        .getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(1));
    verify(cache, never()).deleteIfExists(any());
    verify(cache).removeFromSet(keys.updatedAtKey(), foreign);
    verify(cache).removeFromSet(keys.staleAtKey(), foreign);
    verify(directory, never()).unregisterIfEmpty(any());
  }

  @Test
  void run_propagatesProgrammingErrorsButValidatesPageSizeAtConstruction() {
    var cache = cache();
    var directory = mock(RedisEventGeoSourceDirectory.class);
    var failure = new IllegalStateException("unexpected programming error");
    when(directory.discoverSources(any())).thenThrow(failure);

    assertThatThrownBy(() -> new Janitor(cache, directory, 0, new SimpleMeterRegistry()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Janitor(cache, directory, -1, new SimpleMeterRegistry()))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new Janitor(cache, directory, 1, new SimpleMeterRegistry()).run())
        .isSameAs(failure);
    verify(directory).discoverSources(any());
  }

  @Test
  void run_catchesCacheFailureAtThePassBoundary() throws Exception {
    var keys = standaloneKeys("source-1");
    var stale = keys.eventKey("expired");
    var cache = cache();
    var directory = directoryWith(keys);
    var failure = new CacheException(CacheExceptionCodes.SERIALIZATION_FAILED);
    when(cache.getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(1)))
        .thenReturn(new LinkedHashSet<>(List.of(stale)));
    doThrow(failure).when(cache).removeFromSet(keys.updatedAtKey(), stale);
    var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Janitor.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      new Janitor(cache, directory, 1, new SimpleMeterRegistry()).run();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    var errors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getFormattedMessage()).contains(failure.getCode());
    assertThat(errors.getFirst().getThrowableProxy()).isNull();

    verify(directory).discoverSources(any());
    verify(cache).getEventKeysByScore(eq(keys.staleAtKey()), eq(-1L), anyLong(), eq(0), eq(1));
    verify(cache).deleteIfExists(stale);
    verify(cache).removeFromSet(keys.updatedAtKey(), stale);
    verify(cache, never()).removeFromSet(keys.staleAtKey(), stale);
    verify(directory, never()).unregisterIfEmpty(any());
  }

  @Test
  void run_redisFailureLogsTypeWithoutMessageOrCause() {
    var cache = cache();
    var directory = mock(RedisEventGeoSourceDirectory.class);
    var canary = "private-redis-key-must-not-reach-logs";
    when(directory.discoverSources(any())).thenThrow(new QueryTimeoutException(canary));
    var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Janitor.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      new Janitor(cache, directory, 1, new SimpleMeterRegistry()).run();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    var errors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getFormattedMessage())
        .contains("QueryTimeoutException")
        .doesNotContain(canary);
    assertThat(errors.getFirst().getThrowableProxy()).isNull();
    verifyNoInteractions(cache);
  }

  @Test
  void run_completesNoCleanupWhenTheRealDirectoryReadFails() {
    var stringRedisTemplate = mock(StringRedisTemplate.class);
    org.springframework.data.redis.core.SetOperations<String, String> members = mock();
    when(stringRedisTemplate.opsForSet()).thenReturn(members);
    when(members.members(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET))
        .thenThrow(new QueryTimeoutException("directory unavailable"));
    var directory =
        new RedisEventGeoSourceDirectory(
            EventGeoCacheKeyLayout.CLUSTER, stringRedisTemplate, new SimpleMeterRegistry());
    var cache = cache();

    new Janitor(cache, directory, 1, new SimpleMeterRegistry()).run();

    verify(stringRedisTemplate, never()).scan(any(ScanOptions.class));
    verifyNoInteractions(cache);
  }

  private static SourceKeys standaloneKeys(String sourceId) {
    return EventGeoCacheKeyLayout.STANDALONE.sourceKeys(sourceId);
  }

  @SuppressWarnings("unchecked")
  private static CacheService<Feature> cache() {
    return mock(CacheService.class);
  }

  private static RedisEventGeoSourceDirectory directoryWith(SourceKeys keys) {
    var directory = mock(RedisEventGeoSourceDirectory.class);
    when(directory.discoverSources(any())).thenReturn(List.of(keys));
    return directory;
  }
}

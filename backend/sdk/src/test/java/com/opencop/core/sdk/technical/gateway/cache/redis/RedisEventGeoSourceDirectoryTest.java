// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RedisEventGeoSourceDirectoryTest {

  private static final String SOURCE_INDEX = "opencop:event:sources";
  private static final UUID FIRST_SOURCE = new UUID(0L, 1L);
  private static final UUID SECOND_SOURCE = new UUID(0L, 2L);
  private static final String REMOVALS_METRIC = "opencop_redis_source_directory_removals_total";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @Mock private SetOperations<String, String> setOperations;
  @Mock private RedisScript<Long> unregisterIfEmptyScript;

  @AfterEach
  void closeRegistry() {
    registry.close();
  }

  @Test
  void standaloneDiscovery_returnsNothingAndHealsNothingWhenTheGlobalIndexIsEmpty() {
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(SOURCE_INDEX, 0, -1)).thenReturn(new LinkedHashSet<>());

    assertThat(standaloneDirectory().discoverSources()).isEmpty();

    verify(zSetOperations, never()).remove(anyString(), any());
  }

  @Test
  void standaloneDiscovery_preservesMemberOrderAndReadsOnlyTheGlobalIndex() {
    var first = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(FIRST_SOURCE);
    var second = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SECOND_SOURCE);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(SOURCE_INDEX, 0, -1))
        .thenReturn(new LinkedHashSet<>(List.of(second.sourcePrefix(), first.sourcePrefix())));

    assertThat(standaloneDirectory().discoverSources()).containsExactly(second, first);

    verify(zSetOperations, never()).remove(anyString(), any());
    verify(redisTemplate, never()).scan(any(ScanOptions.class));
  }

  @Test
  void standaloneDiscovery_preservesConstructableMembersAndSelfHealsOnlyForeignEntries() {
    var healthy = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(FIRST_SOURCE);
    var empty = EventGeoCacheKeyLayout.STANDALONE.sourceKeys("");
    var markerShaped = EventGeoCacheKeyLayout.STANDALONE.sourceKeys("{tagged}:registry");
    var glob = EventGeoCacheKeyLayout.STANDALONE.sourceKeys("*");
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(SOURCE_INDEX, 0, -1))
        .thenReturn(
            new LinkedHashSet<>(
                List.of(
                    healthy.sourcePrefix(),
                    empty.sourcePrefix(),
                    markerShaped.sourcePrefix(),
                    glob.sourcePrefix(),
                    "",
                    "totally-unrelated")));
    // Another cleaner may already have removed one of the two malformed members.
    when(zSetOperations.remove(SOURCE_INDEX, "", "totally-unrelated")).thenReturn(1L);

    var logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RedisEventGeoSourceDirectory.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(standaloneDirectory().discoverSources())
          .containsExactly(healthy, empty, markerShaped, glob);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    var warnings = appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    assertThat(warnings).hasSize(1);
    assertThat(warnings.getFirst().getFormattedMessage())
        .contains("2 malformed member(s)")
        .doesNotContain("totally-unrelated");
    assertThat(warnings.getFirst().getThrowableProxy()).isNull();

    verify(zSetOperations).remove(SOURCE_INDEX, "", "totally-unrelated");
    assertThat(registry.get(REMOVALS_METRIC).counter().count()).isEqualTo(1.0d);
  }

  @Test
  void clusterDiscovery_readsRetainedSourceIdsWithoutScanningKeys() {
    var first = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(FIRST_SOURCE);
    var second = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(SECOND_SOURCE);
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET))
        .thenReturn(new LinkedHashSet<>(List.of(first.sourceId(), "*", second.sourceId())));
    assertThat(clusterDirectory().discoverSources()).containsExactly(first, second);
    verify(redisTemplate, never()).scan(any(ScanOptions.class));
    verify(redisTemplate, never()).opsForZSet();
  }

  @Test
  void clusterDiscovery_propagatesReadFailure() {
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET))
        .thenThrow(new QueryTimeoutException("unavailable"));
    assertThatThrownBy(() -> clusterDirectory().discoverSources())
        .isInstanceOf(QueryTimeoutException.class);
  }

  @Test
  void registration_deduplicatesPreparedSourcesAndSkipsEmptyOrStandalone() {
    var first = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(FIRST_SOURCE);
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.add(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET, first.sourceId()))
        .thenReturn(1L);
    clusterDirectory().registerPreparedSources(List.of(first, first));
    clusterDirectory().registerPreparedSources(List.of());
    standaloneDirectory()
        .registerPreparedSources(
            List.of(EventGeoCacheKeyLayout.STANDALONE.sourceKeys(FIRST_SOURCE)));
    verify(setOperations).add(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET, first.sourceId());
  }

  @Test
  void unregister_rejectsForeignLayoutSourceKeysBeforeTouchingRedis() {
    var standaloneKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(FIRST_SOURCE);
    var clusterKeys = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(FIRST_SOURCE);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> clusterDirectory().unregisterIfEmpty(standaloneKeys));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> standaloneDirectory().unregisterIfEmpty(clusterKeys));

    verifyNoInteractions(redisTemplate, unregisterIfEmptyScript);
  }

  @Test
  void standaloneUnregister_removesOnlyTheExactSourceMemberAndReportsWhatRedisDid() {
    var keys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(FIRST_SOURCE);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.remove(SOURCE_INDEX, keys.sourcePrefix()))
        .thenReturn(1L)
        .thenReturn(0L)
        .thenReturn(null);

    var directory = standaloneDirectory();
    assertThat(directory.unregisterIfEmpty(keys)).isTrue();
    assertThat(registry.get(REMOVALS_METRIC).counter().count()).isEqualTo(1.0d);
    assertThat(directory.unregisterIfEmpty(keys)).isFalse();
    assertThat(directory.unregisterIfEmpty(keys)).isFalse();
    assertThat(registry.get(REMOVALS_METRIC).counter().count()).isEqualTo(1.0d);
    assertThat(registry.getMeters()).hasSize(1);
    assertThat(registry.get(REMOVALS_METRIC).counter().getId().getTags()).isEmpty();
  }

  @Test
  void standaloneUnregister_whenRemovalFails_doesNotCountAndPropagatesFailure() {
    var keys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(FIRST_SOURCE);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    var failure = new QueryTimeoutException("unavailable");
    when(zSetOperations.remove(SOURCE_INDEX, keys.sourcePrefix())).thenThrow(failure);

    var directory = standaloneDirectory();

    assertThatThrownBy(() -> directory.unregisterIfEmpty(keys)).isSameAs(failure);
    assertThat(registry.get(REMOVALS_METRIC).counter().count()).isZero();
  }

  @Test
  void standaloneDiscovery_whenMalformedRemovalConfirmsMultipleMembers_countsRemovedMembers() {
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(SOURCE_INDEX, 0, -1))
        .thenReturn(new LinkedHashSet<>(List.of("first", "second", "third")));
    when(zSetOperations.remove(SOURCE_INDEX, "first", "second", "third")).thenReturn(2L);

    assertThat(standaloneDirectory().discoverSources()).isEmpty();

    assertThat(registry.get(REMOVALS_METRIC).counter().count()).isEqualTo(2.0d);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = 0L)
  void standaloneDiscovery_whenMalformedRemovalIsNotConfirmed_doesNotCount(Long removed) {
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(SOURCE_INDEX, 0, -1))
        .thenReturn(new LinkedHashSet<>(List.of("malformed")));
    when(zSetOperations.remove(SOURCE_INDEX, "malformed")).thenReturn(removed);

    assertThat(standaloneDirectory().discoverSources()).isEmpty();

    assertThat(registry.get(REMOVALS_METRIC).counter().count()).isZero();
  }

  @Test
  void standaloneDiscovery_whenMalformedRemovalFails_doesNotCountAndPropagatesFailure() {
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(SOURCE_INDEX, 0, -1))
        .thenReturn(new LinkedHashSet<>(List.of("malformed")));
    var failure = new QueryTimeoutException("unavailable");
    when(zSetOperations.remove(SOURCE_INDEX, "malformed")).thenThrow(failure);

    var directory = standaloneDirectory();

    assertThatThrownBy(directory::discoverSources).isSameAs(failure);
    assertThat(registry.get(REMOVALS_METRIC).counter().count()).isZero();
  }

  @Test
  void clusterUnregister_suppliesExactlyMarkerUpdateAndStaleKeysInThatOrder() {
    var keys = EventGeoCacheKeyLayout.CLUSTER.sourceKeys(FIRST_SOURCE);
    when(redisTemplate.execute(eq(unregisterIfEmptyScript), anyList()))
        .thenReturn(1L)
        .thenReturn(0L)
        .thenReturn(null);

    var directory = clusterDirectory();
    assertThat(directory.unregisterIfEmpty(keys)).isTrue();
    assertThat(directory.unregisterIfEmpty(keys)).isFalse();
    assertThat(directory.unregisterIfEmpty(keys)).isFalse();
    assertThat(registry.get(REMOVALS_METRIC).counter().count()).isZero();

    var suppliedKeys = ArgumentCaptor.forClass(List.class);
    verify(redisTemplate, org.mockito.Mockito.times(3))
        .execute(eq(unregisterIfEmptyScript), suppliedKeys.capture());
    assertThat(suppliedKeys.getValue())
        .containsExactly(keys.registryKey(), keys.updatedAtKey(), keys.staleAtKey());
    verify(redisTemplate, never()).opsForZSet();
  }

  private RedisEventGeoSourceDirectory standaloneDirectory() {
    return new RedisEventGeoSourceDirectory(
        EventGeoCacheKeyLayout.STANDALONE, redisTemplate, registry, unregisterIfEmptyScript);
  }

  private RedisEventGeoSourceDirectory clusterDirectory() {
    return new RedisEventGeoSourceDirectory(
        EventGeoCacheKeyLayout.CLUSTER, redisTemplate, registry, unregisterIfEmptyScript);
  }
}

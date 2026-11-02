// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.ports.gateway.cache.CacheException;
import com.opencop.core.sdk.ports.gateway.cache.CacheExceptionCodes;
import com.opencop.core.sdk.ports.gateway.cache.CacheService;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeys;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class JanitorMetricsTest {

  @ParameterizedTest
  @EnumSource(EventGeoCacheKeyLayout.class)
  void gauge_beforeFirstRead_reportsUnknownWithoutRedisCalls(EventGeoCacheKeyLayout layout) {
    var fixture = new Fixture(layout);
    assertThat(fixture.gauge.value()).isNaN();
    verifyNoInteractions(fixture.redis, fixture.cache);
  }

  @ParameterizedTest
  @EnumSource(EventGeoCacheKeyLayout.class)
  void run_populationChanges_reportsRawPreCleanupSizeIncludingZero(EventGeoCacheKeyLayout layout) {
    var fixture = new Fixture(layout);
    fixture
        .read()
        .thenReturn(
            Set.of(fixture.member("a"), "malformed{entry"), Set.of(), Set.of(fixture.member("c")));

    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isEqualTo(2);
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isZero();
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isEqualTo(1);

    clearInvocations(fixture.redis, fixture.zset, fixture.set, fixture.cache);
    assertThat(fixture.gauge.value()).isEqualTo(1);
    verifyNoInteractions(fixture.redis, fixture.zset, fixture.set, fixture.cache);
  }

  @ParameterizedTest
  @EnumSource(EventGeoCacheKeyLayout.class)
  void run_nullRead_invalidatesPreviousCount(EventGeoCacheKeyLayout layout) {
    var fixture = new Fixture(layout);
    fixture.read().thenReturn(Set.of(fixture.member("a"))).thenReturn(null);
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isEqualTo(1);
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isNaN();
  }

  @ParameterizedTest
  @EnumSource(EventGeoCacheKeyLayout.class)
  void run_readFailure_reportsUnknownUntilRecovery(EventGeoCacheKeyLayout layout) {
    var fixture = new Fixture(layout);
    fixture
        .read()
        .thenReturn(Set.of(fixture.member("a")))
        .thenThrow(new RedisConnectionFailureException("test failure"))
        .thenReturn(Set.of());
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isEqualTo(1);
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isNaN();
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isZero();
  }

  @ParameterizedTest
  @EnumSource(EventGeoCacheKeyLayout.class)
  void run_programmingFailure_propagatesAndInvalidatesPreviousCount(EventGeoCacheKeyLayout layout) {
    var fixture = new Fixture(layout);
    var failure = new IllegalStateException("unexpected programming error");
    fixture.read().thenReturn(Set.of(fixture.member("a"))).thenThrow(failure);
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isEqualTo(1);
    assertThatThrownBy(fixture.janitor::run).isSameAs(failure);
    assertThat(fixture.gauge.value()).isNaN();
  }

  @ParameterizedTest
  @EnumSource(EventGeoCacheKeyLayout.class)
  void run_cleanupFailure_preservesSuccessfullyObservedSize(EventGeoCacheKeyLayout layout)
      throws Exception {
    var fixture = new Fixture(layout);
    fixture.read().thenReturn(Set.of(fixture.member("a")));
    when(fixture.cache.getEventKeysByScore(anyString(), anyLong(), anyLong(), anyInt(), anyInt()))
        .thenAnswer(
            _ -> {
              assertThat(fixture.gauge.value()).isEqualTo(1);
              throw new CacheException(CacheExceptionCodes.VALUE_DOES_NOT_EXIST);
            });
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isEqualTo(1);
  }

  @Test
  void run_malformedMemberCleanupFailure_preservesRawSizeBeforeFiltering() {
    var fixture = new Fixture(EventGeoCacheKeyLayout.STANDALONE);
    fixture.read().thenReturn(Set.of(fixture.member("a"), "malformed{entry"));
    when(fixture.zset.remove(EventGeoCacheKeys.SOURCE_INDEX, "malformed{entry"))
        .thenThrow(new RedisConnectionFailureException("cleanup unavailable"));
    fixture.janitor.run();
    assertThat(fixture.gauge.value()).isEqualTo(2);
    verifyNoInteractions(fixture.cache);
  }

  @ParameterizedTest
  @EnumSource(EventGeoCacheKeyLayout.class)
  void run_oneDiscoveryRead_doesNotAddScanOrCardinalityRequests(EventGeoCacheKeyLayout layout) {
    var fixture = new Fixture(layout);
    fixture.read().thenReturn(Set.of());
    fixture.janitor.run();
    if (layout == EventGeoCacheKeyLayout.STANDALONE) {
      verify(fixture.zset).range(EventGeoCacheKeys.SOURCE_INDEX, 0, -1);
      verifyNoInteractions(fixture.set);
    } else {
      verify(fixture.set).members(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET);
      verifyNoInteractions(fixture.zset);
    }
    verify(fixture.redis, never()).scan(any());
    verify(fixture.zset, never()).size(anyString());
    verify(fixture.set, never()).size(anyString());
  }

  private static final class Fixture {
    private final EventGeoCacheKeyLayout layout;
    private final StringRedisTemplate redis = mock();
    private final ZSetOperations<String, String> zset = mock();
    private final SetOperations<String, String> set = mock();
    private final CacheService<Feature> cache = mock();
    private final Janitor janitor;
    private final Gauge gauge;

    private Fixture(EventGeoCacheKeyLayout layout) {
      this.layout = layout;
      when(redis.opsForZSet()).thenReturn(zset);
      when(redis.opsForSet()).thenReturn(set);
      var registry = new SimpleMeterRegistry();
      janitor =
          new Janitor(
              cache, new RedisEventGeoSourceDirectory(layout, redis, registry), 100, registry);
      gauge = registry.get("opencop_redis_source_directory_size").gauge();
      assertThat(registry.getMeters()).hasSize(2);
      assertThat(gauge.getId().getTags()).isEmpty();
    }

    private String member(String id) {
      return layout == EventGeoCacheKeyLayout.STANDALONE
          ? layout.sourceKeys(id).sourcePrefix()
          : id;
    }

    private OngoingStubbing<Set<String>> read() {
      return layout == EventGeoCacheKeyLayout.STANDALONE
          ? when(zset.range(EventGeoCacheKeys.SOURCE_INDEX, 0, -1))
          : when(set.members(RedisEventGeoSourceDirectory.CLUSTER_SOURCE_SET));
    }
  }
}

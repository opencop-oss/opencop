// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.cache;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisEventGeoSourceDirectory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class RedisEventGeoCacheEvictionGatewayTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @Mock private RedisEventGeoSourceDirectory sourceDirectory;

  @Test
  void evictGeoSource_capturesIndexesAndFinishesScanBeforeDeletingAndUnregistering() {
    var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(sourceKeys.updatedAtKey(), 0, -1))
        .thenReturn(Set.of(sourceKeys.eventKey("updated")));
    when(zSetOperations.range(sourceKeys.staleAtKey(), 0, -1))
        .thenReturn(Set.of(sourceKeys.eventKey("stale")));
    when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(List.of());
    when(redisTemplate.delete(anyCollection())).thenReturn(4L);

    new RedisEventGeoCacheEvictionGateway(
            redisTemplate, EventGeoCacheKeyLayout.STANDALONE, sourceDirectory)
        .evictGeoSource(SOURCE_ID);

    InOrder order = inOrder(zSetOperations, redisTemplate, sourceDirectory);
    order.verify(zSetOperations).range(sourceKeys.updatedAtKey(), 0, -1);
    order.verify(zSetOperations).range(sourceKeys.staleAtKey(), 0, -1);
    order.verify(redisTemplate).execute(any(RedisCallback.class));
    order.verify(redisTemplate).delete(anyCollection());
    order.verify(sourceDirectory).unregisterIfEmpty(sourceKeys);
  }

  @Test
  void evictGeoSource_whenPayloadScanFails_doesNotDeleteOrUnregister() {
    var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE_ID);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(sourceKeys.updatedAtKey(), 0, -1)).thenReturn(Set.of());
    when(zSetOperations.range(sourceKeys.staleAtKey(), 0, -1)).thenReturn(Set.of());
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenThrow(new IllegalStateException("payload scan failed"));

    assertThatThrownBy(
            () ->
                new RedisEventGeoCacheEvictionGateway(
                        redisTemplate, EventGeoCacheKeyLayout.STANDALONE, sourceDirectory)
                    .evictGeoSource(SOURCE_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("payload scan failed");

    verify(redisTemplate, never()).delete(anyCollection());
    verifyNoInteractions(sourceDirectory);
  }
}

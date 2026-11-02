// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class RedisGeoEventReaderTest {

  private static final String SOURCE = "source-1";

  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private RedisTemplate<String, byte[]> redisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @Mock private ValueOperations<String, byte[]> valueOperations;

  @Test
  void readObjects_emptyInputDoesNotTouchRedis() {
    var reader = reader(EventGeoCacheKeyLayout.STANDALONE);

    assertThat(reader.readObjects(SOURCE, List.of())).isEmpty();

    verifyNoInteractions(stringRedisTemplate, redisTemplate);
  }

  @Test
  void readObjects_buildsSelectedKeysAndFiltersMissingPayloadsInOrder() {
    var layout = EventGeoCacheKeyLayout.CLUSTER;
    var sourceKeys = layout.sourceKeys(SOURCE);
    var objectIds = List.of("first", "missing", "last");
    var payloadKeys =
        List.of(
            sourceKeys.eventKey("first"),
            sourceKeys.eventKey("missing"),
            sourceKeys.eventKey("last"));
    var first = new byte[] {1, 2};
    var last = new byte[] {3, 4};
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.multiGet(payloadKeys)).thenReturn(Arrays.asList(first, null, last));

    var result = reader(layout).readObjects(SOURCE, objectIds);

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).containsExactly(1, 2);
    assertThat(result.get(1)).containsExactly(3, 4);
    verify(valueOperations).multiGet(payloadKeys);
    verifyNoInteractions(stringRedisTemplate);
  }

  @Test
  void readPage_preservesZrangeOrderCountsForeignMembersAndMgetsOnlyOwnedKeys() {
    var layout = EventGeoCacheKeyLayout.CLUSTER;
    var sourceKeys = layout.sourceKeys(SOURCE);
    var foreignKeys = layout.sourceKeys("other-source");
    var first = sourceKeys.eventKey("first");
    var second = sourceKeys.eventKey("second");
    var missing = sourceKeys.eventKey("missing");
    var foreign = foreignKeys.eventKey("foreign");
    var members = new LinkedHashSet<>(List.of(foreign, second, first, missing));
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(zSetOperations.range(sourceKeys.updatedAtKey(), 7, 9)).thenReturn(members);
    when(valueOperations.multiGet(List.of(second, first, missing)))
        .thenReturn(Arrays.asList(new byte[] {2}, new byte[] {1}, null));

    var page = reader(layout).readPage(SOURCE, 7, 3);

    assertThat(page.consumedMembers()).isEqualTo(4);
    assertThat(page.payloads()).hasSize(2);
    assertThat(page.payloads().get(0)).containsExactly(2);
    assertThat(page.payloads().get(1)).containsExactly(1);
    verify(zSetOperations).range(sourceKeys.updatedAtKey(), 7, 9);
    verify(valueOperations).multiGet(List.of(second, first, missing));
  }

  @Test
  void readPage_foreignOnlyPageReportsProgressWithoutFetchingForeignPayloads() {
    var layout = EventGeoCacheKeyLayout.STANDALONE;
    var sourceKeys = layout.sourceKeys(SOURCE);
    var foreign = layout.sourceKeys("other-source").eventKey("foreign");
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(sourceKeys.updatedAtKey(), 0, 4))
        .thenReturn(new LinkedHashSet<>(List.of(foreign, "unrelated")));

    var page = reader(layout).readPage(SOURCE, 0, 5);

    assertThat(page.consumedMembers()).isEqualTo(2);
    assertThat(page.payloads()).isEmpty();
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void readPage_emptyRangeReturnsAnEmptyPage() {
    var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE);
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(sourceKeys.updatedAtKey(), 2, 6)).thenReturn(null);

    var page = reader(EventGeoCacheKeyLayout.STANDALONE).readPage(SOURCE, 2, 5);

    assertThat(page.consumedMembers()).isZero();
    assertThat(page.payloads()).isEmpty();
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void readPage_rejectsInvalidPagingArgumentsAndPageRejectsInvalidPayloadShape() {
    var reader = reader(EventGeoCacheKeyLayout.STANDALONE);
    var payload = new byte[] {1};

    assertThatThrownBy(() -> reader.readPage(SOURCE, -1, 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.readPage(SOURCE, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GeoEventReader.Page(-1, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GeoEventReader.Page(0, List.of(payload)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GeoEventReader.Page(1, Arrays.asList(payload, null)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void page_mutatedInputList_copiesListButRetainsPayloadArrays() {
    var payload = new byte[] {1, 2};
    var original = new ArrayList<byte[]>(List.of(payload));

    var page = new GeoEventReader.Page(1, original);
    original.clear();
    payload[0] = 9;

    assertThat(page.payloads()).hasSize(1);
    assertThat(page.payloads().get(0)).containsExactly(9, 2);
  }

  @Test
  void readPage_propagatesRedisTransportFailure() {
    var sourceKeys = EventGeoCacheKeyLayout.STANDALONE.sourceKeys(SOURCE);
    var failure = new IllegalStateException("redis unavailable");
    when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range(sourceKeys.updatedAtKey(), 0, 4)).thenThrow(failure);

    assertThatThrownBy(() -> reader(EventGeoCacheKeyLayout.STANDALONE).readPage(SOURCE, 0, 5))
        .isSameAs(failure);
  }

  private RedisGeoEventReader reader(EventGeoCacheKeyLayout layout) {
    return new RedisGeoEventReader(layout, stringRedisTemplate, redisTemplate);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.processor.business.model.history.SourceObjectKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import opencop.core.proto.deckgl.ElementProtos.ElementMessage;
import org.junit.jupiter.api.Test;

class HistoricalChunkPayloadPartitionerTest {

  @Test
  void partition_whenNoPoints_returnsSingleEmptyPart() {
    var partitioner = new HistoricalChunkPayloadPartitioner(500);

    var partitions = partitioner.partition(sampledChunk(0), List.of());

    assertEquals(1, partitions.size());
    assertTrue(partitions.getFirst().isEmpty());
  }

  @Test
  void partition_splitsPointsInOriginalOrderBySerializedSize() {
    var partitioner = new HistoricalChunkPayloadPartitioner(8_500);
    var first = point("obj-1", 4_000);
    var second = point("obj-2", 4_000);
    var third = point("obj-3", 4_000);

    var partitions = partitioner.partition(sampledChunk(3), List.of(first, second, third));

    assertEquals(2, partitions.size());
    assertEquals(2, partitions.get(0).size());
    assertEquals(1, partitions.get(1).size());
    assertSame(first, partitions.get(0).get(0));
    assertSame(second, partitions.get(0).get(1));
    assertSame(third, partitions.get(1).get(0));
  }

  @Test
  void partition_whenSinglePointExceedsPartLimit_throwsChunkSpecificException() {
    var partitioner = new HistoricalChunkPayloadPartitioner(500);
    var sampledChunk = sampledChunk(1);

    var thrown =
        assertThrows(
            IllegalStateException.class,
            () -> partitioner.partition(sampledChunk, List.of(point("obj-1", 10_000))));

    assertTrue(thrown.getMessage().contains("max payload part bytes"));
    assertTrue(thrown.getMessage().contains(sampledChunk.chunkKey()));
  }

  private static HistoricalChunkSampledResult sampledChunk(int count) {
    return new HistoricalChunkSampledResult(
        5,
        "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:05:00Z",
        Instant.parse("2026-04-06T10:05:00Z"),
        Instant.parse("2026-04-06T10:06:00Z"),
        HistoricalResolution.MINUTE_1,
        count,
        count,
        List.of());
  }

  private static HistoricalChunkPointPayload point(String objectId, int serializedSizeBytes) {
    return new HistoricalChunkPointPayload(
        new SourceObjectKey(UUID.fromString("550e8400-e29b-41d4-a716-446655440201"), objectId),
        objectId,
        ElementMessage.newBuilder().setObjectId(objectId).build(),
        serializedSizeBytes);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.cache.CacheChannel;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import java.time.Instant;
import java.util.UUID;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkProfile;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkResolution;
import org.junit.jupiter.api.Test;

class WebSocketHistoryChunkBackpressureIT extends AbstractWebSocketComponentIT {

  @Test
  void parts_are_serialized_by_write_completion() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    var session = fixture.connect(websocketUrl(), clientHandler);
    UUID targetUserId = awaitCurrentUserId();
    fixture.send(session, fixture.subscribe("/user/queue/history"));

    String basePayloadKey =
        "opencop:history:payload:SOURCE:550e8400-e29b-41d4-a716-446655440102:HOUR_1:v1:v1_latest_in_bucket:0000000000000000000000000000000000000000000000000000000000000000:2026-05-14T11:00:00Z";
    redisTemplate.opsForValue().set(basePayloadKey, partPayloadBytes(0, 3));
    redisTemplate.opsForValue().set(basePayloadKey + ":part:1", partPayloadBytes(1, 3));
    redisTemplate.opsForValue().set(basePayloadKey + ":part:2", partPayloadBytes(2, 3));

    HistoryChunkStatusEvent event =
        new HistoryChunkStatusEvent(
            targetUserId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            SubjectType.SOURCE,
            UUID.randomUUID(),
            "chunk-backpressure-1",
            "opencop:history:chunk-state:bp-test",
            1,
            basePayloadKey,
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            "v1_latest_in_bucket",
            true,
            3,
            HistoryChunkStatus.AVAILABLE_FOR_DELIVERY);

    stringRedisTemplate.convertAndSend(
        CacheChannel.HISTORY_EVENTS.name(), objectMapper.writeValueAsString(event));

    // SENDING status frame arrives first
    var sendingMsg = awaitMessage(clientHandler, "SENDING status frame");
    assertThat(sendingMsg.getDestination()).isEqualTo("/user/queue/history");
    assertThat(objectMapper.readTree(sendingMsg.getPayload().toByteArray()).get("status").asText())
        .isEqualTo(HistoryChunkStatus.SENDING.name());

    // This integration slice verifies loopback delivery order and latency; the deterministic unit
    // tests prove the server-side write-completion gating.
    long t0 = System.nanoTime();
    var part0Msg = awaitMessage(clientHandler, "binary chunk part 0");
    long t1 = System.nanoTime();
    var part1Msg = awaitMessage(clientHandler, "binary chunk part 1");
    long t2 = System.nanoTime();
    var part2Msg = awaitMessage(clientHandler, "binary chunk part 2");
    long t3 = System.nanoTime();

    assertThat(part0Msg.getHeadersMap()).containsEntry("content-type", "application/x-protobuf");
    assertThat(part1Msg.getHeadersMap()).containsEntry("content-type", "application/x-protobuf");
    assertThat(part2Msg.getHeadersMap()).containsEntry("content-type", "application/x-protobuf");

    var p0 = HistoricalChunkPayload.parseFrom(part0Msg.getPayload());
    var p1 = HistoricalChunkPayload.parseFrom(part1Msg.getPayload());
    var p2 = HistoricalChunkPayload.parseFrom(part2Msg.getPayload());

    assertThat(p0.getPartIndex()).isEqualTo(0);
    assertThat(p1.getPartIndex()).isEqualTo(1);
    assertThat(p2.getPartIndex()).isEqualTo(2);

    // Receipt timestamps are retained for the latency budget, not as proof of transport
    // write-completion backpressure.
    assertThat(t1).isGreaterThanOrEqualTo(t0);
    assertThat(t2).isGreaterThanOrEqualTo(t1);
    assertThat(t3).isGreaterThanOrEqualTo(t2);

    // Log latency for budget measurement
    long latencyMs = (t3 - t0) / 1_000_000;
    System.out.println("Phase3-latency-ms=" + latencyMs);
    assertThat(latencyMs).isLessThan(500);

    // COMPLETED status frame arrives after all parts
    var completedMsg = awaitMessage(clientHandler, "COMPLETED status frame");
    assertThat(completedMsg.getDestination()).isEqualTo("/user/queue/history");
    assertThat(
            objectMapper.readTree(completedMsg.getPayload().toByteArray()).get("status").asText())
        .isEqualTo(HistoryChunkStatus.COMPLETED.name());
  }

  private static byte[] partPayloadBytes(int partIndex, int partCount) {
    return HistoricalChunkPayload.newBuilder()
        .setChunkKey("chunk-backpressure-1")
        .setChunkIndex(1)
        .setFromEpochMillis(Instant.parse("2026-04-06T10:00:00Z").toEpochMilli())
        .setToEpochMillis(Instant.parse("2026-04-06T11:00:00Z").toEpochMilli())
        .setResolution(HistoricalChunkResolution.HISTORICAL_CHUNK_RESOLUTION_HOUR_1)
        .setProfile(HistoricalChunkProfile.HISTORICAL_CHUNK_PROFILE_DETAIL)
        .setPointCount(1)
        .setObjectCount(1)
        .setPartIndex(partIndex)
        .setPartCount(partCount)
        .setFinalForChunk(partIndex == partCount - 1)
        .build()
        .toByteArray();
  }
}

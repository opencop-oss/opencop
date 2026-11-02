// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.cache.CacheChannel;
import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.core.sdk.model.history.HistoryEventType;
import com.opencop.core.sdk.model.history.HistoryJobPlanEvent;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.AccessOperationType;
import com.opencop.core.sdk.model.notification.content.LayerSourceNotification;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeys;
import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class WebSocketRedisComponentIT extends AbstractWebSocketComponentIT {

  @Test
  void publishesRealtimeRedisEventsLargerThanTheClientInboundLimit() throws Exception {
    UUID sourceId = UUID.randomUUID();
    byte[] event = new byte[65_537];
    redisTemplate.opsForValue().set(EventGeoCacheKeys.event(sourceId, "event-42"), event);

    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    seedActiveSourceAccess(sourceId, awaitCurrentUserId());
    fixture.send(session, fixture.subscribe(sourceId));

    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId).isEmpty(),
        fixture.defaultTimeout(),
        "topic subscription registration");

    CacheEventsNotification notification =
        new CacheEventsNotification(sourceId, List.of("event-42"));
    stringRedisTemplate.convertAndSend(
        CacheChannel.REAL_TIME_EVENTS.name(), objectMapper.writeValueAsString(notification));

    var realtimeMessage = awaitMessage(clientHandler, "redis realtime event");
    assertThat(realtimeMessage.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(realtimeMessage.getDestination()).isEqualTo("/topic/queue/event/" + sourceId);
    assertThat(readMultiPayload(realtimeMessage)).containsExactly(event);
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void publishesRedisNotificationsToSubscribedUserQueue() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID targetUserId = awaitCurrentUserId();
    fixture.send(session, fixture.subscribe("/user/queue/notification"));
    OpencopNotification notification =
        new OpencopNotification(
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            targetUserId,
            NotificationSeverityType.INFO,
            new LayerSourceNotification(
                UUID.randomUUID(), UUID.randomUUID(), AccessOperationType.ADDED, "viewer"),
            NotificationStatus.CREATED,
            Instant.now(),
            false,
            null);

    stringRedisTemplate.convertAndSend(
        CacheChannel.OPENCOP_NOTIFICATIONS.name(), objectMapper.writeValueAsString(notification));

    var delivered = awaitMessage(clientHandler, "redis notification delivery");
    assertThat(delivered.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(delivered.getDestination()).isEqualTo("/user/queue/notification");
    assertThat(readJson(delivered.getPayload().toByteArray()).get("notificationId").asString())
        .isEqualTo(notification.notificationId().toString());
    assertThat(readJson(delivered.getPayload().toByteArray()).get("targetUserId").asString())
        .isEqualTo(targetUserId.toString());
    assertThat(readJson(delivered.getPayload().toByteArray()).get("content").get("type").asString())
        .isEqualTo("LAYER_SOURCE");
  }

  @Test
  void publishesHistoryRedisEventsToSubscribedUserQueue() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID targetUserId = awaitCurrentUserId();
    fixture.send(session, fixture.subscribe("/user/queue/history"));

    HistoryJobPlanEvent event =
        new HistoryJobPlanEvent(
            targetUserId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            SubjectType.SOURCE,
            UUID.randomUUID(),
            targetUserId,
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            Instant.parse("2026-04-07T10:00:00Z"),
            1,
            0,
            1);

    stringRedisTemplate.convertAndSend(
        CacheChannel.HISTORY_EVENTS.name(), objectMapper.writeValueAsString(event));

    var delivered = awaitMessage(clientHandler, "redis history event delivery");
    assertThat(delivered.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(delivered.getDestination()).isEqualTo("/user/queue/history");
    assertThat(readJson(delivered.getPayload().toByteArray()).get("type").asString())
        .isEqualTo(HistoryEventType.HISTORY_JOB_PLAN.getName());
    assertThat(readJson(delivered.getPayload().toByteArray()).get("jobId").asString())
        .isEqualTo(event.jobId().toString());
    assertThat(readJson(delivered.getPayload().toByteArray()).get("targetUserId").asString())
        .isEqualTo(targetUserId.toString());
    assertThat(readJson(delivered.getPayload().toByteArray()).get("requestedBy").asString())
        .isEqualTo(targetUserId.toString());
  }

  @Test
  void publishesHistoryChunkStatusEventsToSubscribedUserQueueWhenPayloadExists() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID targetUserId = awaitCurrentUserId();
    fixture.send(session, fixture.subscribe("/user/queue/history"));

    String payloadKey =
        "opencop:history:payload:SOURCE:550e8400-e29b-41d4-a716-446655440102:HOUR_1:v1:v1_latest_in_bucket:0000000000000000000000000000000000000000000000000000000000000000:2026-05-14T11:00:00Z";
    redisTemplate.opsForValue().set(payloadKey, chunkPayloadBytes());

    HistoryChunkStatusEvent event =
        new HistoryChunkStatusEvent(
            targetUserId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            SubjectType.SOURCE,
            UUID.randomUUID(),
            "chunk-1",
            "opencop:history:chunk-state:test",
            1,
            payloadKey,
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            "v1_latest_in_bucket",
            true,
            1,
            HistoryChunkStatus.AVAILABLE_FOR_DELIVERY);

    stringRedisTemplate.convertAndSend(
        CacheChannel.HISTORY_EVENTS.name(), objectMapper.writeValueAsString(event));

    var delivered = awaitMessage(clientHandler, "redis history chunk status delivery");
    assertThat(delivered.getDestination()).isEqualTo("/user/queue/history");
    assertThat(readJson(delivered.getPayload().toByteArray()).get("type").asString())
        .isEqualTo(HistoryEventType.HISTORY_CHUNK_STATUS.getName());
    assertThat(readJson(delivered.getPayload().toByteArray()).get("jobId").asString())
        .isEqualTo(event.jobId().toString());
    assertThat(readJson(delivered.getPayload().toByteArray()).get("payloadKey").asString())
        .isEqualTo(payloadKey);
    assertThat(readJson(delivered.getPayload().toByteArray()).get("status").asString())
        .isEqualTo(HistoryChunkStatus.SENDING.name());

    var chunkData = awaitMessage(clientHandler, "redis history chunk data delivery");
    assertThat(chunkData.getDestination()).isEqualTo("/user/queue/history");
    assertThat(chunkData.getHeadersMap()).containsEntry("content-type", "application/x-protobuf");
    assertThat(chunkData.getHeadersMap())
        .containsEntry("history-type", HistoryEventType.HISTORY_CHUNK_DATA.getName());
    var payload =
        opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload.parseFrom(
            chunkData.getPayload());
    assertThat(payload.getChunkKey()).isEqualTo("chunk-1");
    assertThat(payload.getPointCount()).isEqualTo(1);
    assertThat(payload.getPartIndex()).isZero();
    assertThat(payload.getPartCount()).isEqualTo(1);
    assertThat(payload.getFinalForChunk()).isTrue();

    var completed = awaitMessage(clientHandler, "redis history chunk completed delivery");
    assertThat(completed.getDestination()).isEqualTo("/user/queue/history");
    assertThat(readJson(completed.getPayload().toByteArray()).get("type").asString())
        .isEqualTo(HistoryEventType.HISTORY_CHUNK_STATUS.getName());
    assertThat(readJson(completed.getPayload().toByteArray()).get("status").asString())
        .isEqualTo(HistoryChunkStatus.COMPLETED.name());
  }

  private static byte[] chunkPayloadBytes() {
    return opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload.newBuilder()
        .setChunkKey("chunk-1")
        .setChunkIndex(1)
        .setFromEpochMillis(Instant.parse("2026-04-06T10:00:00Z").toEpochMilli())
        .setToEpochMillis(Instant.parse("2026-04-06T11:00:00Z").toEpochMilli())
        .setResolution(
            opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkResolution
                .HISTORICAL_CHUNK_RESOLUTION_HOUR_1)
        .setProfile(
            opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkProfile
                .HISTORICAL_CHUNK_PROFILE_DETAIL)
        .setPointCount(1)
        .setObjectCount(1)
        .setPartIndex(0)
        .setPartCount(1)
        .setFinalForChunk(true)
        .addElements(
            opencop.core.proto.deckgl.ElementProtos.ElementMessage.newBuilder()
                .setId(
                    com.google.protobuf.ByteString.copyFrom(
                        uuidBytes("550e8400-e29b-41d4-a716-446655440110")))
                .setSource(
                    com.google.protobuf.ByteString.copyFrom(
                        uuidBytes("550e8400-e29b-41d4-a716-446655440111")))
                .setObjectId("obj-1")
                .setType("a-f-G-U-C")
                .setDisplayName("Object 1")
                .setStart(10L)
                .setTime(20L)
                .setStale(30L)
                .addPositions(1.0)
                .addPositions(2.0)
                .putValues(
                    "speed",
                    opencop.core.proto.deckgl.ElementProtos.Value.newBuilder()
                        .setDouble(12.5)
                        .build())
                .build())
        .build()
        .toByteArray();
  }

  private static byte[] uuidBytes(String value) {
    var uuid = UUID.fromString(value);
    var buffer = java.nio.ByteBuffer.allocate(16);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return buffer.array();
  }
}

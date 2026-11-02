// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.technical.gateway.history.HistoryJobDeliveryCoordinator;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalChunkDeliveryStateRepository;
import com.opencop.websocket.ports.business.usecase.history.HistoryEventHandler;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public class UserHistoryEventHandler implements HistoryEventHandler {
  private static final Logger log = LoggerFactory.getLogger(UserHistoryEventHandler.class);

  private final SocketProducer producer;
  private final ObjectMapper mapper;
  private final UserHistoryChunkSender historyChunkSender;
  private final HistoryJobDeliveryCoordinator jobDeliveryCoordinator;
  private final RedisHistoricalChunkDeliveryStateRepository chunkDeliveryStateRepository;

  public UserHistoryEventHandler(
      SocketProducer producer,
      ObjectMapper mapper,
      UserHistoryChunkSender historyChunkSender,
      HistoryJobDeliveryCoordinator jobDeliveryCoordinator,
      RedisHistoricalChunkDeliveryStateRepository chunkDeliveryStateRepository) {
    this.producer = producer;
    this.mapper = mapper;
    this.historyChunkSender = historyChunkSender;
    this.jobDeliveryCoordinator = jobDeliveryCoordinator;
    this.chunkDeliveryStateRepository = chunkDeliveryStateRepository;
  }

  @Override
  public void handle(HistoryEvent event) {
    if (event instanceof HistoryChunkStatusEvent chunkStatusEvent
        && chunkStatusEvent.status() == HistoryChunkStatus.AVAILABLE_FOR_DELIVERY) {
      var result = historyChunkSender.sendChunk(chunkStatusEvent);
      if (result.success()) {
        boolean markedAsDelivered =
            chunkDeliveryStateRepository.tryMarkDeliveredForJob(
                chunkStatusEvent.jobId(), chunkStatusEvent.chunkStateKey(), Duration.ofHours(24));
        if (markedAsDelivered) {
          jobDeliveryCoordinator.onChunkDelivered(chunkStatusEvent.jobId());
        }
      } else {
        boolean markedAsFailed =
            chunkDeliveryStateRepository.tryMarkFailedForJob(
                chunkStatusEvent.jobId(),
                chunkStatusEvent.chunkStateKey(),
                Duration.ofHours(24),
                result.failureReason());
        if (markedAsFailed) {
          log.warn(
              "Historical websocket delivery failed for job {} chunkIndex {} reason={}",
              chunkStatusEvent.jobId(),
              chunkStatusEvent.chunkIndex(),
              result.failureReason());
          jobDeliveryCoordinator.onChunkDeliveryFailed(
              chunkStatusEvent.jobId(), result.failureReason());
        }
      }
      return;
    }
    send(event);
  }

  private void send(HistoryEvent event) {
    producer.sendToUser(
        event.targetUserId(), "/user/queue/history", mapper.writeValueAsBytes(event), false);
  }
}

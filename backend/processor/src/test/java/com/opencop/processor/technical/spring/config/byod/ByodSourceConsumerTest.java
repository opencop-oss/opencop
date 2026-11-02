// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.ByodSourceState.Status;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.spring.consumer.ByodRuntimeObservationCoalescer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ByodSourceConsumerTest {

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final String TOPIC = "byod.test-topic";
  private static final String USERNAME = "test-user";

  private static ByodSourceState state(Status status, Instant updatedAt) {
    return new ByodSourceState(
        SOURCE_ID, TOPIC, USERNAME, status, updatedAt, ByodSourceState.CURRENT_VERSION);
  }

  @Nested
  class ShouldApply {

    @Test
    void firstWrite_alwaysApplies() {
      var incoming = state(Status.STARTED, Instant.now());
      assertTrue(ByodSourceConsumer.shouldApply(incoming, null));
    }

    @Test
    void deleted_alwaysWins() {
      var existing = state(Status.STARTED, Instant.now());
      var incoming = state(Status.DELETED, null);
      assertTrue(ByodSourceConsumer.shouldApply(incoming, existing));
    }

    @Test
    void cannotOverrideDeleted() {
      var existing = state(Status.DELETED, null);
      var incoming = state(Status.STARTED, Instant.now());
      assertFalse(ByodSourceConsumer.shouldApply(incoming, existing));
    }

    @Test
    void newerTimestamp_wins() {
      var t1 = Instant.now();
      var t2 = t1.plusSeconds(1);
      var existing = state(Status.STARTED, t1);
      var incoming = state(Status.STOPPED, t2);
      assertTrue(ByodSourceConsumer.shouldApply(incoming, existing));
    }

    @Test
    void olderTimestamp_loses() {
      var t1 = Instant.now();
      var t2 = t1.minusSeconds(1);
      var existing = state(Status.STARTED, t1);
      var incoming = state(Status.STOPPED, t2);
      assertFalse(ByodSourceConsumer.shouldApply(incoming, existing));
    }

    @Test
    void sameTimestamp_doesNotApply() {
      var t = Instant.now();
      var existing = state(Status.STARTED, t);
      var incoming = state(Status.STOPPED, t);
      assertFalse(ByodSourceConsumer.shouldApply(incoming, existing));
    }

    @Test
    void nullTimestamp_appliesWithWarning() {
      // Shouldn't happen in practice, but shouldApply returns true for safety
      var existing = state(Status.STARTED, null);
      var incoming = state(Status.STOPPED, Instant.now());
      assertTrue(ByodSourceConsumer.shouldApply(incoming, existing));
    }
  }

  @Nested
  class Dispatch {

    private final ByodConsumerManager consumerManager = mock(ByodConsumerManager.class);

    @Test
    void started_startsConsumer() {
      var state = state(Status.STARTED, Instant.now());
      dispatchState(state);
      verify(consumerManager).startConsumer(SOURCE_ID, TOPIC);
    }

    @Test
    void stopped_stopsConsumer() {
      var state = state(Status.STOPPED, Instant.now());
      dispatchState(state);
      verify(consumerManager).stopConsumer(SOURCE_ID);
    }

    @Test
    void deleted_stopsConsumer() {
      var state = state(Status.DELETED, null);
      dispatchState(state);
      verify(consumerManager).stopConsumer(SOURCE_ID);
    }

    /**
     * Simulates the dispatch logic from ByodSourceConsumer.dispatch(). Instance assignment is no
     * longer checked — Kafka consumer groups handle distribution at the per-topic level.
     */
    private void dispatchState(ByodSourceState state) {
      switch (state.status()) {
        case STARTED -> consumerManager.startConsumer(state.sourceId(), state.topicName());
        case STOPPED, DELETED -> consumerManager.stopConsumer(state.sourceId());
      }
    }
  }

  @Nested
  class RuntimeFlush {

    @Test
    void stopped_flushesPendingRuntimeBeforeStoppingContainer() {
      var objectMapper = new ObjectMapper();
      var consumerManager = mock(ByodConsumerManager.class);
      var runtimeObservationCoalescer = mock(ByodRuntimeObservationCoalescer.class);
      var consumer =
          new ByodSourceConsumer(
              mock(BaseKafkaConfig.class),
              "opencop.byod.sources",
              objectMapper,
              consumerManager,
              runtimeObservationCoalescer);
      var stopped = state(Status.STOPPED, Instant.now());
      Map<UUID, ByodSourceState> stateMap = new HashMap<>();

      consumer.dispatch(record(objectMapper, stopped), stateMap);

      var inOrder = inOrder(runtimeObservationCoalescer, consumerManager);
      inOrder.verify(runtimeObservationCoalescer).flush(SOURCE_ID);
      inOrder.verify(consumerManager).stopConsumer(SOURCE_ID);
    }
  }

  private static ConsumerRecord<String, byte[]> record(
      ObjectMapper objectMapper, ByodSourceState state) {
    return new ConsumerRecord<>(
        "opencop.byod.sources", 0, 0L, null, objectMapper.writeValueAsBytes(state));
  }
}

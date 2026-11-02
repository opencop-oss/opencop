// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;

class ByodDeadLetterConsumerManagerTest {

  private ByodDeadLetterListenerContainerFactory factory;
  private KafkaConsumersProperties kafkaConsumersProperties;
  private ByodDeadLetterConsumerManager manager;

  @SuppressWarnings("unchecked")
  private KafkaMessageListenerContainer<String, byte[]> mockContainer() {
    return mock(KafkaMessageListenerContainer.class);
  }

  @BeforeEach
  void setUp() {
    factory = mock(ByodDeadLetterListenerContainerFactory.class);
    kafkaConsumersProperties = new KafkaConsumersProperties();
    kafkaConsumersProperties.getDltIndexing().setEnabled(true);
    manager = new ByodDeadLetterConsumerManager(factory, kafkaConsumersProperties);
  }

  @Test
  void startConsumerCreatesAndStartsDltContainer() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var dltContainer = mockContainer();
    when(factory.create(sourceId, topic)).thenReturn(dltContainer);

    manager.startConsumer(sourceId, topic);

    verify(dltContainer).start();
    assertTrue(manager.isRunning(sourceId));
    assertEquals(1, manager.activeCount());
  }

  @Test
  void startConsumerIsIdempotentForSameSource() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var dltContainer = mockContainer();
    when(factory.create(sourceId, topic)).thenReturn(dltContainer);

    manager.startConsumer(sourceId, topic);
    manager.startConsumer(sourceId, topic);

    verify(factory, times(1)).create(sourceId, topic);
    verify(dltContainer, times(1)).start();
    assertEquals(1, manager.activeCount());
  }

  @Test
  void stopConsumerStopsAndRemovesDltContainer() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var dltContainer = mockContainer();
    when(factory.create(sourceId, topic)).thenReturn(dltContainer);
    manager.startConsumer(sourceId, topic);

    manager.stopConsumer(sourceId);

    verify(dltContainer).stop();
    assertFalse(manager.isRunning(sourceId));
    assertEquals(0, manager.activeCount());
  }

  @Test
  void stopConsumerIsNoOpWhenNoDltContainerRuns() {
    var sourceId = UUID.randomUUID();

    manager.stopConsumer(sourceId);

    verify(factory, never()).create(any(), any());
    assertEquals(0, manager.activeCount());
  }

  @Test
  void disabledDltIndexingDoesNotCreateContainer() {
    kafkaConsumersProperties.getDltIndexing().setEnabled(false);
    var sourceId = UUID.randomUUID();

    manager.startConsumer(sourceId, "opencop.byod.test." + sourceId);

    verify(factory, never()).create(any(), any());
    assertFalse(manager.isRunning(sourceId));
    assertEquals(0, manager.activeCount());
  }
}

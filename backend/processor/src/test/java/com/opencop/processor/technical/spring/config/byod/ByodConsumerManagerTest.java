// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;

class ByodConsumerManagerTest {

  private ByodListenerContainerFactory factory;
  private ByodDeadLetterConsumerManager deadLetterConsumerManager;
  private ByodConsumerProperties properties;
  private ByodConsumerManager manager;

  @SuppressWarnings("unchecked")
  private KafkaMessageListenerContainer<String, byte[]> mockContainer() {
    return mock(KafkaMessageListenerContainer.class);
  }

  @BeforeEach
  void setUp() {
    factory = mock(ByodListenerContainerFactory.class);
    deadLetterConsumerManager = mock(ByodDeadLetterConsumerManager.class);
    properties =
        new ByodConsumerProperties(
            100, 2, 1_000, "earliest", 3, 45_000, 3_000, 300_000, 1, 52_428_800);
    manager = new ByodConsumerManager(factory, deadLetterConsumerManager, properties);
    manager.setReady(true);
  }

  @Test
  void startConsumer_createsAndStartsContainer() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var container = mockContainer();
    when(factory.create(sourceId, topic)).thenReturn(container);

    manager.startConsumer(sourceId, topic);

    verify(deadLetterConsumerManager).startConsumer(sourceId, topic);
    verify(container).start();
    assertTrue(manager.isRunning(sourceId));
    assertEquals(1, manager.activeCount());
  }

  @Test
  void startConsumer_idempotent_doesNotCreateTwice() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var container = mockContainer();
    when(factory.create(sourceId, topic)).thenReturn(container);

    manager.startConsumer(sourceId, topic);
    manager.startConsumer(sourceId, topic);

    verify(factory, times(1)).create(sourceId, topic);
    verify(deadLetterConsumerManager, times(1)).startConsumer(sourceId, topic);
    assertEquals(1, manager.activeCount());
  }

  @Test
  void stopConsumer_stopsAndRemovesContainer() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var container = mockContainer();
    when(factory.create(sourceId, topic)).thenReturn(container);
    manager.startConsumer(sourceId, topic);

    manager.stopConsumer(sourceId);

    verify(container).stop();
    verify(deadLetterConsumerManager).stopConsumer(sourceId);
    assertFalse(manager.isRunning(sourceId));
    assertEquals(0, manager.activeCount());
  }

  @Test
  void stopConsumer_idempotent_noOpWhenNotRunning() {
    manager.stopConsumer(UUID.randomUUID());
    verify(deadLetterConsumerManager, never()).stopConsumer(any());
    assertEquals(0, manager.activeCount());
  }

  @Test
  void stopAll_stopsAllContainersAndSetsShuttingDown() {
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    var c1 = mockContainer();
    var c2 = mockContainer();
    when(factory.create(eq(id1), any())).thenReturn(c1);
    when(factory.create(eq(id2), any())).thenReturn(c2);

    manager.startConsumer(id1, "topic1");
    manager.startConsumer(id2, "topic2");
    manager.stopAll();

    verify(c1).stop();
    verify(c2).stop();
    verify(deadLetterConsumerManager).stopConsumer(id1);
    verify(deadLetterConsumerManager).stopConsumer(id2);
    assertEquals(0, manager.activeCount());
  }

  @Test
  void shuttingDown_refusesNewContainers() {
    manager.stopAll();

    var sourceId = UUID.randomUUID();
    manager.startConsumer(sourceId, "topic");

    verify(factory, never()).create(any(), any());
    assertFalse(manager.isRunning(sourceId));
  }

  @Test
  void readinessGate_refusesContainersBeforeReady() {
    manager.setReady(false);

    var sourceId = UUID.randomUUID();
    manager.startConsumer(sourceId, "topic");

    verify(factory, never()).create(any(), any());
    assertFalse(manager.isRunning(sourceId));
  }

  @Test
  void maxContainersCap_refusesWhenAtLimit() {
    for (int i = 0; i < 3; i++) {
      var id = UUID.randomUUID();
      var container = mockContainer();
      when(factory.create(eq(id), any())).thenReturn(container);
      manager.startConsumer(id, "topic-" + i);
    }
    assertEquals(3, manager.activeCount());

    var overflow = UUID.randomUUID();
    manager.startConsumer(overflow, "overflow-topic");

    assertFalse(manager.isRunning(overflow));
    assertEquals(3, manager.activeCount());
  }

  @Test
  void sourceContainerStartFailureRollsBackDltContainer() {
    var sourceId = UUID.randomUUID();
    var topic = "opencop.byod.test." + sourceId;
    var container = mockContainer();
    when(factory.create(sourceId, topic)).thenReturn(container);
    doThrow(new IllegalStateException("cannot start")).when(container).start();

    assertThrows(IllegalStateException.class, () -> manager.startConsumer(sourceId, topic));

    verify(deadLetterConsumerManager).startConsumer(sourceId, topic);
    verify(deadLetterConsumerManager).stopConsumer(sourceId);
    assertFalse(manager.isRunning(sourceId));
  }
}

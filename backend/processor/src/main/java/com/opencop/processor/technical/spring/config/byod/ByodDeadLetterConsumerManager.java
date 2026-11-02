// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class ByodDeadLetterConsumerManager {

  private static final Logger log = LoggerFactory.getLogger(ByodDeadLetterConsumerManager.class);

  private final ByodDeadLetterListenerContainerFactory factory;
  private final KafkaConsumersProperties kafkaConsumersProperties;
  private final ConcurrentHashMap<UUID, Entry> containers = new ConcurrentHashMap<>();
  private final Object lock = new Object();

  ByodDeadLetterConsumerManager(
      ByodDeadLetterListenerContainerFactory factory,
      KafkaConsumersProperties kafkaConsumersProperties) {
    this.factory = factory;
    this.kafkaConsumersProperties = kafkaConsumersProperties;
  }

  public void startConsumer(UUID sourceId, String topicName) {
    if (!isDltIndexingEnabled()) {
      log.debug("BYOD DLT indexing is disabled; skipping sourceId={}", sourceId);
      return;
    }
    synchronized (lock) {
      if (containers.containsKey(sourceId)) {
        log.debug("BYOD DLT index container already running for sourceId={}", sourceId);
        return;
      }

      var container = factory.create(sourceId, topicName);
      containers.put(sourceId, new Entry(sourceId, topicName, container));
      try {
        container.start();
      } catch (Exception e) {
        containers.remove(sourceId);
        try {
          container.stop();
        } catch (Exception suppressed) {
          e.addSuppressed(suppressed);
        }
        throw e;
      }
      log.info("Started BYOD DLT index container for sourceId={}, topic={}", sourceId, topicName);
    }
  }

  public void stopConsumer(UUID sourceId) {
    synchronized (lock) {
      var entry = containers.remove(sourceId);
      if (entry == null) {
        log.debug("No BYOD DLT index container to stop for sourceId={}", sourceId);
        return;
      }
      entry.container.stop();
      log.info("Stopped BYOD DLT index container for sourceId={}", sourceId);
    }
  }

  public void stopAll() {
    List<Entry> toStop;
    synchronized (lock) {
      toStop = new ArrayList<>(containers.values());
      containers.clear();
    }
    for (var entry : toStop) {
      entry.container.stop();
      log.info("Stopped BYOD DLT index container for sourceId={}", entry.sourceId());
    }
  }

  public boolean isRunning(UUID sourceId) {
    return containers.containsKey(sourceId);
  }

  public Map<UUID, Entry> getAllStatuses() {
    return Map.copyOf(containers);
  }

  int activeCount() {
    return containers.size();
  }

  private boolean isDltIndexingEnabled() {
    return Boolean.TRUE.equals(kafkaConsumersProperties.getDltIndexing().getEnabled());
  }

  public record Entry(
      UUID sourceId, String topicName, KafkaMessageListenerContainer<String, byte[]> container) {}
}

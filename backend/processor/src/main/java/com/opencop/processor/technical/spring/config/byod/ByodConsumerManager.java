// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Registry for per-topic BYOD listener containers. Thread-safe with idempotent start/stop, shutdown
 * gate, readiness gate, and max-containers cap.
 */
@Component
public class ByodConsumerManager {

  private static final Logger log = LoggerFactory.getLogger(ByodConsumerManager.class);

  private final ByodListenerContainerFactory factory;
  private final ByodDeadLetterConsumerManager deadLetterConsumerManager;
  private final ByodConsumerProperties properties;
  // ConcurrentHashMap (not plain HashMap) because public read methods (isRunning, getAllStatuses,
  // activeCount) intentionally bypass the lock for non-blocking status queries. ConcurrentHashMap
  // ensures those unsynchronized reads don't see a corrupted map or throw
  // ConcurrentModificationException.
  private final ConcurrentHashMap<UUID, Entry> containers = new ConcurrentHashMap<>();
  private final Object lock = new Object();
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
  private volatile boolean ready = false;

  ByodConsumerManager(
      ByodListenerContainerFactory factory,
      ByodDeadLetterConsumerManager deadLetterConsumerManager,
      ByodConsumerProperties properties) {
    this.factory = factory;
    this.deadLetterConsumerManager = deadLetterConsumerManager;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onApplicationReady() {
    ready = true;
    log.info("ByodConsumerManager ready to accept listener containers");
  }

  public void startConsumer(UUID sourceId, String topicName) {
    if (!ready) {
      log.debug("Ignoring startConsumer for sourceId={} — application not ready", sourceId);
      return;
    }
    if (shuttingDown.get()) {
      log.debug("Ignoring startConsumer for sourceId={} — shutting down", sourceId);
      return;
    }

    synchronized (lock) {
      if (shuttingDown.get()) {
        log.debug("Ignoring startConsumer for sourceId={} — shutting down (re-check)", sourceId);
        return;
      }
      if (containers.containsKey(sourceId)) {
        log.debug("Listener container already running for sourceId={}", sourceId);
        return;
      }
      if (containers.size() >= properties.maxContainers()) {
        log.warn(
            "Max containers cap ({}) reached — refusing sourceId={}",
            properties.maxContainers(),
            sourceId);
        return;
      }

      var container = factory.create(sourceId, topicName);
      var entry = new Entry(sourceId, topicName, container);
      containers.put(sourceId, entry);
      try {
        deadLetterConsumerManager.startConsumer(sourceId, topicName);
        container.start();
      } catch (Exception e) {
        containers.remove(sourceId);
        deadLetterConsumerManager.stopConsumer(sourceId);
        try {
          container.stop();
        } catch (Exception suppressed) {
          e.addSuppressed(suppressed);
        }
        throw e;
      }
      log.info("Started listener container for sourceId={}, topic={}", sourceId, topicName);
    }
  }

  public void stopConsumer(UUID sourceId) {
    synchronized (lock) {
      var entry = containers.remove(sourceId);
      if (entry == null) {
        log.debug("No listener container to stop for sourceId={}", sourceId);
        return;
      }
      entry.container.stop();
      deadLetterConsumerManager.stopConsumer(sourceId);
      log.info("Stopped listener container for sourceId={}", sourceId);
    }
  }

  public void stopAll() {
    shuttingDown.set(true);
    // Copy + clear under lock, then stop outside — avoids holding the lock during potentially
    // slow container stops (offset commits, consumer close) which could exceed Docker's SIGTERM
    // grace period with many containers.
    List<Entry> toStop;
    synchronized (lock) {
      toStop = new ArrayList<>(containers.values());
      containers.clear();
    }
    for (var entry : toStop) {
      entry.container.stop();
      deadLetterConsumerManager.stopConsumer(entry.sourceId());
      log.info("Stopped listener container for sourceId={}", entry.sourceId());
    }
  }

  public boolean isRunning(UUID sourceId) {
    return containers.containsKey(sourceId);
  }

  public Map<UUID, Entry> getAllStatuses() {
    return Map.copyOf(containers);
  }

  // Visible for testing
  boolean isReady() {
    return ready;
  }

  // Visible for testing
  void setReady(boolean ready) {
    this.ready = ready;
  }

  // Visible for testing
  int activeCount() {
    return containers.size();
  }

  public record Entry(
      UUID sourceId, String topicName, KafkaMessageListenerContainer<String, byte[]> container) {}
}

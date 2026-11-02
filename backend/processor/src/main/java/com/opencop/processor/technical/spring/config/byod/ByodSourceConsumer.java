// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.spring.consumer.ByodRuntimeObservationCoalescer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes the compacted {@code opencop.byod.sources} topic to converge local listener containers.
 * Replays from beginning on startup (deterministic), then tails for ongoing changes.
 *
 * <p>Uses {@code assign()} (not {@code subscribe()}) for the sources topic — no coordinator needed.
 * All Processor instances see all source state; per-topic consumer groups handle deduplication.
 */
@Component
public class ByodSourceConsumer implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(ByodSourceConsumer.class);
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final Duration TAIL_POLL_TIMEOUT = Duration.ofSeconds(1);

  private final BaseKafkaConfig baseKafkaConfig;
  private final String sourcesTopic;
  private final ObjectMapper objectMapper;
  private final ByodConsumerManager consumerManager;
  private final ByodRuntimeObservationCoalescer runtimeObservationCoalescer;

  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
  private final AtomicReference<KafkaConsumer<String, byte[]>> consumerRef =
      new AtomicReference<>();
  private volatile boolean running = false;
  private Thread pollThread;

  ByodSourceConsumer(
      BaseKafkaConfig baseKafkaConfig,
      @Value("${opencop.byod.sources-topic}") String sourcesTopic,
      ObjectMapper objectMapper,
      ByodConsumerManager consumerManager,
      ByodRuntimeObservationCoalescer runtimeObservationCoalescer) {
    this.baseKafkaConfig = baseKafkaConfig;
    this.sourcesTopic = sourcesTopic;
    this.objectMapper = objectMapper;
    this.consumerManager = consumerManager;
    this.runtimeObservationCoalescer = runtimeObservationCoalescer;
  }

  @Override
  public void start() {
    log.info("Starting ByodSourceConsumer for topic={}", sourcesTopic);
    running = true;

    // Single virtual thread — no pinning contention risk (only one consumer per instance)
    pollThread = Thread.ofVirtual().name("byod-source-consumer").start(this::runConsumerLoop);
  }

  private void runConsumerLoop() {
    long backoffMs = 1_000;
    final long maxBackoffMs = 60_000;

    while (!shuttingDown.get()) {
      try {
        runConsumerLoopInner();
        backoffMs = 1_000;
      } catch (Exception e) {
        if (shuttingDown.get()) return;
        log.error("ByodSourceConsumer loop failed, retrying in {}ms", backoffMs, e);
        try {
          Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
        backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
      }
    }
    running = false;
  }

  private void runConsumerLoopInner() {
    var props =
        new HashMap<>(
            Map.<String, Object>of(
                // group.id not strictly needed with assign(), but kept for broker observability
                ConsumerConfig.GROUP_ID_CONFIG,
                "processor-byod-sources",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"));
    props.putAll(baseKafkaConfig.props());

    try (var consumer = new KafkaConsumer<String, byte[]>(props)) {
      consumerRef.set(consumer);

      // assign() — no group coordinator, no rebalance
      var partitionInfos = consumer.partitionsFor(sourcesTopic);
      log.info(
          "Assigned BYOD source topic partitions: topic={}, partitionCount={}",
          sourcesTopic,
          partitionInfos.size());
      var partitions =
          partitionInfos.stream()
              .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
              .collect(Collectors.toList());
      consumer.assign(partitions);

      // Capture end offsets before replay, then seek to beginning
      var endOffsets = consumer.endOffsets(partitions);
      consumer.seekToBeginning(partitions);

      // Replay to captured end offsets
      var stateMap = replay(consumer, partitions, endOffsets);
      log.info("BYOD source replay built state map: trackedSources={}", stateMap.size());

      // Wait for ByodConsumerManager readiness (ApplicationReadyEvent)
      while (!shuttingDown.get() && !consumerManager.isReady()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }

      // Start listener containers for all STARTED sources (Kafka consumer groups handle
      // distribution — each source's partition is assigned to exactly one group member)
      if (!shuttingDown.get()) {
        var startedCount = new int[] {0};

        stateMap.forEach(
            (sourceId, state) -> {
              if (state.status() == ByodSourceState.Status.STARTED) {
                consumerManager.startConsumer(sourceId, state.topicName());
                startedCount[0]++;
              }
            });
        log.info("Replay complete — started {} STARTED BYOD sources", startedCount[0]);
      }

      // Tail for ongoing changes (backoff carries across reconnects — acceptable)
      while (!shuttingDown.get()) {
        try {
          var records = consumer.poll(TAIL_POLL_TIMEOUT);
          if (!records.isEmpty()) {
            log.info("Consumed BYOD source tail batch: records={}", records.count());
          }
          for (var record : records) {
            if (shuttingDown.get()) break;
            dispatch(record, stateMap);
          }
        } catch (WakeupException e) {
          if (!shuttingDown.get()) throw e;
          break;
        }
      }
    } finally {
      consumerRef.set(null);
    }
  }

  private Map<UUID, ByodSourceState> replay(
      KafkaConsumer<String, byte[]> consumer,
      java.util.List<TopicPartition> partitions,
      Map<TopicPartition, Long> endOffsets) {

    var stateMap = new HashMap<UUID, ByodSourceState>();

    // Check if topic is empty (all end offsets = 0)
    boolean allEmpty = endOffsets.values().stream().allMatch(offset -> offset == 0);
    if (allEmpty) {
      log.info("Source topic {} is empty — nothing to replay", sourcesTopic);
      return stateMap;
    }

    int replayedRecords = 0;
    while (!shuttingDown.get()) {
      var records = consumer.poll(POLL_TIMEOUT);
      if (!records.isEmpty() && log.isDebugEnabled()) {
        log.debug("Consumed BYOD replay batch: records={}", records.count());
      }
      for (var record : records) {
        replayedRecords++;
        applyToStateMap(record, stateMap);
      }

      // Check if all partitions have reached their captured end offsets
      boolean replayComplete = true;
      for (var tp : partitions) {
        long endOffset = endOffsets.getOrDefault(tp, 0L);
        if (endOffset > 0 && consumer.position(tp) < endOffset) {
          replayComplete = false;
          break;
        }
      }
      if (replayComplete) break;
    }

    log.info(
        "Completed BYOD source replay: records={}, trackedSources={}",
        replayedRecords,
        stateMap.size());
    return stateMap;
  }

  private void applyToStateMap(
      ConsumerRecord<String, byte[]> record, Map<UUID, ByodSourceState> stateMap) {
    var state = deserialize(record);
    if (state == null) return;

    var existing = stateMap.get(state.sourceId());
    if (shouldApply(state, existing)) {
      stateMap.put(state.sourceId(), state);
      if (log.isDebugEnabled()) {
        log.debug(
            "Replayed BYOD source state: sourceId={}, topic={}, status={}, updatedAt={}",
            state.sourceId(),
            state.topicName(),
            state.status(),
            state.updatedAt());
      }
    }
  }

  void dispatch(ConsumerRecord<String, byte[]> record, Map<UUID, ByodSourceState> stateMap) {
    var state = deserialize(record);
    if (state == null) return;

    var existing = stateMap.get(state.sourceId());
    if (!shouldApply(state, existing)) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Ignored stale BYOD source update: sourceId={}, topic={}, status={}, updatedAt={}",
            state.sourceId(),
            state.topicName(),
            state.status(),
            state.updatedAt());
      }
      return;
    }

    stateMap.put(state.sourceId(), state);
    log.info(
        "Applying BYOD source update: sourceId={}, topic={}, status={}, updatedAt={}",
        state.sourceId(),
        state.topicName(),
        state.status(),
        state.updatedAt());

    switch (state.status()) {
      case STARTED -> consumerManager.startConsumer(state.sourceId(), state.topicName());
      case STOPPED, DELETED -> {
        runtimeObservationCoalescer.flush(state.sourceId());
        consumerManager.stopConsumer(state.sourceId());
      }
    }
  }

  /**
   * DELETED always wins unconditionally. For non-DELETED, ignore updates with updatedAt <= current.
   */
  static boolean shouldApply(ByodSourceState incoming, ByodSourceState existing) {
    if (existing == null) return true;
    if (incoming.status() == ByodSourceState.Status.DELETED) return true;
    if (existing.status() == ByodSourceState.Status.DELETED) return false;
    if (incoming.updatedAt() == null || existing.updatedAt() == null) {
      log.warn("Unexpected null updatedAt in shouldApply: sourceId={}", incoming.sourceId());
      return true;
    }
    return incoming.updatedAt().isAfter(existing.updatedAt());
  }

  private ByodSourceState deserialize(ConsumerRecord<String, byte[]> record) {
    try {
      return objectMapper.readValue(record.value(), ByodSourceState.class);
    } catch (Exception e) {
      log.warn(
          "Failed to deserialize ByodSourceState from topic={}, partition={}, offset={}, key={}: {}",
          record.topic(),
          record.partition(),
          record.offset(),
          record.key(),
          e.getMessage());
      return null;
    }
  }

  @Override
  public void stop() {
    log.info("Stopping ByodSourceConsumer");
    shuttingDown.set(true);
    // wakeup() is the Kafka-documented mechanism for breaking poll() from another thread
    var c = consumerRef.get();
    if (c != null) c.wakeup();
    if (pollThread != null) {
      pollThread.interrupt(); // fallback for Thread.sleep() calls
      try {
        pollThread.join(5_000);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    }
    runtimeObservationCoalescer.flushAll();
    consumerManager.stopAll();
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }
}

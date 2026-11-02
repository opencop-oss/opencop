// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "opencop.messaging-broker")
public class KafkaConsumersProperties {

  private Map<String, ListenerProps> listeners;
  @NotNull private Duration producerAckTimeout = Duration.ofSeconds(10);
  private DeadLetterIndexingProps dltIndexing = new DeadLetterIndexingProps();

  public Map<String, ListenerProps> getListeners() {
    return listeners;
  }

  public void setListeners(Map<String, ListenerProps> listeners) {
    this.listeners = listeners;
  }

  public Duration getProducerAckTimeout() {
    return producerAckTimeout;
  }

  public void setProducerAckTimeout(Duration producerAckTimeout) {
    this.producerAckTimeout = producerAckTimeout;
  }

  public DeadLetterIndexingProps getDltIndexing() {
    return dltIndexing;
  }

  public void setDltIndexing(DeadLetterIndexingProps dltIndexing) {
    this.dltIndexing = dltIndexing;
  }

  @AssertTrue(message = "producerAckTimeout must be greater than zero")
  public boolean isProducerAckTimeoutPositive() {
    return producerAckTimeout != null
        && !producerAckTimeout.isZero()
        && !producerAckTimeout.isNegative();
  }

  public static class DeadLetterIndexingProps {
    private Boolean enabled = false;
    private String groupIdSuffix = "-dlt-indexer";
    private Set<String> enabledListeners = Set.of();
    private Integer concurrency = 1;
    private Integer maxPollRecords = 5000;

    /**
     * Forward-only by default. New DLT indexer consumer groups start at the tail and do not index
     * already-retained DLT records unless operators explicitly set {@code
     * DLT_INDEXING_AUTO_OFFSET_RESET=earliest} or reset offsets for a backfill.
     */
    private String autoOffsetReset = "latest";

    private Integer retryAttempts = 5;
    private Long retryInitialIntervalMs = 1_000L;
    private Double retryMultiplier = 2.0;
    private Long retryMaxIntervalMs = 60_000L;
    private Duration payloadRetention = Duration.ofDays(7);

    public Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
    }

    public String getGroupIdSuffix() {
      return groupIdSuffix;
    }

    public void setGroupIdSuffix(String groupIdSuffix) {
      this.groupIdSuffix = groupIdSuffix;
    }

    public Set<String> getEnabledListeners() {
      return enabledListeners;
    }

    public void setEnabledListeners(Set<String> enabledListeners) {
      this.enabledListeners = enabledListeners == null ? Set.of() : Set.copyOf(enabledListeners);
    }

    public Integer getConcurrency() {
      return concurrency;
    }

    public void setConcurrency(Integer concurrency) {
      this.concurrency = concurrency;
    }

    public Integer getMaxPollRecords() {
      return maxPollRecords;
    }

    public void setMaxPollRecords(Integer maxPollRecords) {
      this.maxPollRecords = maxPollRecords;
    }

    public String getAutoOffsetReset() {
      return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
      this.autoOffsetReset = autoOffsetReset;
    }

    public Integer getRetryAttempts() {
      return retryAttempts;
    }

    public void setRetryAttempts(Integer retryAttempts) {
      this.retryAttempts = retryAttempts;
    }

    public Long getRetryInitialIntervalMs() {
      return retryInitialIntervalMs;
    }

    public void setRetryInitialIntervalMs(Long retryInitialIntervalMs) {
      this.retryInitialIntervalMs = retryInitialIntervalMs;
    }

    public Double getRetryMultiplier() {
      return retryMultiplier;
    }

    public void setRetryMultiplier(Double retryMultiplier) {
      this.retryMultiplier = retryMultiplier;
    }

    public Long getRetryMaxIntervalMs() {
      return retryMaxIntervalMs;
    }

    public void setRetryMaxIntervalMs(Long retryMaxIntervalMs) {
      this.retryMaxIntervalMs = retryMaxIntervalMs;
    }

    public Duration getPayloadRetention() {
      return payloadRetention;
    }

    public void setPayloadRetention(Duration payloadRetention) {
      this.payloadRetention = payloadRetention;
    }
  }

  public static class ListenerProps {
    private String topic;
    private String groupId;
    private Integer concurrency = 1;
    private Boolean batch = true;
    private Integer maxPollRecords = 1;
    private Integer maxRetries = 3;
    private Integer maxWaitMs = 1000;
    private Integer minSizeBytes = 1_024_000;
    private Integer maxSizeBytes = 2_048_000;
    private String valueType;
    private String stage;
    private String autoOffsetReset;
    // When set, the listener's containers publish ListenerContainerIdleEvents on this cadence
    // whenever a poll returns no records. Only the watchbox listener sets it: idle events are the
    // detection heartbeat's no-traffic liveness evidence.
    private Duration idleEventInterval;
    // When true, transient retained-batch failures (infrastructure outages and database-proven
    // generation churn) retry in place instead of dead-lettering after maxRetries. Only Watchbox
    // sets it: its DLT is a tombstone, not a recovery queue, because the monotonic fold and live
    // window make later replay ineffective. Deterministic poison failures keep the DLT contract.
    private Boolean retryInPlaceFailures = false;

    public String getTopic() {
      return topic;
    }

    public void setTopic(String topic) {
      this.topic = topic;
    }

    public String getGroupId() {
      return groupId;
    }

    public void setGroupId(String groupId) {
      this.groupId = groupId;
    }

    public Integer getConcurrency() {
      return concurrency;
    }

    public void setConcurrency(Integer concurrency) {
      this.concurrency = concurrency;
    }

    public Boolean getBatch() {
      return batch;
    }

    public void setBatch(Boolean batch) {
      this.batch = batch;
    }

    public Integer getMaxPollRecords() {
      return maxPollRecords;
    }

    public void setMaxPollRecords(Integer maxPollRecords) {
      this.maxPollRecords = maxPollRecords;
    }

    public Integer getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
      this.maxRetries = maxRetries;
    }

    public Integer getMaxWaitMs() {
      return maxWaitMs;
    }

    public void setMaxWaitMs(Integer maxWaitMs) {
      this.maxWaitMs = maxWaitMs;
    }

    public Integer getMinSizeBytes() {
      return minSizeBytes;
    }

    public void setMinSizeBytes(Integer minSizeBytes) {
      this.minSizeBytes = minSizeBytes;
    }

    public Integer getMaxSizeBytes() {
      return maxSizeBytes;
    }

    public void setMaxSizeBytes(Integer maxSizeBytes) {
      this.maxSizeBytes = maxSizeBytes;
    }

    public String getValueType() {
      return valueType;
    }

    public void setValueType(String valueType) {
      this.valueType = valueType;
    }

    public String getStage() {
      return stage;
    }

    public void setStage(String stage) {
      this.stage = stage;
    }

    public String getAutoOffsetReset() {
      return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
      this.autoOffsetReset = autoOffsetReset;
    }

    public Duration getIdleEventInterval() {
      return idleEventInterval;
    }

    public void setIdleEventInterval(Duration idleEventInterval) {
      this.idleEventInterval = idleEventInterval;
    }

    public Boolean getRetryInPlaceFailures() {
      return retryInPlaceFailures;
    }

    public void setRetryInPlaceFailures(Boolean retryInPlaceFailures) {
      this.retryInPlaceFailures = retryInPlaceFailures;
    }
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import com.opencop.processor.technical.gateway.db.JdbcQueryTimeout;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("opencop.history.execution")
public class HistoricalExecutionProperties {

  private static final int DEFAULT_WORKER_POOL_SIZE = 8;
  private static final int DEFAULT_MAX_CONCURRENT_SUBJECTS_PER_JOB = 2;

  private Duration payloadTtl = Duration.ofMinutes(30);
  private Duration jobStateTtl = Duration.ofHours(24);
  private Duration admissionTtl = Duration.ofMinutes(45);
  private Duration maxRuntime = Duration.ofMinutes(30);
  @NotNull private Duration activeSnapshotQueryTimeout = Duration.ofMinutes(5);
  private Duration ttlRefreshInterval = Duration.ofMinutes(5);
  private Duration admissionRecoveryInterval = Duration.ofSeconds(10);
  private int workerPoolSize = DEFAULT_WORKER_POOL_SIZE;
  private int queueCapacity = 100;
  private int maxConcurrentChunksPerJob = 2;

  @Min(1)
  private int maxConcurrentSubjectsPerJob = DEFAULT_MAX_CONCURRENT_SUBJECTS_PER_JOB;

  private int maxPayloadPartBytes = 131_072;
  private int maxChunkPayloadBytes = 524_288;

  @Min(0)
  private int maxConsecutiveChunkFailures = 5;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double maxFailureRatio = 0.10;

  public Duration getPayloadTtl() {
    return payloadTtl;
  }

  public void setPayloadTtl(Duration payloadTtl) {
    this.payloadTtl = payloadTtl;
  }

  public Duration getJobStateTtl() {
    return jobStateTtl;
  }

  public void setJobStateTtl(Duration jobStateTtl) {
    this.jobStateTtl = jobStateTtl;
  }

  public Duration getAdmissionTtl() {
    return admissionTtl;
  }

  public void setAdmissionTtl(Duration admissionTtl) {
    this.admissionTtl = admissionTtl;
  }

  public Duration getMaxRuntime() {
    return maxRuntime;
  }

  public void setMaxRuntime(Duration maxRuntime) {
    this.maxRuntime = maxRuntime;
  }

  public Duration getActiveSnapshotQueryTimeout() {
    return activeSnapshotQueryTimeout;
  }

  public void setActiveSnapshotQueryTimeout(Duration activeSnapshotQueryTimeout) {
    this.activeSnapshotQueryTimeout = activeSnapshotQueryTimeout;
  }

  public Duration getTtlRefreshInterval() {
    return ttlRefreshInterval;
  }

  public void setTtlRefreshInterval(Duration ttlRefreshInterval) {
    this.ttlRefreshInterval = ttlRefreshInterval;
  }

  public Duration getAdmissionRecoveryInterval() {
    return admissionRecoveryInterval;
  }

  public void setAdmissionRecoveryInterval(Duration admissionRecoveryInterval) {
    this.admissionRecoveryInterval = admissionRecoveryInterval;
  }

  public int getWorkerPoolSize() {
    return workerPoolSize;
  }

  public void setWorkerPoolSize(int workerPoolSize) {
    this.workerPoolSize = workerPoolSize;
  }

  public int getQueueCapacity() {
    return queueCapacity;
  }

  public void setQueueCapacity(int queueCapacity) {
    this.queueCapacity = queueCapacity;
  }

  public int getMaxConcurrentChunksPerJob() {
    return maxConcurrentChunksPerJob;
  }

  public void setMaxConcurrentChunksPerJob(int maxConcurrentChunksPerJob) {
    this.maxConcurrentChunksPerJob = maxConcurrentChunksPerJob;
  }

  public int getMaxConcurrentSubjectsPerJob() {
    return maxConcurrentSubjectsPerJob;
  }

  public void setMaxConcurrentSubjectsPerJob(int maxConcurrentSubjectsPerJob) {
    this.maxConcurrentSubjectsPerJob = maxConcurrentSubjectsPerJob;
  }

  public int getMaxPayloadPartBytes() {
    return maxPayloadPartBytes;
  }

  public void setMaxPayloadPartBytes(int maxPayloadPartBytes) {
    this.maxPayloadPartBytes = maxPayloadPartBytes;
  }

  public int getMaxChunkPayloadBytes() {
    return maxChunkPayloadBytes;
  }

  public void setMaxChunkPayloadBytes(int maxChunkPayloadBytes) {
    this.maxChunkPayloadBytes = maxChunkPayloadBytes;
  }

  public int getMaxConsecutiveChunkFailures() {
    return maxConsecutiveChunkFailures;
  }

  public void setMaxConsecutiveChunkFailures(int maxConsecutiveChunkFailures) {
    this.maxConsecutiveChunkFailures = maxConsecutiveChunkFailures;
  }

  public double getMaxFailureRatio() {
    return maxFailureRatio;
  }

  public void setMaxFailureRatio(double maxFailureRatio) {
    this.maxFailureRatio = maxFailureRatio;
  }

  @AssertTrue(message = "workerPoolSize must be at least maxConcurrentSubjectsPerJob")
  public boolean isWorkerPoolSizeAtLeastMaxConcurrentSubjectsPerJob() {
    return workerPoolSize >= maxConcurrentSubjectsPerJob;
  }

  @AssertTrue(message = "admissionTtl must cover maxRuntime plus two ttlRefreshInterval periods")
  public boolean isAdmissionTtlLongEnoughForExecutionWindow() {
    if (admissionTtl == null || maxRuntime == null || ttlRefreshInterval == null) {
      return true;
    }
    Duration minimumAdmissionTtl = maxRuntime.plus(ttlRefreshInterval.multipliedBy(2));
    return admissionTtl.compareTo(minimumAdmissionTtl) >= 0;
  }

  @AssertTrue(message = "activeSnapshotQueryTimeout must be greater than zero")
  public boolean isActiveSnapshotQueryTimeoutPositive() {
    return activeSnapshotQueryTimeout != null
        && !activeSnapshotQueryTimeout.isZero()
        && !activeSnapshotQueryTimeout.isNegative();
  }

  @AssertTrue(message = "activeSnapshotQueryTimeout must not exceed maxRuntime")
  public boolean isActiveSnapshotQueryTimeoutWithinMaxRuntime() {
    if (activeSnapshotQueryTimeout == null || maxRuntime == null) {
      return true;
    }
    return activeSnapshotQueryTimeout.compareTo(maxRuntime) <= 0;
  }

  @AssertTrue(message = "activeSnapshotQueryTimeout must fit JDBC query timeout seconds")
  public boolean isActiveSnapshotQueryTimeoutWithinJdbcRange() {
    return JdbcQueryTimeout.fitsSeconds(activeSnapshotQueryTimeout);
  }
}

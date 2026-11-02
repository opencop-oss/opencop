// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

public enum HistoricalJobStage {
  INGRESS_RECEIVED,
  INGRESS_QUEUED,
  PLANNING,
  PLANNED,
  EXECUTION_RUNNING,
  EXECUTION_COMPLETED,
  DELIVERY_PENDING,
  INGRESS_REJECTED,
  TIMEOUT,
  DELIVERY_FAILED,
  // New constants must be appended here to keep ordinals stable. Wire/persistence uses name(),
  // so name preservation is what matters today — but appending also leaves room for future code
  // that might key off ordinal (e.g. EnumMap-backed indexes, persistent ORDINAL mappings).
  /**
   * Catch-all execution-failure variant. Used as the fallback target for {@code
   * HistoricalJobState.stageOrLegacy} when a persisted stage name is not recognized by this binary
   * — which covers two scenarios that look identical to the deserializer:
   *
   * <ul>
   *   <li>Pre-subdivision Redis snapshots that carry the literal string {@code "EXECUTION_FAILED"}
   *       written by older replicas before this stage was split into the {@code EXECUTION_FAILED_*}
   *       variants.
   *   <li>Forward-compatibility: an older replica reading a snapshot written by a newer replica
   *       that names a variant introduced in a later release.
   * </ul>
   *
   * <p>This constant is permanent — it is not a deprecation marker, it is the documented landing
   * pad for "we don't have a more specific category." New code that genuinely knows the failure
   * shape should produce one of {@link #EXECUTION_FAILED_STORAGE}, {@link
   * #EXECUTION_FAILED_TIMEOUT}, {@link #EXECUTION_FAILED_INTERRUPTED}, or {@link
   * #EXECUTION_FAILED_UNEXPECTED} via {@code HistoricalJobIngressService.classifyExecutionFailure}.
   */
  EXECUTION_FAILED_UNKNOWN,
  /** Data store (Postgres / Redis) reported an error mid-execution. Retryable. */
  EXECUTION_FAILED_STORAGE,
  /** A sub-operation timed out (distinct from the job-level {@link #TIMEOUT} supervisor). */
  EXECUTION_FAILED_TIMEOUT,
  /** Worker thread was interrupted mid-execution (cancellation). */
  EXECUTION_FAILED_INTERRUPTED,
  /** Anything else (programmer error, JVM-level failure, unclassified throwable). */
  EXECUTION_FAILED_UNEXPECTED,
  CANCELLATION_REQUESTED,
  CANCELLED
}

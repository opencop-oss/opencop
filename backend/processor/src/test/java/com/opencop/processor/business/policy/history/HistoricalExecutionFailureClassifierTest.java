// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import io.lettuce.core.RedisCommandTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;

class HistoricalExecutionFailureClassifierTest {

  private final HistoricalExecutionFailureClassifier classifier =
      new HistoricalExecutionFailureClassifier();

  @Test
  void classify_interruptedException_returnsInterrupted() {
    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_INTERRUPTED,
        classifier.classify(new InterruptedException("worker cancelled")));
  }

  @Test
  void classify_timeoutException_returnsTimeout() {
    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_TIMEOUT,
        classifier.classify(new TimeoutException("step timed out")));
  }

  @Test
  void classify_springQueryTimeoutException_returnsTimeout() {
    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_TIMEOUT,
        classifier.classify(new QueryTimeoutException("query timed out")));
  }

  @Test
  void classify_springDataAccessException_returnsStorage() {
    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_STORAGE,
        classifier.classify(new DuplicateKeyException("dup")));
  }

  @Test
  void classify_lettuceRedisException_returnsStorage() {
    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_STORAGE,
        classifier.classify(new RedisCommandTimeoutException("redis timed out")));
  }

  @Test
  void classify_runtimeExceptionWithStorageCause_walksChain() {
    var wrapped = new RuntimeException("workflow failed", new DuplicateKeyException("dup"));

    assertEquals(HistoricalJobStage.EXECUTION_FAILED_STORAGE, classifier.classify(wrapped));
  }

  @Test
  void classify_runtimeExceptionWithInterruptedCause_walksChain() {
    var wrapped = new RuntimeException("worker thread bailed", new InterruptedException("stop"));

    assertEquals(HistoricalJobStage.EXECUTION_FAILED_INTERRUPTED, classifier.classify(wrapped));
  }

  @Test
  void classify_illegalStateException_fallsThroughToUnexpected() {
    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED,
        classifier.classify(new IllegalStateException("boom")));
  }

  @Test
  void classify_outOfMemoryError_fallsThroughToUnexpected() {
    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED,
        classifier.classify(new OutOfMemoryError("simulated")));
  }

  @Test
  void classify_cyclicCauseChain_terminates() {
    var a = new RuntimeException("A");
    var b = new RuntimeException("B", a);
    a.initCause(b);

    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED,
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> classifier.classify(a)));
  }
}

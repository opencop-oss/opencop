// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import static com.opencop.processor.business.usecase.watchbox.WatchboxGenerationChangedTestSupport.generationChanged;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.transaction.CannotCreateTransactionException;

class RetryInPlaceFailureClassifierTest {

  @Test
  void infrastructureAndGenerationFailuresRetryInPlace() {
    assertThat(
            RetryInPlaceFailureClassifier.retryInPlace(
                new DataAccessResourceFailureException("db down")))
        .isTrue();
    assertThat(RetryInPlaceFailureClassifier.retryInPlace(new QueryTimeoutException("timeout")))
        .isTrue();
    assertThat(
            RetryInPlaceFailureClassifier.retryInPlace(
                new CannotCreateTransactionException(
                    "tx begin", new SQLTransientConnectionException("pool exhausted"))))
        .isTrue();
    assertThat(RetryInPlaceFailureClassifier.retryInPlace(generationChanged())).isTrue();
  }

  @Test
  void wrappedCausesAreUnwrapped() {
    var wrapped =
        new ListenerExecutionFailedException(
            "listener failed", new RuntimeException(generationChanged()));

    assertThat(RetryInPlaceFailureClassifier.retryInPlace(wrapped)).isTrue();
  }

  @Test
  void deterministicFailuresAreNotRetained() {
    assertThat(
            RetryInPlaceFailureClassifier.retryInPlace(
                new DataIntegrityViolationException("constraint violated")))
        .isFalse();
    assertThat(RetryInPlaceFailureClassifier.retryInPlace(new IllegalStateException("bug")))
        .isFalse();
    assertThat(RetryInPlaceFailureClassifier.retryInPlace(null)).isFalse();
  }
}

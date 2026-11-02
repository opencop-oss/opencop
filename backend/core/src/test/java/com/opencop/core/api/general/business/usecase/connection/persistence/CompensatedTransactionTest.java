// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.opencop.core.api.general.business.service.AmbiguousPersistenceOutcomeHandler;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

class CompensatedTransactionTest {

  private static final UUID SOURCE_ID = UUID.fromString("6c7d8e9f-0a1b-4c2d-8e3f-4a5b6c7d8e9f");

  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);
  private final AmbiguousPersistenceOutcomeHandler ambiguousOutcomeHandler =
      mock(AmbiguousPersistenceOutcomeHandler.class);
  private final CompensatedTransaction transaction =
      new CompensatedTransaction(
          new TransactionTemplate(transactionManager), ambiguousOutcomeHandler);

  @Test
  void answersWhatTheBodyReturnedOnceItCommitted() {
    var compensation = mock(Runnable.class);

    var result =
        transaction.run(SOURCE_ID, TransportKind.TCP, () -> "stored", Optional.of(compensation));

    assertThat(result).isEqualTo("stored");
    verify(transactionManager).commit(any());
    verifyNoInteractions(compensation, ambiguousOutcomeHandler);
  }

  @Test
  void compensatesABodyThatFailedBeforeCommit() {
    var compensation = mock(Runnable.class);
    var failure = new IllegalStateException("write failed");

    assertThatThrownBy(
            () ->
                transaction.run(
                    SOURCE_ID,
                    TransportKind.TCP,
                    () -> {
                      throw failure;
                    },
                    Optional.of(compensation)))
        .isSameAs(failure);

    verify(compensation).run();
    verifyNoInteractions(ambiguousOutcomeHandler);
  }

  /** The commit may have succeeded, so what was provisioned may be what the connection now uses. */
  @Test
  void preservesWhatWasProvisionedWhenTheCommitsOutcomeIsUnknown() {
    var compensation = mock(Runnable.class);
    var failure = new TransactionSystemException("commit failed");
    doThrow(failure).when(transactionManager).commit(any());

    assertThatThrownBy(
            () ->
                transaction.runWithoutResult(
                    SOURCE_ID, TransportKind.UDP, () -> {}, Optional.of(compensation)))
        .isSameAs(failure);

    verify(ambiguousOutcomeHandler)
        .preserveAfterFailure(
            SOURCE_ID,
            TransportKind.UDP,
            AmbiguousPersistenceOutcomeHandler.FailurePhase.TRANSACTION_COMPLETION,
            failure);
    verify(compensation, never()).run();
  }

  /** The outcome is as uncertain for a connection that provisioned nothing, so it is recorded. */
  @Test
  void recordsAnUnknownCommitOutcomeEvenWhenNothingWasProvisioned() {
    var failure = new TransactionSystemException("commit failed");
    doThrow(failure).when(transactionManager).commit(any());

    assertThatThrownBy(
            () ->
                transaction.runWithoutResult(
                    SOURCE_ID, TransportKind.TCP, () -> {}, Optional.empty()))
        .isSameAs(failure);

    verify(ambiguousOutcomeHandler)
        .preserveAfterFailure(
            SOURCE_ID,
            TransportKind.TCP,
            AmbiguousPersistenceOutcomeHandler.FailurePhase.TRANSACTION_COMPLETION,
            failure);
  }

  @Test
  void attachesAFailedCompensationToTheFailureItCompensates() {
    var failure = new IllegalStateException("no Connect instance");
    var compensationFailure = new IllegalStateException("vault down");

    transaction.compensate(
        SOURCE_ID,
        () -> {
          throw compensationFailure;
        },
        failure);

    assertThat(failure.getSuppressed()).containsExactly(compensationFailure);
  }
}

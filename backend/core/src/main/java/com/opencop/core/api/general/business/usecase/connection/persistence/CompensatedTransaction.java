// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import com.opencop.core.api.general.business.service.AmbiguousPersistenceOutcomeHandler;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores a connection in one transaction, compensating what was provisioned for it outside the
 * database, such as its certificates, should that transaction fail before it could commit.
 *
 * <p>A failure once the transaction's body completed comes from the commit itself, whose outcome is
 * unknown: the uncertain outcome is recorded, and since the connection may now use what was
 * provisioned, that is preserved rather than compensated.
 */
@Component
public class CompensatedTransaction {

  private static final Logger log = LoggerFactory.getLogger(CompensatedTransaction.class);

  private final TransactionTemplate transactionTemplate;
  private final AmbiguousPersistenceOutcomeHandler ambiguousOutcomeHandler;

  public CompensatedTransaction(
      TransactionTemplate transactionTemplate,
      AmbiguousPersistenceOutcomeHandler ambiguousOutcomeHandler) {
    this.transactionTemplate = transactionTemplate;
    this.ambiguousOutcomeHandler = ambiguousOutcomeHandler;
  }

  /**
   * Runs the body in one transaction and answers what it returned once that committed.
   *
   * @param kind the connection's transport, which an uncertain outcome is recorded for
   * @param compensation what deletes the material provisioned for the connection
   */
  public <T> T run(
      UUID sourceId, TransportKind kind, Supplier<T> body, Optional<Runnable> compensation) {
    return run(
        sourceId,
        body,
        compensation,
        failure ->
            ambiguousOutcomeHandler.preserveAfterFailure(
                sourceId,
                kind,
                AmbiguousPersistenceOutcomeHandler.FailurePhase.TRANSACTION_COMPLETION,
                failure));
  }

  /**
   * Runs a body that replaces a connection's setup in one transaction, as {@link #run(UUID,
   * TransportKind, Supplier, Optional)} does. An uncertain outcome is recorded for the setup's
   * secrets, since the connection itself existed before.
   *
   * @param setupSecretRefs the replacing setup's secret-store references, preserved on an uncertain
   *     outcome
   */
  public <T> T runReplacement(
      UUID sourceId,
      TransportKind kind,
      List<String> setupSecretRefs,
      Supplier<T> body,
      Optional<Runnable> compensation) {
    return run(
        sourceId,
        body,
        compensation,
        failure ->
            ambiguousOutcomeHandler.preserveReplacementAfterFailure(
                sourceId, kind, setupSecretRefs, failure));
  }

  /**
   * Runs a body that answers nothing in one transaction, as {@link #run(UUID, TransportKind,
   * Supplier, Optional)} does.
   */
  public void runWithoutResult(
      UUID sourceId, TransportKind kind, Runnable body, Optional<Runnable> compensation) {
    run(
        sourceId,
        kind,
        () -> {
          body.run();
          return null;
        },
        compensation);
  }

  private <T> T run(
      UUID sourceId,
      Supplier<T> body,
      Optional<Runnable> compensation,
      Consumer<RuntimeException> preserveUncertain) {
    var bodyCompleted = new AtomicBoolean();
    try {
      return transactionTemplate.execute(
          _ -> {
            var result = body.get();
            bodyCompleted.set(true);
            return result;
          });
    } catch (RuntimeException failure) {
      if (bodyCompleted.get()) {
        preserveUncertain.accept(failure);
      } else {
        compensation.ifPresent(provisioned -> compensate(sourceId, provisioned, failure));
      }
      throw failure;
    }
  }

  /**
   * Deletes what was provisioned for a connection that failed before its transaction began, such as
   * one no Connect instance could be found for. A compensation that fails itself is attached to the
   * failure it compensates, which still surfaces.
   */
  public void compensate(UUID sourceId, Runnable compensation, RuntimeException failure) {
    try {
      compensation.run();
    } catch (RuntimeException compensationFailure) {
      failure.addSuppressed(compensationFailure);
      log.error(
          "Failed to compensate connection persistence for source {}",
          sourceId,
          compensationFailure);
    }
  }
}

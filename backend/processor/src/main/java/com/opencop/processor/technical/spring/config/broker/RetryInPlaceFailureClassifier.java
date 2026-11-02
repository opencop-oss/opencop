// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import com.opencop.processor.business.usecase.watchbox.WatchboxGenerationChangedException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Decides whether a listener failure should retain its batch and retry in place until the transient
 * condition clears, rather than exhaust the ordinary retry ladder and publish to DLT.
 *
 * <p>The classification walks the cause chain, because the exception that reaches the error handler
 * is wrapped by the listener adapter and often again by the transaction layer. Alongside
 * infrastructure outages, a database-proven Watchbox generation change belongs here: replay can
 * succeed after configuration stabilizes, while the Watchbox DLT is a tombstone rather than a
 * recovery queue.
 *
 * <p>{@link DataAccessResourceFailureException} is included even though Spring classifies it
 * non-transient. Spring's contract answers whether the same connection can work immediately; this
 * policy answers whether a fresh attempt can work when the database returns. Input bounds keep
 * deterministic class-54 resource-limit failures off this path.
 */
final class RetryInPlaceFailureClassifier {

  private RetryInPlaceFailureClassifier() {}

  static boolean retryInPlace(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause == cause.getCause()) {
        break;
      }
      if (retryInPlaceCause(cause)) {
        return true;
      }
    }
    return false;
  }

  /** Grouped by exception-hierarchy root; these trees share no common policy-level ancestor. */
  private static boolean retryInPlaceCause(Throwable cause) {
    return switch (cause) {
      case WatchboxGenerationChangedException _ -> true;
      // Spring DAO tree.
      case TransientDataAccessException _, RecoverableDataAccessException _ -> true;
      // Spring calls this one non-transient; retained here by policy — see class javadoc.
      case DataAccessResourceFailureException _ -> true;
      // Spring transaction tree: connection acquisition failed before any statement ran.
      case CannotCreateTransactionException _ -> true;
      // Raw java.sql tree: driver-level signals Spring translation may not have wrapped.
      case SQLTransientException _,
          SQLRecoverableException _,
          SQLNonTransientConnectionException _ ->
          true;
      default -> false;
    };
  }
}

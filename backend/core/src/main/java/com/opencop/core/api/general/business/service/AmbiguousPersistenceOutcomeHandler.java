// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Preserves externally provisioned resources when a persistence failure has an unknown outcome.
 *
 * <p>The caller decides that the failure is ambiguous. This service performs the shared
 * post-transaction probe and emits the reconciliation diagnostic without logging exception messages
 * or credential material.
 */
@Service
public class AmbiguousPersistenceOutcomeHandler {

  private static final Logger log =
      LoggerFactory.getLogger(AmbiguousPersistenceOutcomeHandler.class);
  private static final String NO_EXTERNAL_RESOURCE = "none";

  private final DataSourceRepository dataSourceRepository;

  public AmbiguousPersistenceOutcomeHandler(DataSourceRepository dataSourceRepository) {
    this.dataSourceRepository = dataSourceRepository;
  }

  public void preserveAfterFailure(
      UUID sourceId,
      TransportKind connectionKind,
      FailurePhase failurePhase,
      RuntimeException failure) {
    preserveAfterFailure(sourceId, connectionKind, failurePhase, NO_EXTERNAL_RESOURCE, failure);
  }

  /**
   * Probe durable state and record why the external resource is being preserved.
   *
   * @param externalResource non-secret external resource identifier, such as a Kafka topic
   */
  public void preserveAfterFailure(
      UUID sourceId,
      TransportKind connectionKind,
      FailurePhase failurePhase,
      String externalResource,
      RuntimeException failure) {
    var probeResult = "inconclusive";
    var probeExceptionCategory = "none";
    try {
      if (dataSourceRepository.findById(sourceId).isPresent()) {
        probeResult = "present";
      }
    } catch (RuntimeException probeFailure) {
      probeExceptionCategory = probeFailure.getClass().getSimpleName();
      failure.addSuppressed(probeFailure);
    }
    log.error(
        "Connection persistence has an unknown outcome: "
            + "sourceId={}, connectionKind={}, externalResource={}, failurePhase={}, "
            + "probeResult={}, cleanupDecision=preserve, exceptionCategory={}, "
            + "probeExceptionCategory={}",
        sourceId,
        connectionKind,
        externalResource,
        failurePhase.logValue,
        probeResult,
        failure.getClass().getSimpleName(),
        probeExceptionCategory);
  }

  /**
   * Record why a setup replacement's material is preserved once its commit failed. The connection's
   * row existed before the replacement, so its presence says nothing of the outcome and is not
   * probed; the setup's secret references identify the attempt.
   *
   * @param setupSecretRefs the replacing setup's non-secret secret-store references
   */
  public void preserveReplacementAfterFailure(
      UUID sourceId,
      TransportKind connectionKind,
      List<String> setupSecretRefs,
      RuntimeException failure) {
    log.error(
        "Connection setup replacement has an unknown outcome: "
            + "sourceId={}, connectionKind={}, setupSecretRefs={}, failurePhase={}, "
            + "setupOutcome=inconclusive, cleanupDecision=preserve, exceptionCategory={}",
        sourceId,
        connectionKind,
        setupSecretRefs,
        FailurePhase.TRANSACTION_COMPLETION.logValue,
        failure.getClass().getSimpleName());
  }

  public enum FailurePhase {
    TRANSACTION_COMPLETION("transaction-completion"),
    PERSISTENCE_TRANSACTION("persistence-transaction");

    private final String logValue;

    FailurePhase(String logValue) {
      this.logValue = logValue;
    }
  }
}

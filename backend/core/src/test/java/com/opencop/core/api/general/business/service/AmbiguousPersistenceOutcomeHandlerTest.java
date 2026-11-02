// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class AmbiguousPersistenceOutcomeHandlerTest {

  private static final UUID SOURCE_ID = UUID.fromString("96a182b2-d27a-4683-9998-251c34fb49b3");
  private static final String CLIENT_REF = "opencop/shared/connect/source/generation/client";

  @Mock private DataSourceRepository dataSourceRepository;

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;
  private AmbiguousPersistenceOutcomeHandler handler;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(AmbiguousPersistenceOutcomeHandler.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    handler = new AmbiguousPersistenceOutcomeHandler(dataSourceRepository);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void durableSourceIsReportedAsPresentWithoutChangingTheOriginalFailure() {
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(mock(SocketDataSource.class)));
    var failure = new IllegalStateException("commit acknowledgement lost");

    handler.preserveAfterFailure(
        SOURCE_ID,
        TransportKind.TCP,
        AmbiguousPersistenceOutcomeHandler.FailurePhase.TRANSACTION_COMPLETION,
        failure);

    verify(dataSourceRepository).findById(SOURCE_ID);
    assertThat(failure.getSuppressed()).isEmpty();
    assertThat(singleOutcomeLog())
        .contains(
            "sourceId=" + SOURCE_ID,
            "connectionKind=TCP",
            "externalResource=none",
            "failurePhase=transaction-completion",
            "probeResult=present",
            "cleanupDecision=preserve");
  }

  @Test
  void immediateMissRemainsInconclusiveAndPreservesTheExternalResourceReference() {
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());
    var failure = new IllegalStateException("commit acknowledgement lost");

    handler.preserveAfterFailure(
        SOURCE_ID,
        TransportKind.KAFKA,
        AmbiguousPersistenceOutcomeHandler.FailurePhase.PERSISTENCE_TRANSACTION,
        "topic-" + SOURCE_ID,
        failure);

    assertThat(singleOutcomeLog())
        .contains(
            "externalResource=topic-" + SOURCE_ID,
            "failurePhase=persistence-transaction",
            "probeResult=inconclusive",
            "cleanupDecision=preserve");
  }

  @Test
  void failedProbeIsSuppressedAndTheDiagnosticDoesNotLeakExceptionMessages() {
    var persistenceFailure = new IllegalStateException("persistence-sensitive-detail");
    var probeFailure = new IllegalStateException("probe-sensitive-detail");
    when(dataSourceRepository.findById(SOURCE_ID)).thenThrow(probeFailure);

    handler.preserveAfterFailure(
        SOURCE_ID,
        TransportKind.KAFKA,
        AmbiguousPersistenceOutcomeHandler.FailurePhase.PERSISTENCE_TRANSACTION,
        "safe-topic",
        persistenceFailure);

    assertThat(persistenceFailure.getSuppressed()).containsExactly(probeFailure);
    assertThat(singleOutcomeLog())
        .contains(
            "probeResult=inconclusive",
            "exceptionCategory=IllegalStateException",
            "probeExceptionCategory=IllegalStateException")
        .doesNotContain("persistence-sensitive-detail", "probe-sensitive-detail");
  }

  /**
   * The replaced connection's row existed before, so its presence says nothing of the outcome; the
   * setup's secrets identify the attempt instead.
   */
  @Test
  void setupReplacementIsIdentifiedByItsSecretsWithoutProbingTheSourceRow() {
    var failure = new IllegalStateException("commit-sensitive-detail");

    handler.preserveReplacementAfterFailure(
        SOURCE_ID, TransportKind.TCP, List.of(CLIENT_REF), failure);

    verifyNoInteractions(dataSourceRepository);
    assertThat(failure.getSuppressed()).isEmpty();
    assertThat(singleOutcomeLog())
        .contains(
            "setup replacement",
            "sourceId=" + SOURCE_ID,
            "connectionKind=TCP",
            "setupSecretRefs=[" + CLIENT_REF + "]",
            "failurePhase=transaction-completion",
            "setupOutcome=inconclusive",
            "cleanupDecision=preserve",
            "exceptionCategory=IllegalStateException")
        .doesNotContain("probeResult", "commit-sensitive-detail");
  }

  private String singleOutcomeLog() {
    var messages =
        appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(message -> message.contains("unknown outcome"))
            .toList();
    assertThat(messages).hasSize(1);
    return messages.getFirst();
  }
}

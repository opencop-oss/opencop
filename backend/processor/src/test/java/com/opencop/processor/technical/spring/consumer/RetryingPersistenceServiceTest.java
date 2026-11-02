// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.processor.ports.business.usecase.event.PersistenceFailureException;
import com.opencop.processor.technical.gateway.db.PostGisFeatureRecordWriter;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RetryingPersistenceServiceTest {

  @Test
  void persist_retriesDeadlockFailuresUntilSuccess() throws Exception {
    var writer = mock(PostGisFeatureRecordWriter.class);
    var transactionManager = mockTransactionManager();
    var deadlock = new SQLException("deadlock detected", "40P01");

    doThrow(repositoryException(deadlock)).doNothing().when(writer).save(any(), any());

    var service = new RetryingPersistenceService(writer, transactionManager);

    service.persist(List.of(featureRecord()));

    verify(writer, times(2)).save(any(), any());
  }

  @Test
  void persist_doesNotRetryNonDeadlockFailures() throws Exception {
    var writer = mock(PostGisFeatureRecordWriter.class);
    var transactionManager = mockTransactionManager();

    doThrow(repositoryException(new SQLException("boom", "23505"))).when(writer).save(any(), any());

    var service = new RetryingPersistenceService(writer, transactionManager);

    assertThrows(
        PersistenceFailureException.class, () -> service.persist(List.of(featureRecord())));

    verify(writer, times(1)).save(any(), any());
  }

  private static PlatformTransactionManager mockTransactionManager() {
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    TransactionStatus transactionStatus = new SimpleTransactionStatus();
    org.mockito.Mockito.when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(transactionStatus);
    doNothing().when(transactionManager).commit(any(TransactionStatus.class));
    doNothing().when(transactionManager).rollback(any(TransactionStatus.class));
    return transactionManager;
  }

  private static PostGisFeatureRecord featureRecord() {
    return new PostGisFeatureRecord(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440800"),
        "TRACK",
        UUID.fromString("550e8400-e29b-41d4-a716-446655440801"),
        null,
        "obj-1",
        null,
        null,
        "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
        "{\"type\":\"Feature\"}");
  }

  private static RepositoryException repositoryException(SQLException cause) {
    var exception = new RepositoryException(RepositoryExceptionCodes.FAILED_TO_PERSIST);
    exception.initCause(cause);
    return exception;
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.nudge.SocketReconciliationNudge;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class PostgresSocketReconciliationNudgePublisherTest {

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void requestReconciliation_whenNoTransactionPublishesImmediately() {
    var entityManager = mock(EntityManager.class);
    var query = queryFor(entityManager);
    var publisher =
        new PostgresSocketReconciliationNudgePublisher(entityManager, new ObjectMapper());

    publisher.requestReconciliation();

    verify(query).setParameter("channel", SocketReconciliationNudge.CHANNEL);
    var payload = publishedPayload(query);
    var nudge = new ObjectMapper().readValue(payload, SocketReconciliationNudge.class);
    assertEquals(SocketReconciliationNudge.CURRENT_VERSION, nudge.version());
    assertEquals("{\"version\":1}", payload);
  }

  @Test
  void requestReconciliation_whenTransactionSynchronizationIsActivePublishesAfterCommit() {
    var entityManager = mock(EntityManager.class);
    var query = queryFor(entityManager);
    var publisher =
        new PostgresSocketReconciliationNudgePublisher(entityManager, new ObjectMapper());

    TransactionSynchronizationManager.initSynchronization();
    publisher.requestReconciliation();

    verifyNoInteractions(entityManager);
    TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

    verify(query).setParameter("channel", SocketReconciliationNudge.CHANNEL);
  }

  @Test
  void requestReconciliation_whenPublishFailsDoesNotThrow() {
    var entityManager = mock(EntityManager.class);
    var query = queryFor(entityManager);
    var publisher =
        new PostgresSocketReconciliationNudgePublisher(entityManager, new ObjectMapper());
    doThrow(new PersistenceException("boom")).when(query).getSingleResult();

    assertDoesNotThrow(publisher::requestReconciliation);
  }

  private static Query queryFor(EntityManager entityManager) {
    var query = mock(Query.class);
    when(entityManager.createNativeQuery(PostgresSocketReconciliationNudgePublisher.SQL))
        .thenReturn(query);
    return query;
  }

  private static String publishedPayload(Query query) {
    var captor = ArgumentCaptor.forClass(String.class);
    verify(query).setParameter(eq("payload"), captor.capture());
    return captor.getValue();
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.sdk.model.nudge.SocketReconciliationNudge;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** Publishes socket reconciliation nudges through Postgres LISTEN/NOTIFY. */
public class PostgresSocketReconciliationNudgePublisher
    implements SocketReconciliationNudgePublisher {

  static final String SQL = "SELECT pg_notify(:channel, :payload)";

  private static final Logger log =
      LoggerFactory.getLogger(PostgresSocketReconciliationNudgePublisher.class);

  private final EntityManager entityManager;
  private final ObjectMapper objectMapper;

  public PostgresSocketReconciliationNudgePublisher(
      EntityManager entityManager, ObjectMapper objectMapper) {
    this.entityManager = entityManager;
    this.objectMapper = objectMapper;
  }

  @Override
  public void requestReconciliation() {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(SocketReconciliationNudge.current());
    } catch (Exception e) {
      log.warn("Failed to serialize socket reconciliation nudge payload: {}", e.getMessage(), e);
      return;
    }

    publish(SocketReconciliationNudge.CHANNEL, payload);
  }

  private void publish(String channel, String payload) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publishNow(channel, payload);
            }
          });
      return;
    }

    publishNow(channel, payload);
  }

  private void publishNow(String channel, String payload) {
    try {
      var query = entityManager.createNativeQuery(SQL);
      query.setParameter("channel", channel);
      query.setParameter("payload", payload);
      // pg_notify returns PostgreSQL void, but this SELECT still produces one row; executing it
      // through getSingleResult() is intentional and the null/void value is ignored.
      query.getSingleResult();
      log.debug("Published socket reconciliation nudge");
    } catch (Exception e) {
      log.warn(
          "Failed to publish socket reconciliation nudge; desired state remains persisted: {}",
          e.getMessage(),
          e);
    }
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.processor.ports.business.usecase.event.PersistenceFailureException;
import com.opencop.processor.technical.gateway.db.PostGisFeatureRecordWriter;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RetryingPersistenceService {

  private static final String POSTGRES_DEADLOCK_SQLSTATE = "40P01";
  private static final int MAX_ATTEMPTS = 3;

  private final PostGisFeatureRecordWriter postGisFeatureRecordWriter;
  private final TransactionTemplate transactionTemplate;

  RetryingPersistenceService(
      PostGisFeatureRecordWriter postGisFeatureRecordWriter,
      PlatformTransactionManager transactionManager) {
    this.postGisFeatureRecordWriter = postGisFeatureRecordWriter;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  void persist(List<PostGisFeatureRecord> input) {
    persist(input, null);
  }

  /**
   * @param batchKey identity of the consumer batch, so a deadlock retry re-records the same
   *     activity rather than counting it twice. It is deliberately stable across attempts.
   */
  void persist(List<PostGisFeatureRecord> input, String batchKey) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        transactionTemplate.executeWithoutResult(
            _ -> {
              try {
                postGisFeatureRecordWriter.save(input, batchKey);
              } catch (Exception e) {
                throw new PersistenceFailureException("failed to persist prepared batch", e);
              }
            });
        return;
      } catch (PersistenceFailureException e) {
        if (!isRetryableDeadlock(e) || attempt == MAX_ATTEMPTS) {
          throw e;
        }
      }
    }
  }

  private static boolean isRetryableDeadlock(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sqlException
          && POSTGRES_DEADLOCK_SQLSTATE.equals(sqlException.getSQLState())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}

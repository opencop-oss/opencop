// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.processor.ports.technical.gateway.db.LayerCopyGateway;
import java.util.UUID;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class LayerCopyRepository implements LayerCopyGateway {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate repeatableReadTransaction;

  public LayerCopyRepository(
      JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = jdbcTemplate;
    this.repeatableReadTransaction = new TransactionTemplate(transactionManager);
    this.repeatableReadTransaction.setIsolationLevel(
        TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  @Override
  public void copyLayer(
      UUID sourceLayerId, UUID targetLayerId, boolean copyRemarks, boolean copyMedia) {
    try {
      repeatableReadTransaction.executeWithoutResult(
          _ -> {
            jdbcTemplate.update(
                "CALL geo.clone_source_to_layer(CAST(? AS uuid), CAST(? AS uuid), ?, ?)",
                sourceLayerId,
                targetLayerId,
                copyRemarks,
                copyMedia);
            // Inside the same transaction, so a failed clone records no activity for it.
            LayerCopyActivityWriter.record(jdbcTemplate, targetLayerId);
          });
    } catch (RuntimeException e) {
      var message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
      throw new IllegalStateException(
          message != null ? message : "Failed to clone layer contents", e);
    }
  }

  @Override
  public boolean targetHasFeatures(UUID targetLayerId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM geo.features WHERE source = CAST(? AS uuid) LIMIT 1)",
            Boolean.class,
            targetLayerId);
    return Boolean.TRUE.equals(exists);
  }
}

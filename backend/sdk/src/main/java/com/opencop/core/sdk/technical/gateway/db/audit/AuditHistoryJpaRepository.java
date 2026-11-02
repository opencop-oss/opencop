// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.audit;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Append and read only: no delete, no bulk save, and no finder that loads an entity to mutate. The
 * table rejects updates and deletes itself; this keeps the application from offering them.
 */
public interface AuditHistoryJpaRepository
    extends Repository<AuditHistoryEntity, Long>, JpaSpecificationExecutor<AuditHistoryEntity> {

  AuditHistoryEntity saveAndFlush(AuditHistoryEntity entity);
}

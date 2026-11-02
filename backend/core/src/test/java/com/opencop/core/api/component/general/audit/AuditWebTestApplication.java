// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.audit;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.usecase.audit.ReadAuditHistoryUsecase;
import com.opencop.core.api.general.technical.endpoint.audit.AuditHistoryEndpoint;
import com.opencop.core.api.general.technical.gateway.db.audit.JpaAuditHistoryStore;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.audit.AuthzAuditConfig;
import com.opencop.core.sdk.technical.exception.GlobalExceptionManager;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryEntity;
import com.opencop.core.sdk.technical.gateway.db.audit.AuditHistoryJpaRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * The smallest context that serves the audit endpoint: the ledger's own entity, repository, store
 * and endpoint. The reader joins nothing to live state, so no resource domain is needed here.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(basePackageClasses = AuditHistoryEntity.class)
@EnableJpaRepositories(basePackageClasses = AuditHistoryJpaRepository.class)
@Import({
  AuditHistoryEndpoint.class,
  ReadAuditHistoryUsecase.class,
  JpaAuditHistoryStore.class,
  GlobalExceptionManager.class,
  AuthzAuditConfig.class,
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class
})
public class AuditWebTestApplication {}

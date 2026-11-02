// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A mocked {@link AuditRecorder} with the real projections, for component contexts whose tests
 * assert something other than the ledger.
 *
 * <p>Shared rather than repeated: recording reaches into connections, sources, layers and exports,
 * so a use case gaining a recorder otherwise breaks every context that wires it and nothing else —
 * a context failure whose cause is several frames below the test that reports it. Importing this
 * means a context opts in once instead of growing another copy each time a seam is instrumented.
 *
 * <p>Deliberately a mock: these contexts prove their own behaviour, not that rows land or roll back
 * with the change. The contexts that prove <em>that</em> wire the real {@code JpaAuditHistoryStore}
 * (see {@code LayerGroupAuditWebTestApplication}) or {@link FailAfterPersistRecorderConfig}, and
 * must not import this.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MockAuditRecordingConfig {

  @Bean
  AuditRecorder auditRecorder() {
    return Mockito.mock(AuditRecorder.class);
  }

  @Bean
  DataSourceAuditProjection dataSourceAuditProjection() {
    return new DataSourceAuditProjection();
  }

  @Bean
  LayerAuditProjection layerAuditProjection() {
    return new LayerAuditProjection();
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import com.opencop.core.api.general.technical.gateway.db.audit.JpaAuditHistoryStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Makes the audit recorder a {@link FailAfterPersistRecorder} over the real store. */
@TestConfiguration(proxyBeanMethods = false)
public class FailAfterPersistRecorderConfig {

  @Bean
  @Primary
  FailAfterPersistRecorder failAfterPersistRecorder(
      JpaAuditHistoryStore delegate, JdbcTemplate jdbcTemplate) {
    return new FailAfterPersistRecorder(delegate, jdbcTemplate);
  }
}

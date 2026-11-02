// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.appmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetGateway;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetTelemetry;
import com.opencop.core.api.general.technical.observability.runtimefeature.MicrometerWatchboxTrackingResetTelemetry;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ComponentWebIT(classes = AppMetadataWebTestApplication.class)
class RuntimeFeatureWatchboxTrackingResetAtomicityIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443301");
  private static final UUID WATCHBOX_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443311");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoSpyBean private WatchboxTrackingResetGateway trackingResetGateway;
  @MockitoSpyBean private WatchboxTrackingResetTelemetry trackingResetTelemetry;

  @BeforeEach
  void resetState() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              deleteFixtures();
              jdbc.update(
                  """
                  INSERT INTO opencop_user (
                    id, idp_user_id, idp_username, idp_issuer, username,
                    active, administrator, created_at, updated_at
                  ) VALUES (?, 'runtime-reset-failure-admin', 'runtime-reset-failure-admin',
                            'runtime-reset-failure-admin', 'runtime-reset-failure-admin',
                            true, true, now(), now())
                  """,
                  ADMIN_ID);
              jdbc.update(
                  """
                  INSERT INTO watchbox.watchbox (
                    id, name, enabled, direction, polygon_json, filter_json,
                    config_epoch, created_by, created_at, updated_at
                  ) VALUES (
                    ?, 'atomicity-watchbox', true, 'BOTH',
                    '{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}'::jsonb,
                    '{"predicates":[]}'::jsonb,
                    5, ?, now(), now()
                  )
                  """,
                  WATCHBOX_ID,
                  ADMIN_ID);
            });
  }

  @AfterEach
  void clearState() {
    new TransactionTemplate(transactionManager).executeWithoutResult(_ -> deleteFixtures());
  }

  @Test
  void replaceAudience_whenResetGatewayFailsAfterEpochUpdate_rollsBackFeatureHistoryAndEpoch()
      throws Exception {
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("induced failure after tracking reset");
            })
        .when(trackingResetGateway)
        .bumpAllActiveConfigEpochs();
    var updatedAtBefore =
        jdbc.queryForObject(
            "SELECT updated_at FROM watchbox.watchbox WHERE id = ?", Instant.class, WATCHBOX_ID);

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":0}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

    verify(trackingResetGateway).bumpAllActiveConfigEpochs();
    assertRolledBack(updatedAtBefore);
  }

  @Test
  void replaceAudience_whenCommitFailsAfterEvidenceRegistration_emitsNoSuccessEvidence()
      throws Exception {
    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              TransactionSynchronizationManager.registerSynchronization(
                  new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                      throw new IllegalStateException("induced commit failure after reset");
                    }
                  });
              return result;
            })
        .when(trackingResetGateway)
        .bumpAllActiveConfigEpochs();
    var updatedAtBefore =
        jdbc.queryForObject(
            "SELECT updated_at FROM watchbox.watchbox WHERE id = ?", Instant.class, WATCHBOX_ID);
    double resetCountBefore = resetCount();
    double bumpedEpochCountBefore = bumpedEpochCount();
    var logger = (Logger) LoggerFactory.getLogger(MicrometerWatchboxTrackingResetTelemetry.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      mockMvc
          .perform(
              patch("/v1/admin/runtime-features/watchbox")
                  .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":0}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

      verify(trackingResetGateway).bumpAllActiveConfigEpochs();
      verify(trackingResetTelemetry).recordResetAfterCommit(new RuntimeFeatureAudience.All(), 1);
      assertThat(appender.list)
          .noneMatch(
              event -> event.getFormattedMessage().contains("WATCHBOX_TRACKING_RESET trigger="));
      assertThat(resetCount()).isEqualTo(resetCountBefore);
      assertThat(bumpedEpochCount()).isEqualTo(bumpedEpochCountBefore);
      assertRolledBack(updatedAtBefore);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private double resetCount() {
    var counter =
        meterRegistry.find(MicrometerWatchboxTrackingResetTelemetry.RESETS_TOTAL).counter();
    return counter == null ? 0 : counter.count();
  }

  private double bumpedEpochCount() {
    var counter =
        meterRegistry
            .find(MicrometerWatchboxTrackingResetTelemetry.CONFIG_EPOCHS_BUMPED_TOTAL)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  private void assertRolledBack(Instant updatedAtBefore) {
    assertThat(
            jdbc.queryForObject(
                "SELECT audience_mode FROM config.runtime_feature WHERE name = 'watchbox'",
                String.class))
        .isEqualTo("NONE");
    assertThat(
            jdbc.queryForObject(
                "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT config_epoch FROM watchbox.watchbox WHERE id = ?", Long.class, WATCHBOX_ID))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at FROM watchbox.watchbox WHERE id = ?",
                Instant.class,
                WATCHBOX_ID))
        .isEqualTo(updatedAtBefore);
  }

  private void deleteFixtures() {
    jdbc.update("DELETE FROM config.runtime_feature_history");
    jdbc.update("DELETE FROM watchbox.watchbox WHERE id = ?", WATCHBOX_ID);
    jdbc.update(
        "UPDATE config.runtime_feature SET audience_mode = 'NONE', revision = 0 WHERE name = 'watchbox'");
    jdbc.update(
        "DELETE FROM config.runtime_feature_audience_user_group WHERE feature_name = 'watchbox'");
    jdbc.update("DELETE FROM opencop_user WHERE id = ?", ADMIN_ID);
  }
}

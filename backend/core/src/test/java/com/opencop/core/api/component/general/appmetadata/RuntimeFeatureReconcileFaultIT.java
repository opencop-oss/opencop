// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.appmetadata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureBusyException;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureJpaRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reconciliation's failure boundary, exercised through the real proxied store.
 *
 * <p>The other reconcile tests reject a name either before the store is reached (malformed,
 * reserved) or after it returned successfully (class conflict), so none of them crosses {@code
 * JpaRuntimeFeatureStore.register}'s transactional boundary. These do: the repository is stubbed to
 * fail for one name, so the failure is raised <em>inside</em> that transaction and propagates out
 * through it — the case an outer transaction on {@code reconcile} would turn into all-or-nothing.
 *
 * <p>Lives apart from {@link RuntimeFeatureEndpointIT} because a bean override gives this class its
 * own application context; folding it in would evict the cached context that class shares.
 */
@ComponentWebIT(classes = AppMetadataWebTestApplication.class)
class RuntimeFeatureReconcileFaultIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443107");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoSpyBean private RuntimeFeatureJpaRepository featureRepository;

  @BeforeEach
  void seedAdmin() {
    resetCatalog(true);
  }

  @AfterEach
  void clearFixtures() {
    resetCatalog(false);
  }

  private void resetCatalog(boolean insertAdmin) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              jdbc.update("DELETE FROM config.runtime_feature_history");
              jdbc.update(
                  "DELETE FROM config.runtime_feature WHERE name NOT IN ('watchbox', 'rtsp')");
              jdbc.update("DELETE FROM opencop_user WHERE id = ?", ADMIN_ID);
              if (insertAdmin) {
                jdbc.update(
                    "INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username) VALUES (?, 'reconcile-fault-admin', 'reconcile-fault-admin', 'reconcile-fault-admin', 'reconcile-fault-admin')",
                    ADMIN_ID);
              }
            });
  }

  /**
   * Contention is the one store failure with a per-name meaning, so it is reported rather than
   * raised — and the names on either side of it still commit independently.
   */
  @Test
  void aContendedNameIsReportedWhileTheNamesAroundItStillCommit() throws Exception {
    doThrow(new RuntimeFeatureBusyException(new IllegalStateException("row locked")))
        .when(featureRepository)
        .insertFrontendOnlyIfAbsent(eq("fault-busy"), any());

    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"good-before\",\"fault-busy\",\"good-after\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(false))
        .andExpect(jsonPath("$.created.length()").value(2))
        .andExpect(jsonPath("$.created[0].name").value("good-before"))
        // The name after the failure was still attempted: a per-name refusal does not stop the run.
        .andExpect(jsonPath("$.created[1].name").value("good-after"))
        .andExpect(jsonPath("$.rejected.length()").value(1))
        .andExpect(jsonPath("$.rejected[0].name").value("fault-busy"))
        .andExpect(jsonPath("$.rejected[0].code").value("RUNTIME_FEATURE_BUSY"));

    assertRegistered("good-before");
    assertRegistered("good-after");
    assertAbsent("fault-busy");
    org.assertj.core.api.Assertions.assertThat(historyFeatureNames())
        .containsExactlyInAnyOrder("good-before", "good-after");
  }

  /**
   * An unexpected store failure is not a business outcome. It fails the request, and because no
   * transaction spans the batch, the name that already committed stays committed while the name
   * after it is never attempted.
   */
  @Test
  void anUnexpectedStoreFailureAbortsTheRunAndLeavesEarlierNamesCommitted() throws Exception {
    doThrow(new DataAccessResourceFailureException("connection reset"))
        .when(featureRepository)
        .insertFrontendOnlyIfAbsent(eq("fault-fatal"), any());

    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"durable-before\",\"fault-fatal\",\"never-attempted\"]}"))
        .andExpect(status().is5xxServerError());

    // The partial progress the abort event records: committed before the failure, nothing after.
    assertRegistered("durable-before");
    assertAbsent("fault-fatal");
    assertAbsent("never-attempted");
    org.assertj.core.api.Assertions.assertThat(historyFeatureNames())
        .containsExactly("durable-before");
  }

  private void assertRegistered(String name) {
    var count =
        jdbc.queryForObject(
            "SELECT count(*) FROM config.runtime_feature WHERE name = ?", Integer.class, name);
    org.assertj.core.api.Assertions.assertThat(count)
        .as("%s should be registered", name)
        .isEqualTo(1);
  }

  private void assertAbsent(String name) {
    var count =
        jdbc.queryForObject(
            "SELECT count(*) FROM config.runtime_feature WHERE name = ?", Integer.class, name);
    org.assertj.core.api.Assertions.assertThat(count)
        .as("%s should not be registered", name)
        .isZero();
  }

  private List<String> historyFeatureNames() {
    return jdbc.queryForList(
        "SELECT feature_name FROM config.runtime_feature_history"
            + " WHERE operation = 'CREATE' ORDER BY id",
        String.class);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static com.opencop.core.api.component.watchbox.WatchboxTestMapperIdentity.MAPPER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigAuditRecorder;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.api.watchbox.technical.gateway.db.JpaWatchboxConfigAuditStore;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(
    classes = {
      WatchboxWebTestApplication.class,
      WatchboxConfigAuditAtomicityIT.FailAfterPersistRecorderConfig.class
    })
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxConfigAuditAtomicityIT extends AbstractWebIT {

  private static final UUID OWNER_ID = UUID.fromString("650e8400-e29b-41d4-a716-446655442001");
  private static final UUID SOURCE_ID = UUID.fromString("650e8400-e29b-41d4-a716-446655442002");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private MapperProfileService mapperProfileService;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private FailAfterPersistRecorder failAfterPersistRecorder;

  @Autowired
  private com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository
      serverLayerRepository;

  private ConnectionFixtures connectionFixtures;
  private LayerFixtures layerFixtures;
  private UUID originalLayerId;
  private UUID replacementLayerId;

  @BeforeEach
  void setUp() {
    connectionFixtures = new ConnectionFixtures(jdbcTemplate, dataSourceRepository, objectMapper);
    layerFixtures =
        new LayerFixtures(
            jdbcTemplate, layerRepository, layerMembersRepository, serverLayerRepository);

    resetWatchboxes();
    layerFixtures.reset();
    connectionFixtures.reset();
    failAfterPersistRecorder.resetObservations();
    insertUser();
    connectionFixtures.seedDefaultMapper(MAPPER_ID);
    connectionFixtures.persistTcpSource(SOURCE_ID, "audit-atomicity-source", MAPPER_ID, true, null);
    originalLayerId =
        layerFixtures.persistLayerWithMembers(
            "Audit Atomicity Original Layer", Map.of(OWNER_ID, LayerRole.OWNER));
    replacementLayerId =
        layerFixtures.persistLayerWithMembers(
            "Audit Atomicity Replacement Layer", Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, originalLayerId, ServerRole.VIEWER);
    layerFixtures.addServerAccess(SOURCE_ID, replacementLayerId, ServerRole.VIEWER);
    when(mapperProfileService.getLatestMapperProfileFull(MAPPER_ID))
        .thenReturn(
            new MapperProfileFullMeta(
                MapperProfile.builder()
                    .withId(MAPPER_ID)
                    .withName("test-mapper")
                    .withEditable(false)
                    .withIntegrationId(WatchboxTestMapperIdentity.INTEGRATION_ID)
                    .withVersion(1)
                    .withArchived(false)
                    .build(),
                null,
                null));
  }

  @AfterEach
  void tearDown() {
    resetWatchboxes();
    layerFixtures.reset();
    connectionFixtures.reset();
  }

  @Test
  void create_whenAuditRecorderFailsAfterInsert_rollsBackWatchboxLayersAndFlushedAuditRow()
      throws Exception {
    failAfterPersistRecorder.failNext(AuditOperation.CREATE);

    mockMvc
        .perform(
            post("/v1/watchboxes")
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(OWNER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

    assertFailureObservedAfterPersist();
    assertThat(countWatchboxes()).isZero();
    assertThat(countWatchboxLayerJoins()).isZero();
    assertThat(countAuditRows("CREATE")).isZero();
  }

  @Test
  void patch_whenAuditRecorderFailsAfterInsert_rollsBackWatchboxAndFlushedAuditRow()
      throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(OWNER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest()))
            .andExpect(status().isCreated())
            .andReturn();
    var created = readResponse(createResult, WatchboxResponse.class);
    var before = loadWatchboxState(created.id());

    assertThat(before.configEpoch()).isEqualTo(1);
    assertThat(before.polygon()).isEqualTo(objectMapper.valueToTree(created.polygon()));
    assertThat(before.layerIds()).containsExactly(originalLayerId);
    assertThat(countAuditRows(created.id(), "CREATE")).isEqualTo(1);
    assertThat(countAuditRows(created.id(), "PATCH")).isZero();

    failAfterPersistRecorder.failNext(AuditOperation.PATCH);
    mockMvc
        .perform(
            patch("/v1/watchboxes/{watchboxId}", created.id())
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(OWNER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchRequest()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

    assertFailureObservedAfterPersist();
    assertThat(loadWatchboxState(created.id())).isEqualTo(before);
    assertThat(countAuditRows(created.id(), "CREATE")).isEqualTo(1);
    assertThat(countAuditRows(created.id(), "PATCH")).isZero();
  }

  @Test
  void archive_whenAuditRecorderFailsAfterInsert_rollsBackSoftDeleteAndFlushedAuditRow()
      throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(OWNER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest()))
            .andExpect(status().isCreated())
            .andReturn();
    var created = readResponse(createResult, WatchboxResponse.class);
    var before = loadWatchboxState(created.id());

    failAfterPersistRecorder.failNext(AuditOperation.ARCHIVE);
    mockMvc
        .perform(
            delete("/v1/watchboxes/{watchboxId}", created.id())
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(OWNER_ID))))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

    assertFailureObservedAfterPersist();
    assertThat(loadWatchboxState(created.id())).isEqualTo(before);
    assertThat(before.archivedAt()).isNull();
    assertThat(before.archivedBy()).isNull();
    assertThat(countAuditRows(created.id(), "CREATE")).isEqualTo(1);
    assertThat(countAuditRows(created.id(), "ARCHIVE")).isZero();
  }

  private void assertFailureObservedAfterPersist() {
    assertThat(failAfterPersistRecorder.completedDelegateCalls()).isEqualTo(1);
    assertThat(failAfterPersistRecorder.actualTransactionActiveAtInvocation()).isTrue();
    assertThat(failAfterPersistRecorder.auditRowsVisibleBeforeFailure()).isEqualTo(1);
  }

  private PersistedWatchboxState loadWatchboxState(UUID watchboxId) throws Exception {
    var polygon =
        objectMapper.readTree(
            jdbcTemplate.queryForObject(
                "SELECT polygon_json::text FROM watchbox.watchbox WHERE id = ?",
                String.class,
                watchboxId));
    var configEpoch =
        jdbcTemplate.queryForObject(
            "SELECT config_epoch FROM watchbox.watchbox WHERE id = ?", Long.class, watchboxId);
    var layerIds =
        Set.copyOf(
            jdbcTemplate.query(
                "SELECT layer_id FROM watchbox.watchbox_layer WHERE watchbox_id = ?",
                (resultSet, _) -> resultSet.getObject("layer_id", UUID.class),
                watchboxId));
    var archivedAt =
        jdbcTemplate.queryForObject(
            "SELECT archived_at FROM watchbox.watchbox WHERE id = ?",
            java.sql.Timestamp.class,
            watchboxId);
    var archivedBy =
        jdbcTemplate.queryForObject(
            "SELECT archived_by FROM watchbox.watchbox WHERE id = ?", UUID.class, watchboxId);
    return new PersistedWatchboxState(
        polygon,
        configEpoch,
        layerIds,
        archivedAt == null ? null : archivedAt.toInstant(),
        archivedBy);
  }

  private int countAuditRows(UUID watchboxId, String operation) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
          FROM watchbox.watchbox_config_audit
         WHERE watchbox_id = ? AND operation = ?
        """,
        Integer.class,
        watchboxId,
        operation);
  }

  private int countAuditRows(String operation) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM watchbox.watchbox_config_audit WHERE operation = ?",
        Integer.class,
        operation);
  }

  private int countWatchboxes() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM watchbox.watchbox", Integer.class);
  }

  private int countWatchboxLayerJoins() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM watchbox.watchbox_layer", Integer.class);
  }

  private void resetWatchboxes() {
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
  }

  private void insertUser() {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username,
            active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, true, false, now(), now())
        ON CONFLICT (id) DO UPDATE SET
            active = true,
            administrator = false,
            deleted_at = NULL,
            updated_at = now()
        """,
        OWNER_ID,
        "idp-user-" + OWNER_ID,
        "user-" + OWNER_ID,
        "http://localhost/test-issuer",
        "user-" + OWNER_ID);
  }

  private String createRequest() {
    return """
        {
          "name":"audit-atomicity-proof",
          "description":"Transactional audit rollback proof",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"combine":"ALL","predicates":[{"path":"properties.opencop.element.symbol","operation":"IN","values":["10061000001211000000"]}]},
          "enabled":true
        }
        """
        .formatted(originalLayerId);
  }

  private String patchRequest() {
    return """
        {
          "polygon":{"type":"Polygon","coordinates":[[[-101.1,38.9],[-100.9,38.9],[-100.9,39.1],[-101.1,39.1],[-101.1,38.9]]]},
          "layerIds":["%s"],
          "confirmTrackingReset":true
        }
        """
        .formatted(replacementLayerId);
  }

  private record PersistedWatchboxState(
      JsonNode polygon,
      long configEpoch,
      Set<UUID> layerIds,
      java.time.Instant archivedAt,
      UUID archivedBy) {}

  private enum AuditOperation {
    CREATE,
    PATCH,
    ARCHIVE
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FailAfterPersistRecorderConfig {

    @Bean
    @Primary
    FailAfterPersistRecorder failAfterPersistRecorder(
        JpaWatchboxConfigAuditStore delegate, JdbcTemplate jdbcTemplate) {
      return new FailAfterPersistRecorder(delegate, jdbcTemplate);
    }
  }

  static final class FailAfterPersistRecorder implements WatchboxConfigAuditRecorder {

    private final WatchboxConfigAuditRecorder delegate;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean actualTransactionActiveAtInvocation = new AtomicBoolean();
    private final AtomicInteger completedDelegateCalls = new AtomicInteger();
    private final AtomicInteger auditRowsVisibleBeforeFailure = new AtomicInteger(-1);
    private final AtomicReference<AuditOperation> selectedFailure = new AtomicReference<>();

    FailAfterPersistRecorder(WatchboxConfigAuditRecorder delegate, JdbcTemplate jdbcTemplate) {
      this.delegate = delegate;
      this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void recordCreate(WatchboxConfig created, WatchboxCaller actor) {
      record(AuditOperation.CREATE, created.id(), () -> delegate.recordCreate(created, actor));
    }

    @Override
    public void recordPatch(
        WatchboxConfig before,
        WatchboxConfig after,
        List<WatchboxResetReason> resetReasons,
        WatchboxCaller actor) {
      record(
          AuditOperation.PATCH,
          after.id(),
          () -> delegate.recordPatch(before, after, resetReasons, actor));
    }

    @Override
    public void recordArchive(WatchboxConfig before, WatchboxCaller actor) {
      record(AuditOperation.ARCHIVE, before.id(), () -> delegate.recordArchive(before, actor));
    }

    private void record(AuditOperation operation, UUID watchboxId, Runnable persist) {
      actualTransactionActiveAtInvocation.set(
          TransactionSynchronizationManager.isActualTransactionActive());
      persist.run();
      completedDelegateCalls.incrementAndGet();
      auditRowsVisibleBeforeFailure.set(
          jdbcTemplate.queryForObject(
              """
              SELECT COUNT(*)
                FROM watchbox.watchbox_config_audit
               WHERE watchbox_id = ? AND operation = ?
              """,
              Integer.class,
              watchboxId,
              operation.name()));
      if (selectedFailure.compareAndSet(operation, null)) {
        throw new IllegalStateException("induced failure after audit persistence");
      }
    }

    int completedDelegateCalls() {
      return completedDelegateCalls.get();
    }

    boolean actualTransactionActiveAtInvocation() {
      return actualTransactionActiveAtInvocation.get();
    }

    int auditRowsVisibleBeforeFailure() {
      return auditRowsVisibleBeforeFailure.get();
    }

    void failNext(AuditOperation operation) {
      resetObservations();
      if (!selectedFailure.compareAndSet(null, operation)) {
        throw new IllegalStateException("failure already selected");
      }
    }

    void resetObservations() {
      selectedFailure.set(null);
      actualTransactionActiveAtInvocation.set(false);
      completedDelegateCalls.set(0);
      auditRowsVisibleBeforeFailure.set(-1);
    }
  }
}

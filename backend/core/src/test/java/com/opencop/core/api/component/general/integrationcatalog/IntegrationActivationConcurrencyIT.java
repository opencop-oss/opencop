// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import static com.opencop.core.api.support.integration.TestIntegrationCatalogs.selection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.MockAuditRecordingConfig;
import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.connection.NewSocketConnection;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.setup.ConnectionSetup;
import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationInUseException;
import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.business.service.AmbiguousPersistenceOutcomeHandler;
import com.opencop.core.api.general.business.service.ConnectInstanceFailover;
import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.business.service.SelectedMapperLock;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.business.usecase.connection.persistence.CompensatedTransaction;
import com.opencop.core.api.general.business.usecase.connection.persistence.ConnectionPersistenceHelper;
import com.opencop.core.api.general.business.usecase.connection.persistence.ConnectionSetupRechecks;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.permissions.CreateSourcePermissions;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.ManageIntegrationActivation;
import com.opencop.core.api.general.technical.gateway.db.profile.JpaMapperCatalogGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ComponentWebIT(
    classes = {
      IntegrationActivationWebTestApplication.class,
      IntegrationActivationConcurrencyIT.ProductionCreationTestConfiguration.class
    })
class IntegrationActivationConcurrencyIT extends AbstractDatabaseIT {

  @TestConfiguration(proxyBeanMethods = false)
  @Import({
    MockAuditRecordingConfig.class,
    AmbiguousPersistenceOutcomeHandler.class,
    ConnectionPersistenceHelper.class,
    CompensatedTransaction.class,
    ConnectionSetupRechecks.class,
    SelectedMapperLock.class,
    JpaMapperCatalogGateway.class
  })
  static class ProductionCreationTestConfiguration {

    /** The runtime registers the schema version the creation tests select. */
    @Bean
    ConnectionIntegrationSelection connectionIntegrationSelection(
        IntegrationCatalogGateway catalog) {
      return selection(catalog, new IntegrationConnectionSchemaReference(INTEGRATION_ID, 1));
    }
  }

  private static final UUID INTEGRATION_ID =
      UUID.fromString("94cb093e-bcd1-47fb-a0aa-e351dde460a5");
  private static final UUID OTHER_INTEGRATION_ID =
      UUID.fromString("ec6d149b-1b3a-465e-a2b8-344a164c2f1f");
  private static final UUID MAPPER_ID = UUID.fromString("62fb4ad1-90bb-4ff0-b811-4a571843858c");
  private static final UUID ACTOR_ID = UUID.fromString("8029b3db-cdbf-4fda-99ec-a52726423420");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private IntegrationCatalogGateway catalogGateway;
  @Autowired private ConnectionMetadataGateway connectionMetadataGateway;
  @Autowired private ManageIntegrationActivation activation;
  @Autowired private ConnectionPersistenceHelper persistenceHelper;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoSpyBean private DataSourceRepository dataSourceRepository;
  @MockitoBean private ConnectRoutingTargetResolver routingTargetResolver;
  @MockitoBean private ConnectInstanceFailover instanceFailover;
  @MockitoBean private CreateSourcePermissions permissions;

  private TransactionTemplate transaction;

  @BeforeEach
  void resetDatabase() {
    transaction = new TransactionTemplate(transactionManager);
    cleanFixtures();
    seedMapperAndActor();
    when(routingTargetResolver.resolveOwnershipTarget(any(), isNull(), eq("ADD")))
        .thenReturn(new ConnectRoutingTargetResolver.OwnershipTarget("connect-1", true));
  }

  @AfterEach
  void cleanDatabase() {
    cleanFixtures();
  }

  private void cleanFixtures() {
    jdbc.update("DELETE FROM profiles.data_source");
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM config.integration_connection_schema");
    jdbc.update(
        """
        DELETE FROM config.integration i
         WHERE NOT EXISTS (SELECT 1 FROM profiles.mapper m WHERE m.integration_id = i.id)
        """);
    jdbc.update("DELETE FROM opencop_user WHERE id = ?", ACTOR_ID);
  }

  @Test
  void productionCreationCommitMakesConcurrentDeactivationReturnIntegrationInUse()
      throws Exception {
    var reference = new IntegrationConnectionSchemaReference(INTEGRATION_ID, 1);
    seedSchema(INTEGRATION_ID, 1, true);
    var creationReachedPersistence = new CountDownLatch(1);
    var allowCreationCommit = new CountDownLatch(1);
    var deactivationStarted = new CountDownLatch(1);
    // Production reaches this write only after locking the selected catalog row in the same
    // transaction. Pausing here must therefore keep deactivation blocked until creation commits.
    doAnswer(
            invocation -> {
              creationReachedPersistence.countDown();
              await(allowCreationCommit);
              return invocation.callRealMethod();
            })
        .when(dataSourceRepository)
        .persist(any(SocketDataSource.class));

    UUID sourceId;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var creation = executor.submit(() -> createUdpConnection("Created first", 18087, reference));
      assertThat(creationReachedPersistence.await(5, TimeUnit.SECONDS)).isTrue();

      var deactivation =
          executor.submit(
              () -> {
                deactivationStarted.countDown();
                activation.setActive(INTEGRATION_ID, false);
              });
      assertThat(deactivationStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertStillBlocked(deactivation);

      allowCreationCommit.countDown();
      sourceId = creation.get(5, TimeUnit.SECONDS);
      assertFutureCause(deactivation, IntegrationInUseException.class);
    }

    assertThat(activeStates(INTEGRATION_ID)).containsExactly(true);
    assertThat(storedReference(sourceId)).isEqualTo(reference);
  }

  @Test
  void deactivationCommitMakesConcurrentProductionCreationReturnIntegrationInactive()
      throws Exception {
    var reference = new IntegrationConnectionSchemaReference(INTEGRATION_ID, 1);
    seedSchema(INTEGRATION_ID, 1, true);
    var deactivationLockedCatalog = new CountDownLatch(1);
    var allowDeactivationCommit = new CountDownLatch(1);
    var creationStarted = new CountDownLatch(1);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var deactivation =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      _ -> {
                        catalogGateway.findIntegrationForUpdate(INTEGRATION_ID);
                        deactivationLockedCatalog.countDown();
                        await(allowDeactivationCommit);
                        activation.setActive(INTEGRATION_ID, false);
                      }));
      assertThat(deactivationLockedCatalog.await(5, TimeUnit.SECONDS)).isTrue();

      var creation =
          executor.submit(
              () -> {
                creationStarted.countDown();
                return createUdpConnection("Created second", 18088, reference);
              });
      assertThat(creationStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertStillBlocked(creation);

      allowDeactivationCommit.countDown();
      deactivation.get(5, TimeUnit.SECONDS);
      var failure = assertFutureCause(creation, ConnectionManagementException.class);
      assertThat(failure.getCode()).isEqualTo("INTEGRATION_INACTIVE");
    }

    assertThat(activeStates(INTEGRATION_ID)).containsExactly(false);
    assertThat(countSourcesNamed("Created second")).isZero();
  }

  @Test
  void deactivationCommitMakesConcurrentReplacementReturnIntegrationInactive() throws Exception {
    var currentReference = new IntegrationConnectionSchemaReference(OTHER_INTEGRATION_ID, 1);
    var replacement = new IntegrationConnectionSchemaReference(INTEGRATION_ID, 1);
    var sourceId = UUID.fromString("5f93fe86-7ded-457f-af52-462778164999");
    seedSchema(INTEGRATION_ID, 1, true);
    seedSchema(OTHER_INTEGRATION_ID, 1, true);
    dataSourceRepository.persist(kafkaSource(sourceId, "Replace second", currentReference));
    var deactivationLockedCatalog = new CountDownLatch(1);
    var allowDeactivationCommit = new CountDownLatch(1);
    var replacementStarted = new CountDownLatch(1);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var deactivation =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      _ -> {
                        catalogGateway.findIntegrationForUpdate(INTEGRATION_ID);
                        deactivationLockedCatalog.countDown();
                        await(allowDeactivationCommit);
                        activation.setActive(INTEGRATION_ID, false);
                      }));
      assertThat(deactivationLockedCatalog.await(5, TimeUnit.SECONDS)).isTrue();

      var replace =
          executor.submit(
              () -> {
                replacementStarted.countDown();
                replaceReference(sourceId, replacement);
              });
      assertThat(replacementStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertStillBlocked(replace);

      allowDeactivationCommit.countDown();
      deactivation.get(5, TimeUnit.SECONDS);
      var failure = assertFutureCause(replace, ConnectionManagementException.class);
      assertThat(failure.getCode()).isEqualTo("INTEGRATION_INACTIVE");
    }

    assertThat(activeStates(INTEGRATION_ID)).containsExactly(false);
    assertThat(storedReference(sourceId)).isEqualTo(currentReference);
  }

  @Test
  void concurrentActivationRequestsAreAtomicAndLastCommitWins() throws Exception {
    seedSchema(INTEGRATION_ID, 1, true);
    seedSchema(INTEGRATION_ID, 2, true);
    var deactivationUpdatedEveryVersion = new CountDownLatch(1);
    var allowDeactivationCommit = new CountDownLatch(1);
    var reactivationStarted = new CountDownLatch(1);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var deactivate =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      _ -> {
                        activation.setActive(INTEGRATION_ID, false);
                        deactivationUpdatedEveryVersion.countDown();
                        await(allowDeactivationCommit);
                      }));
      assertThat(deactivationUpdatedEveryVersion.await(5, TimeUnit.SECONDS)).isTrue();

      var reactivate =
          executor.submit(
              () -> {
                reactivationStarted.countDown();
                activation.setActive(INTEGRATION_ID, true);
              });
      assertThat(reactivationStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertStillBlocked(reactivate);
      assertThat(activeStates(INTEGRATION_ID)).containsExactly(true);

      allowDeactivationCommit.countDown();
      deactivate.get(5, TimeUnit.SECONDS);
      reactivate.get(5, TimeUnit.SECONDS);
    }

    assertThat(activeStates(INTEGRATION_ID)).containsExactly(true);
  }

  private void replaceReference(UUID sourceId, IntegrationConnectionSchemaReference replacement) {
    transaction.executeWithoutResult(
        _ -> {
          connectionMetadataGateway.lockById(sourceId);
          assertSelectable(replacement);
          connectionMetadataGateway.apply(
              sourceId,
              new ValidatedConnectionMetadataPatch(null, null, null, replacement),
              ACTOR_ID);
        });
  }

  private void assertSelectable(IntegrationConnectionSchemaReference reference) {
    ConnectionIntegrationReferencePolicy.assertSelectable(
        reference,
        catalogGateway.findSchemaOwnerForUpdate(
            new IntegrationConnectionSchemaKey(
                reference.integrationId(), reference.schemaVersion())));
  }

  private KafkaDataSource kafkaSource(
      UUID sourceId, String name, IntegrationConnectionSchemaReference integration) {
    return new KafkaDataSource(
        sourceId,
        name,
        "default",
        "topic-" + sourceId,
        "user-" + sourceId,
        MAPPER_ID,
        integration,
        AccessMode.READ,
        true,
        null,
        null,
        null,
        ACTOR_ID,
        null,
        null,
        null,
        "activation-test-broker",
        true);
  }

  /** Stores a UDP connection the way a creation does, returning its id. */
  private UUID createUdpConnection(
      String name, int port, IntegrationConnectionSchemaReference integration) {
    var sourceId = UUID.randomUUID();
    persistenceHelper.persistAndStart(
        new NewSocketConnection(
            sourceId,
            name,
            TestActors.of(ACTOR_ID),
            new ConnectionSetup(
                new ReadAccess(MAPPER_ID),
                integration,
                new ConnectionSpec(new UdpConfig("127.0.0.1", port, null)),
                SettingsDocument.empty(),
                new SetupPrefill(UUID.randomUUID(), Map.of()),
                Optional.empty())));
    return sourceId;
  }

  private void seedSchema(UUID integrationId, int schemaVersion, boolean active) {
    jdbc.update(
        """
        INSERT INTO config.integration (id, name, description, active)
        VALUES (?, ?, 'Concurrency test integration', ?)
        ON CONFLICT (id) DO NOTHING
        """,
        integrationId,
        "Integration " + integrationId,
        active);
    jdbc.update(
        """
        INSERT INTO config.integration_connection_schema (integration_id, schema_version)
        VALUES (?, ?)
        """,
        integrationId,
        schemaVersion);
  }

  private List<Boolean> activeStates(UUID integrationId) {
    return jdbc.queryForList(
        """
        SELECT active
        FROM config.integration
        WHERE id = ?
        """,
        Boolean.class,
        integrationId);
  }

  private IntegrationConnectionSchemaReference storedReference(UUID sourceId) {
    return jdbc.queryForObject(
        """
        SELECT integration_id, connection_schema_version
        FROM profiles.data_source
        WHERE id = ?
        """,
        (result, _) ->
            new IntegrationConnectionSchemaReference(
                result.getObject("integration_id", UUID.class),
                result.getInt("connection_schema_version")),
        sourceId);
  }

  private long countSourcesNamed(String name) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM profiles.data_source WHERE name = ?", Long.class, name);
  }

  private static void assertStillBlocked(Future<?> future) {
    assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  private static <T extends Throwable> T assertFutureCause(
      Future<?> future, Class<T> expectedType) {
    try {
      future.get(5, TimeUnit.SECONDS);
      throw new AssertionError("Expected future to fail with " + expectedType.getSimpleName());
    } catch (ExecutionException exception) {
      assertThat(exception.getCause()).isInstanceOf(expectedType);
      return expectedType.cast(exception.getCause());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while awaiting future", exception);
    } catch (TimeoutException exception) {
      throw new AssertionError("Timed out awaiting future failure", exception);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out at transaction race barrier");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted at transaction race barrier", exception);
    }
  }

  private void seedMapperAndActor() {
    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier)
        VALUES (?, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        MAPPER_ID);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, mapper)
        VALUES (?, 1, ?, '{}'::jsonb)
        """,
        MAPPER_ID,
        "activation-concurrency-mapper");
    jdbc.update(
        """
        INSERT INTO opencop_user(
          id, idp_user_id, idp_username, idp_issuer,
          username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        ACTOR_ID,
        "idp-" + ACTOR_ID,
        "activation-concurrency-actor",
        "https://issuer.test",
        "activation-concurrency-actor",
        "activation-concurrency@example.test",
        "Activation",
        "Actor",
        "Activation Actor");
  }
}

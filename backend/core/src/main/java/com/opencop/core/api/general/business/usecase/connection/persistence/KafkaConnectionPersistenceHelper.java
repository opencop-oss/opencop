// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.connection.NewConnection;
import com.opencop.core.api.general.business.model.connection.NewKafkaConnection;
import com.opencop.core.api.general.business.service.AmbiguousPersistenceOutcomeHandler;
import com.opencop.core.api.general.business.service.KafkaConnectionPersistenceService;
import com.opencop.core.api.general.business.usecase.connection.security.kafka.KafkaSecurityProvisionerRegistry;
import com.opencop.core.api.general.business.usecase.connection.security.kafka.ProvisionedKafkaSecurity;
import com.opencop.core.api.general.ports.gateway.kafka.ByodSourceStatePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceConstraintViolationException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceNameConflictException;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.KafkaSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistence flow for a Kafka connection: provision the broker resources its security material
 * asks for (a topic, a SCRAM user, and an ACL), persist the connection with its permissions, then
 * publish it to the BYOD consumers.
 *
 * <p>Does not use {@link ConnectionPersistenceHelper}, which serves the socket connections:
 * listen-port check, Connect instance assignment, start nudge.
 *
 * <p>Deterministic persistence rejections happen before commit and deprovision the broker
 * resources. Other persistence exceptions can also represent a commit-completion failure, so the
 * outcome is treated as unknown: durable state is probed and the resources are preserved for
 * reconciliation instead of risking a committed data source whose credentials were deleted.
 *
 * <p>Once the connection is committed, publishing it to the BYOD consumers is best-effort: the
 * producer credentials are shown only once, so a broker failure at that point must not turn the
 * creation into an error. Startup reconciliation and starting the connection both publish it again.
 *
 * <p>SCRAM credentials are not rotated in place. It is still unclear whether a connected producer
 * can use an updated password or whether rotation requires a new topic.
 */
public class KafkaConnectionPersistenceHelper {

  private static final Logger log = LoggerFactory.getLogger(KafkaConnectionPersistenceHelper.class);

  private static final String DEFAULT_TENANT = "default";

  private final KafkaConnectionPersistenceService persistenceService;
  private final AmbiguousPersistenceOutcomeHandler ambiguousOutcomeHandler;
  private final ByodSourceStatePublisher sourcePublisher;
  private final String providerName;
  private final KafkaSecurityProvisionerRegistry securityProvisionerRegistry;

  public KafkaConnectionPersistenceHelper(
      KafkaProvisioningService provisioner,
      KafkaConnectionPersistenceService persistenceService,
      AmbiguousPersistenceOutcomeHandler ambiguousOutcomeHandler,
      ByodSourceStatePublisher sourcePublisher,
      String providerName) {
    this.persistenceService = persistenceService;
    this.ambiguousOutcomeHandler = ambiguousOutcomeHandler;
    this.sourcePublisher = sourcePublisher;
    this.providerName = providerName;
    this.securityProvisionerRegistry = KafkaSecurityProvisionerRegistry.standard(provisioner);
  }

  /**
   * Provisions the connection's broker resources and persists it.
   *
   * @return the topic and producer credentials, which the creation answers with once
   */
  public KafkaCredentials provisionAndPersist(NewKafkaConnection connection) {
    var setup = connection.setup();
    return provisionAndPersist(
        new NewConnection(
            connection.sourceId(),
            connection.name(),
            setup.access(),
            setup.integration(),
            SettingsDocument.empty(),
            setup.prefill()),
        setup.security(),
        connection.owner());
  }

  private KafkaCredentials provisionAndPersist(
      NewConnection connection, KafkaSecurityMaterial security, Caller requestedBy) {
    var provisioned =
        securityProvisionerRegistry.provision(
            connection.sourceId(), connection.sourceName(), security);
    var credentials = provisioned.credentials();
    var dataSource = dataSource(connection, credentials, requestedBy);

    // Credentials stored in Vault at opencop/shared/kafka-ds/{sourceId} during provisioning.
    // DB stores only topicName + username (no password).
    Instant updatedAt;
    try {
      updatedAt =
          persistenceService.persistNewConnection(
              dataSource, connection.integration(), connection.prefill(), requestedBy);
    } catch (DataSourceNameConflictException _) {
      release(provisioned);
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.CONNECTION_NAME_ALREADY_EXISTS, connection.sourceName());
    } catch (DataSourceConstraintViolationException e) {
      // Rejected but unattributable: there is no row to clean up later, so the broker resources
      // this request provisioned have to be released before the failure surfaces.
      release(provisioned);
      throw e;
    } catch (ConnectionManagementException e) {
      // Catalog and archived-mapper rejections are raised inside the transaction body, before any
      // database commit.
      release(provisioned);
      throw e;
    } catch (RuntimeException e) {
      ambiguousOutcomeHandler.preserveAfterFailure(
          dataSource.id(),
          TransportKind.KAFKA,
          AmbiguousPersistenceOutcomeHandler.FailurePhase.PERSISTENCE_TRANSACTION,
          dataSource.topicName(),
          e);
      throw e;
    }

    publishStarted(connection.sourceId(), credentials, updatedAt);
    return credentials;
  }

  private void publishStarted(UUID sourceId, KafkaCredentials credentials, Instant updatedAt) {
    try {
      sourcePublisher.publish(
          new ByodSourceState(
              sourceId,
              credentials.topicName(),
              credentials.username(),
              ByodSourceState.Status.STARTED,
              updatedAt,
              ByodSourceState.CURRENT_VERSION));
    } catch (RuntimeException e) {
      log.warn(
          "Kafka source {} was created but could not be published to the BYOD consumers; it will"
              + " be published again at startup or when the connection is started",
          sourceId,
          e);
    }
  }

  /** Releases what provisioning created on the broker, for a connection rejected before commit. */
  private static void release(ProvisionedKafkaSecurity security) {
    security.cleanup().ifPresent(Runnable::run);
  }

  private KafkaDataSource dataSource(
      NewConnection connection, KafkaCredentials credentials, Caller requestedBy) {
    return new KafkaDataSource(
        connection.sourceId(),
        connection.sourceName(),
        DEFAULT_TENANT,
        credentials.topicName(),
        credentials.username(),
        connection.access().ingressMapperId().orElse(null),
        connection.integration(),
        connection.access().mode(),
        true, // enabled by default
        null, // archivedAt - null while active
        null, // archivedBy - null while active
        null, // createdAt - set by DB
        requestedBy.userId(),
        null, // updatedAt - set by DB
        null, // updatedBy - null on creation
        null, // lastEventAt - null until first event
        providerName,
        true);
  }
}

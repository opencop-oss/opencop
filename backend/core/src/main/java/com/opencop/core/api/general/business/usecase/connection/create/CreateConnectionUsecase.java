// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.create;

import com.opencop.core.api.general.business.model.connection.NewKafkaConnection;
import com.opencop.core.api.general.business.model.connection.NewSocketConnection;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationResult;
import com.opencop.core.api.general.business.model.connection.create.CreationReceipt;
import com.opencop.core.api.general.business.model.connection.setup.ConnectionSetup;
import com.opencop.core.api.general.business.policy.connection.ConnectionNamePolicy;
import com.opencop.core.api.general.business.usecase.connection.persistence.ConnectionPersistenceHelper;
import com.opencop.core.api.general.business.usecase.connection.persistence.KafkaConnectionPersistenceHelper;
import com.opencop.core.api.general.business.usecase.connection.setup.ConnectionSetupResolution;
import com.opencop.core.api.general.ports.gateway.notification.NotificationGateway;
import com.opencop.core.api.general.ports.usecase.connection.create.CreateConnection;
import com.opencop.core.api.general.ports.usecase.connection.retrieve.RetrieveConnection;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates a connection from a v2 request.
 *
 * <p>Checks the name, then has the {@link ConnectionSetupResolution} check every selection of the
 * setup and prepare it for the new connection, and stores the connection with it. Once the
 * connection is durably stored, it tells the owner's other sessions they hold it, and answers with
 * the connection as the owner now sees it.
 */
@Component
public class CreateConnectionUsecase implements CreateConnection {

  private static final Logger log = LoggerFactory.getLogger(CreateConnectionUsecase.class);

  private final ConnectionSetupResolution setupResolution;
  private final ConnectionPersistenceHelper socketPersistence;
  private final KafkaConnectionPersistenceHelper kafkaPersistence;
  private final NotificationGateway notifications;
  private final RetrieveConnection retrieveConnection;

  public CreateConnectionUsecase(
      ConnectionSetupResolution setupResolution,
      ConnectionPersistenceHelper socketPersistence,
      KafkaConnectionPersistenceHelper kafkaPersistence,
      NotificationGateway notifications,
      RetrieveConnection retrieveConnection) {
    this.setupResolution = setupResolution;
    this.socketPersistence = socketPersistence;
    this.kafkaPersistence = kafkaPersistence;
    this.notifications = notifications;
    this.retrieveConnection = retrieveConnection;
  }

  @Override
  public ConnectionCreationResult create(ConnectionCreation creation) {
    var details = creation.details();
    ConnectionNamePolicy.require(details.name());
    var sourceId = UUID.randomUUID();

    var receipt =
        switch (creation) {
          case ConnectionCreation.Tcp tcp -> {
            store(sourceId, details, setupResolution.resolve(tcp, sourceId));
            yield new CreationReceipt.Tcp();
          }
          case ConnectionCreation.Udp udp -> {
            store(sourceId, details, setupResolution.resolve(udp, sourceId));
            yield new CreationReceipt.Udp();
          }
          case ConnectionCreation.Rtsp rtsp -> {
            store(sourceId, details, setupResolution.resolve(rtsp, sourceId));
            yield new CreationReceipt.Rtsp();
          }
          case ConnectionCreation.Kafka kafka ->
              new CreationReceipt.Kafka(
                  kafkaPersistence.provisionAndPersist(
                      new NewKafkaConnection(
                          sourceId,
                          details.name(),
                          details.requestedBy(),
                          setupResolution.resolve(kafka))));
        };

    notifyOwner(sourceId, details.requestedBy().userId());
    return new ConnectionCreationResult(
        retrieveConnection.get(sourceId, details.requestedBy().userId()), receipt);
  }

  private void store(UUID sourceId, ConnectionCreationDetails details, ConnectionSetup setup) {
    socketPersistence.persistAndStart(
        new NewSocketConnection(sourceId, details.name(), details.requestedBy(), setup));
  }

  /**
   * Tells the owner's other sessions about the connection. The connection is already stored, so a
   * notification that cannot be sent must not turn the creation into a failure.
   */
  private void notifyOwner(UUID sourceId, UUID ownerId) {
    try {
      notifications.notifyAddedOwner(sourceId, ownerId);
    } catch (RuntimeException exception) {
      log.warn("Failed to notify the owner of new connection {}", sourceId, exception);
    }
  }
}

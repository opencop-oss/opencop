// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** Holds the connection's row lock for a transaction that runs the work. */
@Repository
public class TransactionalConnectionLock implements ConnectionLock {

  private final ConnectionMetadataGateway connections;
  private final TransactionTemplate transactions;

  public TransactionalConnectionLock(
      ConnectionMetadataGateway connections, TransactionTemplate transactions) {
    this.connections = connections;
    this.transactions = transactions;
  }

  @Override
  public <T> T holding(UUID sourceId, Function<ConnectionMetadata, T> work) {
    return transactions.execute(_ -> work.apply(connections.lockById(sourceId)));
  }
}

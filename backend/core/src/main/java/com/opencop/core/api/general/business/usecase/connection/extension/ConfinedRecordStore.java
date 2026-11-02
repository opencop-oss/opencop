// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.api.general.business.model.connection.extension.RecordLimits;
import com.opencop.core.api.general.business.model.connection.extension.RecordScope;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.business.policy.connection.extension.ConnectionRecordPolicy;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExpectedRevision;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionRecordStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One extension's records on one connection, written as one user. Every call is confined to the
 * scope the host bound, so an extension cannot name another connection's or extension's records.
 *
 * <p>Writes hold the connection's row lock for their transaction, so the writes of one connection
 * run one at a time: a revision cannot change between its check and its write, and the count limit
 * is exact. A write is refused once the connection is deleted or bound to another integration than
 * the one the extension was invoked for, so no record is written for an integration it left.
 */
final class ConfinedRecordStore implements ConnectionRecordStore {

  private static final String OPERATION = "write extension records";

  private final RecordScope scope;
  private final IntegrationConnectionSchemaReference integration;
  private final UUID userId;
  private final ConnectionRecordGateway records;
  private final ConnectionLock lock;
  private final RecordLimits limits;

  ConfinedRecordStore(
      RecordScope scope,
      IntegrationConnectionSchemaReference integration,
      UUID userId,
      ConnectionRecordGateway records,
      ConnectionLock lock,
      RecordLimits limits) {
    this.scope = scope;
    this.integration = integration;
    this.userId = userId;
    this.records = records;
    this.lock = lock;
    this.limits = limits;
  }

  @Override
  public Optional<ConnectionRecord> get(String collection, String key) {
    return records.find(scope, requireName(collection, "collection"), requireName(key, "key"));
  }

  @Override
  public List<ConnectionRecord> list(String collection) {
    return records.list(scope, requireName(collection, "collection"));
  }

  @Override
  public ConnectionRecord put(
      String collection, String key, Map<String, Object> document, ExpectedRevision expected)
      throws ConnectionExtensionException {
    requireName(collection, "collection");
    requireName(key, "key");
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(expected, "expected");
    return inWriteTransaction(
        () -> {
          var current = records.find(scope, collection, key);
          var revision = ConnectionRecordPolicy.nextRevision(current, expected);
          var isNew = current.isEmpty();
          ConnectionRecordPolicy.assertWithinLimits(
              records.storedSize(document), isNew, isNew ? records.count(scope) : 0, limits);
          return records.save(scope, collection, key, document, revision, userId);
        });
  }

  @Override
  public void delete(String collection, String key, ExpectedRevision expected)
      throws ConnectionExtensionException {
    requireName(collection, "collection");
    requireName(key, "key");
    Objects.requireNonNull(expected, "expected");
    inWriteTransaction(
        () -> {
          if (ConnectionRecordPolicy.isDeletable(records.find(scope, collection, key), expected)) {
            records.delete(scope, collection, key);
          }
          return null;
        });
  }

  private <T> T inWriteTransaction(Write<T> write) throws ConnectionExtensionException {
    try {
      return lock.holding(
          scope.sourceId(),
          connection -> {
            ResourceLifecyclePolicy.assertDataSourceActive(connection.archivedAt(), OPERATION);
            ConnectionIntegrationReferencePolicy.assertStillBound(
                connection.integration(), integration);
            try {
              return write.run();
            } catch (ConnectionExtensionException refused) {
              throw new RefusedWrite(refused);
            }
          });
    } catch (RefusedWrite refused) {
      throw refused.refusal;
    }
  }

  private static String requireName(String name, String field) {
    if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
          field + " must be non-blank and at most " + MAX_NAME_LENGTH + " characters");
    }
    return name;
  }

  @FunctionalInterface
  private interface Write<T> {
    T run() throws ConnectionExtensionException;
  }

  /** Carries a refused write out of the transaction, rolling it back. */
  private static final class RefusedWrite extends RuntimeException {

    private final transient ConnectionExtensionException refusal;

    private RefusedWrite(ConnectionExtensionException refusal) {
      super(refusal);
      this.refusal = refusal;
    }
  }
}

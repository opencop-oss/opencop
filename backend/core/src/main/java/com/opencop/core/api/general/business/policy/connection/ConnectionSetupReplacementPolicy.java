// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a stored connection's setup can be replaced by another one.
 *
 * <p>Only a socket connection's setup can: a Kafka connection's resources are provisioned by the
 * broker. A replacement keeps the connection's transport kind; it may bind the connection to
 * another integration or schema version, which its resolution then checks the whole setup against.
 */
public final class ConnectionSetupReplacementPolicy {

  /** The operation a replacement is refused for, once its connection is archived. */
  public static final String OPERATION = "replace setup";

  private ConnectionSetupReplacementPolicy() {}

  /** The stored connection as the socket connection the setup replaces, once it may. */
  public static SocketDataSource requireReplaceable(DataSource stored, ConnectionCreation setup) {
    if (!(stored instanceof SocketDataSource socket)) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.CONNECTION_SETUP_NOT_SUPPORTED, stored.provider());
    }
    if (setup.kind() != socket.provider()) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.CONNECTION_KIND_CHANGE_UNSUPPORTED);
    }
    return socket;
  }

  /**
   * The secrets the replaced spec read that its replacement no longer does, which the replacement
   * leaves for deletion. A store the replacement still reads is kept.
   */
  public static List<String> retiredSecretRefs(
      ConnectionSpec replaced, ConnectionSpec replacement) {
    var kept = Set.copyOf(replacement.secretRefs());
    return replaced.secretRefs().stream().filter(ref -> !kept.contains(ref)).toList();
  }

  /**
   * Requires a replacement that moves the connection to another integration to find no records its
   * extensions wrote: they are user data in the current integration's own shape. A new schema
   * version of the same integration keeps them; its extensions read their own older records.
   *
   * @param current the integration the connection binds to, or null for none
   */
  public static void assertRecordsKept(
      IntegrationConnectionSchemaReference current,
      IntegrationConnectionSchemaReference requested,
      boolean hasRecords) {
    if (hasRecords && changesIntegration(current, requested)) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.CONNECTION_RECORDS_PRESENT);
    }
  }

  /**
   * Whether the requested schema moves the connection to another integration.
   *
   * @param current the integration the connection binds to, or null for none
   */
  public static boolean changesIntegration(
      IntegrationConnectionSchemaReference current,
      IntegrationConnectionSchemaReference requested) {
    return current != null && !current.integrationId().equals(requested.integrationId());
  }
}

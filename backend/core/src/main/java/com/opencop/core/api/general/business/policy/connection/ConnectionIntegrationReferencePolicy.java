// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.mapping.ConnectionMappingDeclaration;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides whether one exact connection schema version may be selected by a connection, and whether
 * it declares a mapping for the connection's transport.
 */
public final class ConnectionIntegrationReferencePolicy {

  private ConnectionIntegrationReferencePolicy() {}

  /**
   * Requires the referenced connection schema version to be in the catalog and its integration to
   * be active.
   *
   * @param owner the integration offering that schema version; empty when the version is unknown
   */
  public static void assertSelectable(
      IntegrationConnectionSchemaReference reference, Optional<IntegrationIdentity> owner) {
    var integration =
        owner.orElseThrow(
            () ->
                new ConnectionManagementException(
                    ConnectionExceptionCodes.INTEGRATION_NOT_FOUND,
                    reference.integrationId(),
                    reference.schemaVersion()));
    if (!integration.active()) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.INTEGRATION_INACTIVE, reference.integrationId());
    }
  }

  /**
   * Requires this runtime to register an implementation for the referenced schema version. Callers
   * check {@link #assertSelectable} first, so an unknown or inactive integration is reported as
   * such rather than as unavailable.
   *
   * @param registered whether this runtime registers the referenced schema version
   */
  public static void assertAvailable(
      IntegrationConnectionSchemaReference reference, boolean registered) {
    if (!registered) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE,
          reference.integrationId(),
          reference.schemaVersion());
    }
  }

  /**
   * Requires the connection to still bind to the integration an extension was invoked for. One
   * moved to another integration since no longer offers that integration's extensions, so it is
   * answered as that integration being unavailable to it; a newer schema version of the same
   * integration still is.
   *
   * @param current the integration the connection binds to now, or null for none
   */
  public static void assertStillBound(
      IntegrationConnectionSchemaReference current, IntegrationConnectionSchemaReference invoked) {
    if (!bindsTo(current, invoked.integrationId())) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE,
          invoked.integrationId(),
          invoked.schemaVersion());
    }
  }

  /**
   * Whether the connection binds to some schema version of the integration.
   *
   * @param current the integration the connection binds to now, or null for none
   */
  public static boolean bindsTo(IntegrationConnectionSchemaReference current, UUID integrationId) {
    return current != null && current.integrationId().equals(integrationId);
  }

  /** Requires the schema version to declare a mapping for the connection's transport. */
  public static <D extends ConnectionMappingDeclaration> D requireDeclaration(
      Optional<D> declaration, IntegrationConnectionSchemaReference integration, String protocol) {
    return declaration.orElseThrow(
        () ->
            new ConnectionManagementException(
                ConnectionExceptionCodes.INTEGRATION_DECLARATION_NOT_FOUND,
                integration.integrationId(),
                integration.schemaVersion(),
                protocol));
  }
}

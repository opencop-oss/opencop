// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetup;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupOutcome;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupRejection;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Decides whether a connection schema version can describe its connection setup. */
public final class IntegrationConnectionSetupPolicy {

  private IntegrationConnectionSetupPolicy() {}

  /**
   * Routes one request through the conditions that make connection setup describable, in the order
   * that keeps the answer stable.
   *
   * <p>Activation belongs to the integration rather than to any one schema version, so it is read
   * from the owning integration. It is checked before availability because it is an administrator
   * decision recorded in the catalog: reporting it first makes every runtime answer alike, whereas
   * availability is a property of the runtime that happens to serve the request.
   *
   * @param requested the connection schema version the client asked about
   * @param schemaOwner the integration offering that version, empty when the catalog has no such
   *     version
   * @param registeredSchema the schema bean matching the requested version in this runtime, empty
   *     when this runtime registers none
   * @param protocols the protocols this runtime can set a connection up over for that version
   */
  public static IntegrationConnectionSetupOutcome describe(
      IntegrationConnectionSchemaKey requested,
      Optional<IntegrationIdentity> schemaOwner,
      Optional<IntegrationConnectionSchema> registeredSchema,
      List<ProtocolConnectionSetup> protocols) {
    Objects.requireNonNull(requested, "requested");
    Objects.requireNonNull(schemaOwner, "schemaOwner");
    Objects.requireNonNull(registeredSchema, "registeredSchema");
    Objects.requireNonNull(protocols, "protocols");

    var unavailable = unavailability(schemaOwner, registeredSchema);
    if (unavailable.isPresent()) {
      return notDescribable(unavailable.orElseThrow());
    }
    if (protocols.isEmpty()) {
      return notDescribable(IntegrationConnectionSetupRejection.PROTOCOL_UNSUPPORTED);
    }
    return new IntegrationConnectionSetupOutcome.Described(
        new IntegrationConnectionSetup(requested, protocols));
  }

  /**
   * Returns why a connection schema version cannot be described at all, whatever is asked about it:
   * unknown to the catalog, deactivated by an administrator, or not registered in this runtime.
   * Activation is checked before availability for the reason given on {@link #describe}.
   *
   * @param schemaOwner the integration offering the version, empty when the catalog has no such
   *     version
   * @param registeredSchema the schema bean matching the version in this runtime, empty when this
   *     runtime registers none
   */
  public static Optional<IntegrationConnectionSetupRejection> unavailability(
      Optional<IntegrationIdentity> schemaOwner,
      Optional<IntegrationConnectionSchema> registeredSchema) {
    Objects.requireNonNull(schemaOwner, "schemaOwner");
    Objects.requireNonNull(registeredSchema, "registeredSchema");
    if (schemaOwner.isEmpty()) {
      return Optional.of(IntegrationConnectionSetupRejection.UNKNOWN);
    }
    if (!schemaOwner.orElseThrow().active()) {
      return Optional.of(IntegrationConnectionSetupRejection.INACTIVE);
    }
    if (registeredSchema.isEmpty()) {
      return Optional.of(IntegrationConnectionSetupRejection.UNAVAILABLE);
    }
    return Optional.empty();
  }

  private static IntegrationConnectionSetupOutcome notDescribable(
      IntegrationConnectionSetupRejection reason) {
    return new IntegrationConnectionSetupOutcome.NotDescribable(reason);
  }
}

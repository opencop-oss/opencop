// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.mapping;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.policy.connection.MapperDeclarationPolicy;
import com.opencop.core.api.general.business.policy.connection.TcpMapperFramingPolicy;
import com.opencop.core.api.general.business.policy.connection.access.EgressMapperAvailabilityPolicy;
import com.opencop.core.api.general.business.policy.connection.access.EgressMapperSelectionPolicy;
import com.opencop.core.api.general.business.policy.connection.access.IngressMapperSelectionPolicy;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpFraming;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperReference;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Checks the mappers a connection selects against what its integration declares for its transport,
 * and that each can be selected, whether the connection is being created or reconfigured. Each
 * check takes the declaration's own shape, so the shape decides which rules apply rather than the
 * transport's name.
 */
@Component
public class ConnectionMapperSelection {

  private final MapperProfileService mappers;
  private final EgressMapperContributions egressMappers;

  public ConnectionMapperSelection(
      MapperProfileService mappers, EgressMapperContributions egressMappers) {
    this.mappers = mappers;
    this.egressMappers = egressMappers;
  }

  /**
   * The revision of the ingress mapper of a connection that frames its stream, once the integration
   * permits it with the framing the connection splits its stream with and it can be selected.
   */
  public MapperReference ingress(
      MapperFramingDeclaration declaration, UUID mapperId, ResolvedTcpFraming framing) {
    TcpMapperFramingPolicy.assertPermits(declaration, mapperId, framing);
    return revision(IngressMapperSelectionPolicy.requireSelectable(mappers.findLatest(mapperId)));
  }

  /**
   * The revision of a connection's egress mapper, once the integration lets the connection write
   * with it and it is available. Only a connection that frames its stream writes.
   */
  public MapperReference egress(MapperFramingDeclaration declaration, UUID mapperId) {
    EgressMapperSelectionPolicy.assertPermits(declaration, mapperId);
    var catalogEntry = mappers.findLatest(mapperId);
    EgressMapperAvailabilityPolicy.requireAvailable(
        egressMappers.find(mapperId), catalogEntry, ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
    return revision(catalogEntry.orElseThrow());
  }

  /**
   * Requires the integration to permit the ingress mapper of a connection that does not frame its
   * stream, and the mapper to be selectable.
   */
  public void ingress(MapperDeclaration declaration, UUID mapperId) {
    MapperDeclarationPolicy.assertAllows(declaration, mapperId);
    IngressMapperSelectionPolicy.requireSelectable(declaration, mappers.findLatest(mapperId));
  }

  private static MapperReference revision(MapperProfile mapper) {
    return new MapperReference(mapper.id(), mapper.version());
  }
}

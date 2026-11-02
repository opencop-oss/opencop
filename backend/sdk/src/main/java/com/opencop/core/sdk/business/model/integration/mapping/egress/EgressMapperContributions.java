// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.egress;

import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.integration.mapping.EgressMappingContribution;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The admitted egress mappers integrations contribute, grouped by owning integration and found by
 * identifier. Contributions without a usable identity are rejected by admission before this value
 * is built.
 */
public final class EgressMapperContributions {

  private final List<IntegrationEgressMappers> groups;
  private final Map<UUID, PayloadEgressMapper> mappersById;

  private EgressMapperContributions(List<? extends EgressMappingContribution> contributions) {
    this.groups =
        Objects.requireNonNull(contributions, "contributions").stream()
            .map(group -> new IntegrationEgressMappers(group.integration(), group.mappers()))
            .toList();
    var byId = new HashMap<UUID, PayloadEgressMapper>();
    for (var group : this.groups) {
      for (var mapper : group.mappers()) {
        byId.putIfAbsent(mapper.id(), mapper);
      }
    }
    this.mappersById = Map.copyOf(byId);
  }

  public static EgressMapperContributions of(
      List<? extends EgressMappingContribution> contributions) {
    return new EgressMapperContributions(contributions);
  }

  public List<IntegrationEgressMappers> groups() {
    return groups;
  }

  public Optional<PayloadEgressMapper> find(UUID egressMapperId) {
    if (egressMapperId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(mappersById.get(egressMapperId));
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.gateway.mapping;

import com.opencop.core.api.watchbox.ports.gateway.WatchboxMapperIdentity;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.ObjectIdStability;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IngressMapperTraitWatchboxMapperIdentity implements WatchboxMapperIdentity {

  private static final Logger log =
      LoggerFactory.getLogger(IngressMapperTraitWatchboxMapperIdentity.class);

  private final Map<UUID, ObjectIdStability> objectIdStabilityByMapperId;
  private final Set<UUID> duplicateMapperIds;

  public IngressMapperTraitWatchboxMapperIdentity(IngressMapperContributions contributions) {
    var stabilityByMapperId = new HashMap<UUID, ObjectIdStability>();
    var duplicateIds = new HashSet<UUID>();

    for (var mapper : contributions.mappers()) {
      if (mapper == null || mapper.id() == null) {
        continue;
      }

      var mapperId = mapper.id();
      var stability =
          Objects.requireNonNullElse(mapper.objectIdStability(), ObjectIdStability.UNKNOWN);
      if (stabilityByMapperId.putIfAbsent(mapperId, stability) != null) {
        duplicateIds.add(mapperId);
      }
    }

    if (!duplicateIds.isEmpty()) {
      log.warn(
          "Duplicate ingress mapper IDs found; Watchbox will treat them as unstable: {}",
          duplicateIds);
    }

    this.objectIdStabilityByMapperId = Map.copyOf(stabilityByMapperId);
    this.duplicateMapperIds = Set.copyOf(duplicateIds);
  }

  @Override
  public boolean hasStableObjectId(UUID mapperId) {
    if (mapperId == null || duplicateMapperIds.contains(mapperId)) {
      return false;
    }
    return objectIdStabilityByMapperId.get(mapperId) == ObjectIdStability.STABLE_PER_ENTITY;
  }
}

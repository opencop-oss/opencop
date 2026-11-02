// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.profile;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Complete, immutable set of catalog writes and isolated rejections for one reconciliation. */
public record PluginMapperCatalogReconciliationPlan(
    List<MapperProfile> insertions,
    List<MetadataUpdate> metadataUpdates,
    List<IntegrationAssignment> integrationAssignments,
    List<LifecycleUpdate> lifecycleUpdates,
    List<Rejection> rejections) {

  public PluginMapperCatalogReconciliationPlan {
    insertions = List.copyOf(Objects.requireNonNull(insertions, "insertions"));
    metadataUpdates = List.copyOf(Objects.requireNonNull(metadataUpdates, "metadataUpdates"));
    integrationAssignments =
        List.copyOf(Objects.requireNonNull(integrationAssignments, "integrationAssignments"));
    lifecycleUpdates = List.copyOf(Objects.requireNonNull(lifecycleUpdates, "lifecycleUpdates"));
    rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
  }

  public record MetadataUpdate(UUID mapperId, String name, String description) {
    public MetadataUpdate {
      Objects.requireNonNull(mapperId, "mapperId");
      Objects.requireNonNull(name, "name");
    }
  }

  /** Records the integration that owns a mapper, for a new entry or one owned by another. */
  public record IntegrationAssignment(UUID mapperId, UUID integrationId) {
    public IntegrationAssignment {
      Objects.requireNonNull(mapperId, "mapperId");
      Objects.requireNonNull(integrationId, "integrationId");
    }
  }

  public record LifecycleUpdate(UUID mapperId, boolean archived) {
    public LifecycleUpdate {
      Objects.requireNonNull(mapperId, "mapperId");
    }
  }

  public record Rejection(
      UUID mapperId, String mapperName, RejectionReason reason, UUID ownerMapperId) {
    public Rejection {
      Objects.requireNonNull(mapperId, "mapperId");
      Objects.requireNonNull(mapperName, "mapperName");
      Objects.requireNonNull(reason, "reason");
    }
  }

  public enum RejectionReason {
    EDITABLE_UUID_OWNER("editable-uuid-owner"),
    DATA_AUTHORED_UUID_OWNER("data-authored-uuid-owner"),
    CATALOG_UUID_OWNER("catalog-uuid-owner"),
    QUALIFIER_UUID_OWNER("qualifier-uuid-owner"),
    PERSISTED_NAME_OWNER("persisted-name-owner"),
    DUPLICATE_UNOWNED_NAME("duplicate-unowned-name");

    private final String code;

    RejectionReason(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }
  }
}

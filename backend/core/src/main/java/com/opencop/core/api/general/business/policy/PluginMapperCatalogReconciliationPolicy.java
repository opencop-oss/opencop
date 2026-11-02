// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.general.business.model.profile.MapperCatalogProfile;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.IntegrationAssignment;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.LifecycleUpdate;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.Rejection;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.RejectionReason;
import com.opencop.core.api.general.business.model.profile.PluginMapperContribution;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stateless ownership and metadata rules used by plugin mapper catalog reconciliation. */
public final class PluginMapperCatalogReconciliationPolicy {

  private PluginMapperCatalogReconciliationPolicy() {}

  public static Optional<Rejection> uuidCollision(
      PluginMapperContribution contribution, List<MapperCatalogProfile> persistedHistory) {
    if (persistedHistory.stream().anyMatch(mapper -> ownedByOtherDirection(mapper, contribution))) {
      return Optional.of(
          rejection(contribution, RejectionReason.QUALIFIER_UUID_OWNER, contribution.id()));
    }
    if (persistedHistory.stream().anyMatch(MapperCatalogProfile::editable)) {
      return Optional.of(
          rejection(contribution, RejectionReason.EDITABLE_UUID_OWNER, contribution.id()));
    }
    if (persistedHistory.stream().anyMatch(MapperCatalogProfile::mappingDefinitionPresent)) {
      return Optional.of(
          rejection(contribution, RejectionReason.DATA_AUTHORED_UUID_OWNER, contribution.id()));
    }
    if (persistedHistory.stream()
        .anyMatch(PluginMapperCatalogReconciliationPolicy::noIntegrationOwns)) {
      return Optional.of(
          rejection(contribution, RejectionReason.CATALOG_UUID_OWNER, contribution.id()));
    }
    return Optional.empty();
  }

  public static Optional<Rejection> nameCollision(
      PluginMapperContribution contribution,
      List<MapperCatalogProfile> persistedNameOwners,
      List<UUID> existingClaimantIds,
      long newClaimantCount) {
    var otherPersistedOwner =
        persistedNameOwners.stream()
            .map(MapperCatalogProfile::id)
            .filter(ownerId -> !ownerId.equals(contribution.id()))
            .min(UUID::compareTo);
    if (otherPersistedOwner.isPresent()) {
      return Optional.of(
          rejection(
              contribution,
              RejectionReason.PERSISTED_NAME_OWNER,
              otherPersistedOwner.orElseThrow()));
    }

    if (!persistedNameOwners.isEmpty()) {
      return Optional.empty();
    }

    var existingIdentity = existingClaimantIds.contains(contribution.id());
    if (existingIdentity && existingClaimantIds.size() == 1) {
      return Optional.empty();
    }

    var otherExistingClaimant =
        existingClaimantIds.stream()
            .filter(ownerId -> !ownerId.equals(contribution.id()))
            .min(UUID::compareTo);
    if (otherExistingClaimant.isPresent()) {
      return Optional.of(
          rejection(
              contribution,
              RejectionReason.DUPLICATE_UNOWNED_NAME,
              otherExistingClaimant.orElseThrow()));
    }
    if (newClaimantCount > 1) {
      return Optional.of(rejection(contribution, RejectionReason.DUPLICATE_UNOWNED_NAME, null));
    }
    return Optional.empty();
  }

  public static boolean metadataRequiresReconciliation(
      MapperCatalogProfile existing, PluginMapperContribution contribution) {
    return !Objects.equals(existing.name(), contribution.name())
        || !Objects.equals(existing.description(), contribution.description());
  }

  /**
   * The owning integration to record for an accepted contribution: always for a new entry, and for
   * an existing one only when another integration owns it.
   */
  public static Optional<IntegrationAssignment> integrationAssignment(
      Optional<MapperCatalogProfile> existing, PluginMapperContribution contribution) {
    if (existing.isPresent()
        && contribution.integrationId().equals(existing.orElseThrow().integrationId())) {
      return Optional.empty();
    }
    return Optional.of(new IntegrationAssignment(contribution.id(), contribution.integrationId()));
  }

  /**
   * Archives an integration's mapper while no accepted contribution backs it, and restores it when
   * one does. A mapper no integration owns keeps whatever state its users gave it.
   */
  public static Optional<LifecycleUpdate> lifecycleUpdate(
      MapperCatalogProfile existing, boolean contributionAccepted) {
    if (noIntegrationOwns(existing)) {
      return Optional.empty();
    }

    var desiredArchived = !contributionAccepted;
    if (existing.archived() == desiredArchived) {
      return Optional.empty();
    }
    return Optional.of(new LifecycleUpdate(existing.id(), desiredArchived));
  }

  /**
   * A mapper no integration contributes was authored by people or shipped as data, and its
   * identifier is never handed to an integration.
   */
  private static boolean noIntegrationOwns(MapperCatalogProfile mapper) {
    return mapper.integrationId() == null;
  }

  /** A mapper's identifier names one direction for good: an entry of the other keeps it. */
  private static boolean ownedByOtherDirection(
      MapperCatalogProfile mapper, PluginMapperContribution contribution) {
    return mapper.qualifier() != contribution.qualifier();
  }

  private static Rejection rejection(
      PluginMapperContribution contribution, RejectionReason reason, UUID ownerMapperId) {
    return new Rejection(contribution.id(), contribution.name(), reason, ownerMapperId);
  }
}

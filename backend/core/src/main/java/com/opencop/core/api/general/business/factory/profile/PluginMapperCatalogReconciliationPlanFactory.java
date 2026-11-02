// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.profile;

import com.opencop.core.api.general.business.model.profile.MapperCatalogProfile;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.IntegrationAssignment;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.LifecycleUpdate;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.MetadataUpdate;
import com.opencop.core.api.general.business.model.profile.PluginMapperCatalogReconciliationPlan.Rejection;
import com.opencop.core.api.general.business.model.profile.PluginMapperContribution;
import com.opencop.core.api.general.business.policy.PluginMapperCatalogReconciliationPolicy;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the complete mapper-catalog reconciliation plan from contribution and catalog facts. */
public final class PluginMapperCatalogReconciliationPlanFactory {

  private PluginMapperCatalogReconciliationPlanFactory() {}

  /**
   * Plans the catalog writes for the contributions of one direction. Every catalog entry takes part
   * in identifier and name collisions, but only entries of that direction are archived or restored:
   * the other direction's contributions are reconciled by their own plan.
   */
  public static PluginMapperCatalogReconciliationPlan create(
      MapperQualifier qualifier,
      List<PluginMapperContribution> contributions,
      List<MapperCatalogProfile> persistedCatalog) {
    requireQualifier(qualifier, contributions);
    var catalog = CatalogIndex.from(persistedCatalog);
    var uuidEligibility = resolveUuidCollisions(orderedCandidates(contributions), catalog);
    var nameEligibility = resolveNameCollisions(uuidEligibility.accepted(), catalog);
    var writes = planCatalogWrites(nameEligibility.accepted(), catalog);
    var lifecycleUpdates = planLifecycleUpdates(qualifier, nameEligibility.accepted(), catalog);

    var rejections = new ArrayList<>(uuidEligibility.rejections());
    rejections.addAll(nameEligibility.rejections());

    return new PluginMapperCatalogReconciliationPlan(
        writes.insertions(),
        writes.metadataUpdates(),
        writes.integrationAssignments(),
        lifecycleUpdates,
        rejections);
  }

  private static void requireQualifier(
      MapperQualifier qualifier, List<PluginMapperContribution> contributions) {
    if (contributions.stream().anyMatch(contribution -> contribution.qualifier() != qualifier)) {
      throw new IllegalArgumentException("Every contribution must be a " + qualifier + " mapper");
    }
  }

  private static List<PluginMapperContribution> orderedCandidates(
      List<PluginMapperContribution> contributions) {
    var candidates = new ArrayList<>(contributions);
    candidates.sort(Comparator.comparing(PluginMapperContribution::id));
    return List.copyOf(candidates);
  }

  private static Eligibility resolveUuidCollisions(
      List<PluginMapperContribution> candidates, CatalogIndex catalog) {
    var accepted = new ArrayList<PluginMapperContribution>();
    var rejections = new ArrayList<Rejection>();

    for (var contribution : candidates) {
      var collision =
          PluginMapperCatalogReconciliationPolicy.uuidCollision(
              contribution, catalog.historyById().getOrDefault(contribution.id(), List.of()));
      if (collision.isPresent()) {
        rejections.add(collision.orElseThrow());
      } else {
        accepted.add(contribution);
      }
    }

    return new Eligibility(accepted, rejections);
  }

  private static Eligibility resolveNameCollisions(
      List<PluginMapperContribution> candidates, CatalogIndex catalog) {
    Map<String, List<PluginMapperContribution>> candidatesByName =
        candidates.stream().collect(Collectors.groupingBy(PluginMapperContribution::name));
    var accepted = new ArrayList<PluginMapperContribution>();
    var rejections = new ArrayList<Rejection>();

    for (var contribution : candidates) {
      var sameNameCandidates = candidatesByName.get(contribution.name());
      var existingClaimantIds = existingClaimantIds(sameNameCandidates, catalog);
      var newClaimantCount = sameNameCandidates.size() - existingClaimantIds.size();
      var collision =
          PluginMapperCatalogReconciliationPolicy.nameCollision(
              contribution,
              catalog.byName().getOrDefault(contribution.name(), List.of()),
              existingClaimantIds,
              newClaimantCount);
      if (collision.isPresent()) {
        rejections.add(collision.orElseThrow());
      } else {
        accepted.add(contribution);
      }
    }

    return new Eligibility(accepted, rejections);
  }

  private static List<UUID> existingClaimantIds(
      List<PluginMapperContribution> sameNameCandidates, CatalogIndex catalog) {
    return sameNameCandidates.stream()
        .map(PluginMapperContribution::id)
        .filter(catalog.headById()::containsKey)
        .toList();
  }

  private static CatalogWrites planCatalogWrites(
      List<PluginMapperContribution> accepted, CatalogIndex catalog) {
    var insertions = new ArrayList<MapperProfile>();
    var metadataUpdates = new ArrayList<MetadataUpdate>();
    var integrationAssignments = new ArrayList<IntegrationAssignment>();

    for (var contribution : accepted) {
      var existing = catalog.headById().get(contribution.id());
      PluginMapperCatalogReconciliationPolicy.integrationAssignment(
              Optional.ofNullable(existing), contribution)
          .ifPresent(integrationAssignments::add);
      if (existing == null) {
        insertions.add(PluginMapperProfileFactory.create(contribution));
        continue;
      }
      if (PluginMapperCatalogReconciliationPolicy.metadataRequiresReconciliation(
          existing, contribution)) {
        metadataUpdates.add(
            new MetadataUpdate(contribution.id(), contribution.name(), contribution.description()));
      }
    }

    return new CatalogWrites(insertions, metadataUpdates, integrationAssignments);
  }

  private static List<LifecycleUpdate> planLifecycleUpdates(
      MapperQualifier qualifier, List<PluginMapperContribution> accepted, CatalogIndex catalog) {
    Set<UUID> acceptedIds =
        accepted.stream().map(PluginMapperContribution::id).collect(Collectors.toSet());

    return catalog.headById().values().stream()
        .filter(existing -> existing.qualifier() == qualifier)
        .sorted(Comparator.comparing(MapperCatalogProfile::id))
        .map(
            existing ->
                PluginMapperCatalogReconciliationPolicy.lifecycleUpdate(
                    existing, acceptedIds.contains(existing.id())))
        .flatMap(Optional::stream)
        .toList();
  }

  private static MapperCatalogProfile latestVersion(
      MapperCatalogProfile left, MapperCatalogProfile right) {
    return left.version() >= right.version() ? left : right;
  }

  private record CatalogIndex(
      Map<UUID, List<MapperCatalogProfile>> historyById,
      Map<UUID, MapperCatalogProfile> headById,
      Map<String, List<MapperCatalogProfile>> byName) {

    private static CatalogIndex from(List<MapperCatalogProfile> persistedCatalog) {
      Objects.requireNonNull(persistedCatalog, "persistedCatalog");
      var historyById =
          persistedCatalog.stream().collect(Collectors.groupingBy(MapperCatalogProfile::id));
      var headById =
          persistedCatalog.stream()
              .collect(
                  Collectors.toMap(
                      MapperCatalogProfile::id,
                      Function.identity(),
                      PluginMapperCatalogReconciliationPlanFactory::latestVersion));
      var byName =
          persistedCatalog.stream().collect(Collectors.groupingBy(MapperCatalogProfile::name));
      return new CatalogIndex(historyById, headById, byName);
    }
  }

  private record Eligibility(List<PluginMapperContribution> accepted, List<Rejection> rejections) {

    private Eligibility {
      accepted = List.copyOf(accepted);
      rejections = List.copyOf(rejections);
    }
  }

  private record CatalogWrites(
      List<MapperProfile> insertions,
      List<MetadataUpdate> metadataUpdates,
      List<IntegrationAssignment> integrationAssignments) {

    private CatalogWrites {
      insertions = List.copyOf(insertions);
      metadataUpdates = List.copyOf(metadataUpdates);
      integrationAssignments = List.copyOf(integrationAssignments);
    }
  }
}

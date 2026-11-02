// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_NO_ELIGIBLE_SOURCES;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_SOURCE_ELIGIBILITY_FAILED;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxMapperIdentity;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.policy.integration.mapping.ingress.IngressMapperAvailabilityPolicy;
import com.opencop.core.sdk.business.policy.integration.mapping.ingress.IngressMapperCatalogOwnershipPolicy;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.source.ConstSource;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public class WatchboxSourceEligibilityService {

  /**
   * Watchbox transition state depends on stable object IDs across events.
   *
   * <p>Mapper ownership is not the same as mapper execution. A shipped mapper can have {@code
   * editable=false} and still be backed by JSON mapping rules. Rule-backed mapper profiles, shipped
   * or user-editable, are inspected structurally for a required non-constant {@code OBJECT_ID}
   * mapping. Java-backed ingress mapper profiles have no rules to inspect, so they must declare
   * stable identity through the ingress mapper trait behind {@link WatchboxMapperIdentity}.
   *
   * <p>The Java-backed path is fail-closed: future shipped or plugin ingress mappers are
   * Watchbox-ineligible until they explicitly declare stable per-entity object IDs.
   */
  private final ServerLayerRepository serverLayerRepository;

  private final DataSourceRepository dataSourceRepository;
  private final MapperProfileService mapperProfileService;
  private final WatchboxMapperIdentity mapperIdentity;

  public WatchboxSourceEligibilityService(
      ServerLayerRepository serverLayerRepository,
      DataSourceRepository dataSourceRepository,
      MapperProfileService mapperProfileService,
      WatchboxMapperIdentity mapperIdentity) {
    this.serverLayerRepository = serverLayerRepository;
    this.dataSourceRepository = dataSourceRepository;
    this.mapperProfileService = mapperProfileService;
    this.mapperIdentity = mapperIdentity;
  }

  public Set<UUID> expandSourceIds(Set<UUID> layerIds) {
    return layerIds.stream()
        .flatMap(layerId -> serverLayerRepository.getLayerServers(layerId).stream())
        .map(SourceLayer::getServerId)
        .collect(Collectors.toSet());
  }

  public void assertEligibleForEnabledWatchbox(Set<UUID> layerIds) {
    // A source that is archived, or whose row no longer exists, is excluded from admission: archive
    // is a soft delete that keeps the layer association on purpose and, once converged, never
    // resumes ingestion. Exclusion here does not fence events already queued for that source or
    // clear retained watchbox status. A stopped source can be started again without re-admission,
    // so its mapper is still inspected below.
    List<DataSource> admissibleSources =
        expandSourceIds(layerIds).stream()
            .map(dataSourceRepository::findById)
            .flatMap(Optional::stream)
            .filter(source -> source.archivedAt() == null)
            .toList();
    if (admissibleSources.isEmpty()) {
      throw new WatchboxException(
          WATCHBOX_NO_ELIGIBLE_SOURCES,
          "Selected layers do not contain eligible sources",
          HttpStatus.CONFLICT,
          List.of("layerIds"));
    }

    var writeOnlySourceNames =
        admissibleSources.stream()
            .sorted(Comparator.comparing(DataSource::name).thenComparing(DataSource::id))
            .filter(WatchboxSourceEligibilityService::onlyWrites)
            .map(DataSource::name)
            .distinct()
            .toList();
    if (!writeOnlySourceNames.isEmpty()) {
      throw new WatchboxException(
          WATCHBOX_SOURCE_ELIGIBILITY_FAILED,
          "Selected sources only write, so they receive nothing to watch",
          HttpStatus.CONFLICT,
          List.of("layerIds"),
          Map.of("sourceNames", writeOnlySourceNames));
    }

    var ineligibleSources =
        admissibleSources.stream()
            .sorted(Comparator.comparing(DataSource::name).thenComparing(DataSource::id))
            .map(source -> new SourceMapper(source, latestIngressMapper(source)))
            .filter(candidate -> !isMapperEligible(candidate.mapper()))
            .toList();
    if (!ineligibleSources.isEmpty()) {
      var ineligibleSourceNames =
          ineligibleSources.stream()
              .map(candidate -> candidate.source().name())
              .distinct()
              .toList();
      // The specific code is only truthful when every listed source fails for that one reason.
      boolean onlyOnMissing =
          ineligibleSources.stream().allMatch(candidate -> failsOnlyOnMissing(candidate.mapper()));
      throw new WatchboxException(
          onlyOnMissing
              ? WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR
              : WATCHBOX_SOURCE_ELIGIBILITY_FAILED,
          onlyOnMissing
              ? "Selected sources' object ID rule must use 'Error' for On Missing"
              : "Selected sources require object ID mapping correction",
          HttpStatus.CONFLICT,
          List.of("layerIds"),
          Map.of("sourceNames", ineligibleSourceNames));
    }
  }

  /**
   * A write-only source has no ingress mapper: it decodes nothing, so nothing reaches a watchbox.
   */
  private static boolean onlyWrites(DataSource source) {
    return !source.access().canRead();
  }

  /**
   * Watchbox admission is low-volume control-plane work and must observe the latest revision of the
   * source's own ingress mapper. The cached source-to-mapper lookup exists for per-event decoding
   * throughput and must not be used for this correctness check.
   */
  private MapperProfile latestIngressMapper(DataSource source) {
    var latest = mapperProfileService.getLatestMapperProfileFull(source.ingressMapperId());
    return latest == null ? null : latest.profile();
  }

  private boolean isMapperEligible(MapperProfile mapper) {
    return isMapperUsable(mapper) && isEligible(mapper);
  }

  /**
   * True when an editable, usable dynamic mapper fails eligibility solely because its effective
   * OBJECT_ID rule's On Missing is not Error. A shipped mapper's rules are not the operator's to
   * fix, so it never qualifies.
   */
  private static boolean failsOnlyOnMissing(MapperProfile mapper) {
    return isMapperUsable(mapper)
        && mapper.dynamicMapper() != null
        && !Boolean.FALSE.equals(mapper.editable())
        && objectIdRuleProblem(mapper) == ObjectIdRuleProblem.OBJECT_ID_ON_MISSING_NOT_ERROR;
  }

  private static boolean isMapperUsable(MapperProfile mapper) {
    if (mapper == null || !IngressMapperAvailabilityPolicy.isAvailable(mapper)) {
      return false;
    }
    return mapper.dynamicMapper() == null || mapper.dynamicMapper().enabled();
  }

  private boolean isEligible(MapperProfile mapper) {
    if (mapper == null) {
      return false;
    }
    if (mapper.dynamicMapper() != null) {
      return objectIdRuleProblem(mapper) == null;
    }
    return IngressMapperCatalogOwnershipPolicy.canUseContribution(mapper)
        && mapperIdentity.hasStableObjectId(mapper.id());
  }

  /** A source paired with its latest ingress mapper, which is null when none resolves. */
  private record SourceMapper(DataSource source, MapperProfile mapper) {}

  private enum ObjectIdRuleProblem {
    /** No enabled rule targets OBJECT_ID, or its source is missing or a constant value. */
    NO_OBJECT_ID_RULE,
    /** A real OBJECT_ID rule exists, but its On Missing behavior isn't Error. */
    OBJECT_ID_ON_MISSING_NOT_ERROR
  }

  /** Why a dynamic mapper's OBJECT_ID rule fails eligibility, or null if it's fine. */
  private static ObjectIdRuleProblem objectIdRuleProblem(MapperProfile mapper) {
    // MappingSession executes enabled rules in stored order and later writes to the same target
    // field replace earlier ones, so the last enabled OBJECT_ID rule is the runtime-effective
    // writer. Disabled rules never execute and must not shadow it.
    List<MappingRule> rules = mapper.dynamicMapper().rules();
    for (int i = rules.size() - 1; i >= 0; i--) {
      MappingRule rule = rules.get(i);
      if (!rule.enabled()
          || rule.target() == null
          || rule.target().field() != TargetField.OBJECT_ID) {
        continue;
      }
      if (rule.source() == null || rule.source() instanceof ConstSource) {
        return ObjectIdRuleProblem.NO_OBJECT_ID_RULE;
      }
      return rule.onMissing() == OnMissing.ERROR
          ? null
          : ObjectIdRuleProblem.OBJECT_ID_ON_MISSING_NOT_ERROR;
    }
    return ObjectIdRuleProblem.NO_OBJECT_ID_RULE;
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_NO_ELIGIBLE_SOURCES;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_SOURCE_ELIGIBILITY_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxMapperIdentity;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.integration.builtin.technical.mapping.ingress.GeoJsonIngressMapperV1;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappedTargetField;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.source.ConstSource;
import com.opencop.core.sdk.model.mapper.source.JsonPathSource;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchboxSourceEligibilityServiceTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000aa");

  @Mock ServerLayerRepository serverLayerRepository;
  @Mock DataSourceRepository dataSourceRepository;
  @Mock MapperProfileService mapperProfileService;
  @Mock WatchboxMapperIdentity mapperIdentity;

  private UUID connectionMapperId;

  @Test
  void acceptsDecoderBackedMapperWithStableTrait() {
    var mapperId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperIdentity.hasStableObjectId(mapperId)).thenReturn(true);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(pluginMapper(mapperId, "Stable-ID mapper")));

    service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));
  }

  @Test
  void rejectsArchivedPluginMapperAsUnavailable() {
    var mapperId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(
            latestRevision(
                MapperProfile.builder()
                    .withId(mapperId)
                    .withName("Archived plugin")
                    .withEditable(false)
                    .withIntegrationId(INTEGRATION_ID)
                    .withVersion(1)
                    .withArchived(true)
                    .build()));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
              assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
              assertThat(ex).hasMessage("Selected sources require object ID mapping correction");
              assertThat(ex.fields()).containsExactly("layerIds");
            });

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsArchivedRuleBackedMapperAsUnavailable() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(
            latestRevision(
                mapperWithRules(true, safeObjectIdRule()).toBuilder().withArchived(true).build()));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
              assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
              assertThat(ex).hasMessage("Selected sources require object ID mapping correction");
            });

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsTraitFromAContributionCollidingWithAUserOwnedMapper() {
    var mapperId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(
            latestRevision(
                MapperProfile.builder()
                    .withId(mapperId)
                    .withName("User mapper")
                    .withEditable(true)
                    .withVersion(1)
                    .withArchived(false)
                    .build()));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsDecoderBackedSystemMapperWithoutStableTrait() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(
            latestRevision(
                MapperProfile.builder()
                    .withId(GeoJsonIngressMapperV1.MAPPER_ID)
                    .withName("GeoJSON")
                    .withDescription(null)
                    .withEditable(false)
                    .withVersion(1)
                    .withArchived(false)
                    .build()));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));
  }

  @Test
  void rejectsDecoderBackedUnknownUserMapperIdWithMapperError() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(
            latestRevision(
                MapperProfile.builder()
                    .withId(UUID.randomUUID())
                    .withName("plugin")
                    .withDescription(null)
                    .withEditable(true)
                    .withVersion(1)
                    .withArchived(false)
                    .build()));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));
  }

  @Test
  void acceptsJsonRuleBackedSystemMapperWithSafeObjectIdRule() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(mapperWithRules(false, safeObjectIdRule())));

    service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void acceptsJsonRuleBackedUserMapperWithSafeObjectIdRule() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));

    service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsJsonRuleBackedSystemMapperWhenProfileDisabled() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(mapperWithRules(false, false, safeObjectIdRule())));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsJsonRuleBackedUserMapperWhenProfileDisabled() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, false, safeObjectIdRule())));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsJsonRuleBackedSystemMapperWithoutObjectIdRule() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(mapperWithRules(false, displayNameRule())));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsJsonRuleBackedMapperWithConstantObjectIdRule() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, objectIdRule(new ConstSource("fixed")))));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsJsonRuleBackedUserMapperWhoseObjectIdRuleDoesNotErrorOnMissing() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, objectIdRuleOnMissing(OnMissing.NULL))));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR);
              assertThat(ex.context()).containsEntry("sourceNames", List.of("source"));
            });

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsJsonRuleBackedSystemMapperWithBadOnMissingAsGenericEligibilityFailure() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(latestRevision(mapperWithRules(false, objectIdRuleOnMissing(OnMissing.NULL))));

    // A shipped mapper's rules are not the operator's to fix, so the actionable
    // On Missing code is reserved for editable mappers.
    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));
  }

  @Test
  void keepsGenericEligibilityFailureWhenOnMissingIsNotTheOnlyCause() {
    var onMissingSourceId = UUID.randomUUID();
    var onMissingMapperId = UUID.randomUUID();
    var missingRuleSourceId = UUID.randomUUID();
    var missingRuleMapperId = UUID.randomUUID();
    stubLayerSources(onMissingSourceId, missingRuleSourceId);
    when(dataSourceRepository.findById(onMissingSourceId))
        .thenReturn(
            Optional.of(source(onMissingSourceId, "on-missing", onMissingMapperId, true, null)));
    when(dataSourceRepository.findById(missingRuleSourceId))
        .thenReturn(
            Optional.of(
                source(missingRuleSourceId, "missing-rule", missingRuleMapperId, true, null)));
    when(mapperProfileService.getLatestMapperProfileFull(onMissingMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, objectIdRuleOnMissing(OnMissing.NULL))));
    when(mapperProfileService.getLatestMapperProfileFull(missingRuleMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, displayNameRule())));

    assertThatThrownBy(
            () -> newService().assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
              assertThat(ex.context())
                  .containsEntry("sourceNames", List.of("missing-rule", "on-missing"));
            });
  }

  @Test
  void rejectsWhenSafeWriterIsSupersededByTrailingEnabledConstantWriter() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(
            latestRevision(
                mapperWithRules(true, safeObjectIdRule(), objectIdRule(new ConstSource("fixed")))));

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));
  }

  @Test
  void acceptsWhenConstantWriterIsSupersededByTrailingSafeWriter() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(
            latestRevision(
                mapperWithRules(true, objectIdRule(new ConstSource("fixed")), safeObjectIdRule())));

    service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));
  }

  @Test
  void acceptsWhenSafeWriterIsFollowedByDisabledConstantWriter() {
    var sourceId = UUID.randomUUID();
    var service = serviceWithSource(sourceId);
    var disabledConstantRule =
        new MappingRule(
            UUID.randomUUID(),
            false,
            new MappedTargetField(TargetField.OBJECT_ID.name()),
            null,
            new ConstSource("fixed"),
            List.of(),
            OnMissing.ERROR,
            null);
    when(mapperProfileService.getLatestMapperProfileFull(connectionMapperId))
        .thenReturn(
            latestRevision(mapperWithRules(true, safeObjectIdRule(), disabledConstantRule)));

    service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));
  }

  @Test
  void rejectsEmptySourceExpansion() {
    UUID layerId = UUID.randomUUID();
    when(serverLayerRepository.getLayerServers(layerId)).thenReturn(Set.of());
    var service =
        new WatchboxSourceEligibilityService(
            serverLayerRepository, dataSourceRepository, mapperProfileService, mapperIdentity);

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(layerId)))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_NO_ELIGIBLE_SOURCES));
  }

  @Test
  void skipsArchivedSourceAndAdmitsTheRemainingLiveSource() {
    var archivedSourceId = UUID.randomUUID();
    var archivedMapperId = UUID.randomUUID();
    var liveSourceId = UUID.randomUUID();
    var liveMapperId = UUID.randomUUID();
    stubLayerSources(archivedSourceId, liveSourceId);
    when(dataSourceRepository.findById(archivedSourceId))
        .thenReturn(Optional.of(source(archivedSourceId, archivedMapperId, false, Instant.now())));
    when(dataSourceRepository.findById(liveSourceId))
        .thenReturn(Optional.of(source(liveSourceId, liveMapperId, true, null)));
    // An archived source is not part of the layer for admission, so even a mapper that would be
    // rejected on its own must never be consulted.
    lenient()
        .when(mapperProfileService.getLatestMapperProfileFull(archivedMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, displayNameRule())));
    when(mapperProfileService.getLatestMapperProfileFull(liveMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));

    newService().assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));

    verify(mapperProfileService, never()).getLatestMapperProfileFull(archivedMapperId);
    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void skipsSourceWithNoRowAndAdmitsTheRemainingLiveSource() {
    var vanishedSourceId = UUID.randomUUID();
    var liveSourceId = UUID.randomUUID();
    var liveMapperId = UUID.randomUUID();
    stubLayerSources(vanishedSourceId, liveSourceId);
    when(dataSourceRepository.findById(vanishedSourceId)).thenReturn(Optional.empty());
    when(dataSourceRepository.findById(liveSourceId))
        .thenReturn(Optional.of(source(liveSourceId, liveMapperId, true, null)));
    when(mapperProfileService.getLatestMapperProfileFull(liveMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));

    newService().assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsLayerWhoseSourcesAreAllArchived() {
    var firstSourceId = UUID.randomUUID();
    var secondSourceId = UUID.randomUUID();
    stubLayerSources(firstSourceId, secondSourceId);
    when(dataSourceRepository.findById(firstSourceId))
        .thenReturn(Optional.of(source(firstSourceId, UUID.randomUUID(), false, Instant.now())));
    when(dataSourceRepository.findById(secondSourceId))
        .thenReturn(Optional.of(source(secondSourceId, UUID.randomUUID(), true, Instant.now())));
    var service = newService();

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WATCHBOX_NO_ELIGIBLE_SOURCES);
              assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
              assertThat(ex).hasMessage("Selected layers do not contain eligible sources");
              assertThat(ex.fields()).containsExactly("layerIds");
            });

    verifyNoInteractions(mapperProfileService, mapperIdentity);
  }

  @Test
  void rejectsStoppedSourceOnTheMapperRuleRatherThanIdentityQuality() {
    var stoppedSourceId = UUID.randomUUID();
    var stoppedMapperId = UUID.randomUUID();
    stubLayerSources(stoppedSourceId);
    when(dataSourceRepository.findById(stoppedSourceId))
        .thenReturn(Optional.of(source(stoppedSourceId, stoppedMapperId, false, null)));
    when(mapperProfileService.getLatestMapperProfileFull(stoppedMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, displayNameRule())));
    var service = newService();

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
              assertThat(ex).hasMessage("Selected sources require object ID mapping correction");
            });

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void admitsStoppedSourceWhoseMapperKeepsAStableObjectId() {
    var stoppedSourceId = UUID.randomUUID();
    var stoppedMapperId = UUID.randomUUID();
    stubLayerSources(stoppedSourceId);
    when(dataSourceRepository.findById(stoppedSourceId))
        .thenReturn(Optional.of(source(stoppedSourceId, stoppedMapperId, false, null)));
    when(mapperProfileService.getLatestMapperProfileFull(stoppedMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));

    newService().assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsLayerWhoseOnlySourceRowIsMissing() {
    var vanishedSourceId = UUID.randomUUID();
    stubLayerSources(vanishedSourceId);
    when(dataSourceRepository.findById(vanishedSourceId)).thenReturn(Optional.empty());
    var service = newService();

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_NO_ELIGIBLE_SOURCES));

    verifyNoInteractions(mapperProfileService, mapperIdentity);
  }

  @Test
  void rejectsStoppedSourceWithoutStableObjectIdEvenBesideLiveEligibleSource() {
    var stoppedSourceId = UUID.randomUUID();
    var stoppedMapperId = UUID.randomUUID();
    var liveSourceId = UUID.randomUUID();
    var liveMapperId = UUID.randomUUID();
    stubLayerSources(stoppedSourceId, liveSourceId);
    when(dataSourceRepository.findById(stoppedSourceId))
        .thenReturn(Optional.of(source(stoppedSourceId, stoppedMapperId, false, null)));
    when(dataSourceRepository.findById(liveSourceId))
        .thenReturn(Optional.of(source(liveSourceId, liveMapperId, true, null)));
    when(mapperProfileService.getLatestMapperProfileFull(stoppedMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, displayNameRule())));
    // Evaluation order over the expanded set is unspecified, so the live source's profile may or
    // may not be consulted before the stopped one is rejected.
    lenient()
        .when(mapperProfileService.getLatestMapperProfileFull(liveMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));
    var service = newService();

    assertThatThrownBy(() -> service.assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED));
  }

  @Test
  void aggregates_different_failures_and_evaluates_eligible_source() {
    var unavailableSourceId = UUID.randomUUID();
    var unavailableMapperId = UUID.randomUUID();
    var missingRuleSourceId = UUID.randomUUID();
    var missingRuleMapperId = UUID.randomUUID();
    var eligibleSourceId = UUID.randomUUID();
    var eligibleMapperId = UUID.randomUUID();
    stubLayerSources(unavailableSourceId, missingRuleSourceId, eligibleSourceId);
    when(dataSourceRepository.findById(unavailableSourceId))
        .thenReturn(
            Optional.of(
                source(
                    unavailableSourceId, "admission-source-a", unavailableMapperId, true, null)));
    when(dataSourceRepository.findById(missingRuleSourceId))
        .thenReturn(
            Optional.of(
                source(
                    missingRuleSourceId, "admission-source-b", missingRuleMapperId, true, null)));
    when(dataSourceRepository.findById(eligibleSourceId))
        .thenReturn(
            Optional.of(
                source(eligibleSourceId, "admission-source-c", eligibleMapperId, true, null)));
    when(mapperProfileService.getLatestMapperProfileFull(unavailableMapperId))
        .thenReturn(
            latestRevision(
                mapperWithRules(true, safeObjectIdRule()).toBuilder().withArchived(true).build()));
    when(mapperProfileService.getLatestMapperProfileFull(missingRuleMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, displayNameRule())));
    when(mapperProfileService.getLatestMapperProfileFull(eligibleMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));

    assertThatThrownBy(
            () -> newService().assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
              assertThat(ex).hasMessage("Selected sources require object ID mapping correction");
              assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
              assertThat(ex.fields()).containsExactly("layerIds");
              assertThat(ex.reasons()).isEmpty();
              assertThat(ex.context())
                  .containsEntry(
                      "sourceNames", List.of("admission-source-a", "admission-source-b"));
            });

    verify(mapperProfileService).getLatestMapperProfileFull(unavailableMapperId);
    verify(mapperProfileService).getLatestMapperProfileFull(missingRuleMapperId);
    verify(mapperProfileService).getLatestMapperProfileFull(eligibleMapperId);
  }

  @Test
  void admitsLayerWhoseOnlySourcesAreStoppedWithStableObjectIds() {
    var firstSourceId = UUID.randomUUID();
    var firstMapperId = UUID.randomUUID();
    var secondSourceId = UUID.randomUUID();
    var secondMapperId = UUID.randomUUID();
    stubLayerSources(firstSourceId, secondSourceId);
    when(dataSourceRepository.findById(firstSourceId))
        .thenReturn(Optional.of(source(firstSourceId, firstMapperId, false, null)));
    when(dataSourceRepository.findById(secondSourceId))
        .thenReturn(Optional.of(source(secondSourceId, secondMapperId, false, null)));
    when(mapperProfileService.getLatestMapperProfileFull(firstMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));
    when(mapperProfileService.getLatestMapperProfileFull(secondMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));

    newService().assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));

    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void skipsArchivedSourceBesideStoppedSourceWithStableObjectId() {
    var archivedSourceId = UUID.randomUUID();
    var archivedMapperId = UUID.randomUUID();
    var stoppedSourceId = UUID.randomUUID();
    var stoppedMapperId = UUID.randomUUID();
    stubLayerSources(archivedSourceId, stoppedSourceId);
    when(dataSourceRepository.findById(archivedSourceId))
        .thenReturn(Optional.of(source(archivedSourceId, archivedMapperId, false, Instant.now())));
    when(dataSourceRepository.findById(stoppedSourceId))
        .thenReturn(Optional.of(source(stoppedSourceId, stoppedMapperId, false, null)));
    lenient()
        .when(mapperProfileService.getLatestMapperProfileFull(archivedMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, displayNameRule())));
    when(mapperProfileService.getLatestMapperProfileFull(stoppedMapperId))
        .thenReturn(latestRevision(mapperWithRules(true, safeObjectIdRule())));

    newService().assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID()));

    verify(mapperProfileService, never()).getLatestMapperProfileFull(archivedMapperId);
    verifyNoInteractions(mapperIdentity);
  }

  @Test
  void rejectsAWriteOnlySourceWithoutInspectingAMapper() {
    var sourceId = UUID.randomUUID();
    stubLayerSources(sourceId);
    var writeOnly =
        new SocketDataSource(
            sourceId,
            "Socket writer",
            "tenant",
            new ConnectionSpec(
                new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
            null,
            UUID.randomUUID(),
            null,
            AccessMode.WRITE,
            true,
            null,
            null,
            null,
            Instant.now(),
            null,
            Instant.now(),
            null,
            null,
            true);
    when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(writeOnly));

    assertThatThrownBy(
            () -> newService().assertEligibleForEnabledWatchbox(Set.of(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
              assertThat(ex.getMessage()).contains("only write");
            });
    verifyNoInteractions(mapperProfileService, mapperIdentity);
  }

  private void stubLayerSources(UUID... sourceIds) {
    var layerSources = new LinkedHashSet<SourceLayer>();
    for (UUID sourceId : sourceIds) {
      layerSources.add(new SourceLayer.Builder().withServerId(sourceId).build());
    }
    when(serverLayerRepository.getLayerServers(any(UUID.class))).thenReturn(layerSources);
  }

  private WatchboxSourceEligibilityService newService() {
    return new WatchboxSourceEligibilityService(
        serverLayerRepository, dataSourceRepository, mapperProfileService, mapperIdentity);
  }

  private WatchboxSourceEligibilityService serviceWithSource(UUID sourceId) {
    connectionMapperId = UUID.randomUUID();
    when(serverLayerRepository.getLayerServers(any(UUID.class)))
        .thenReturn(Set.of(new SourceLayer.Builder().withServerId(sourceId).build()));
    when(dataSourceRepository.findById(sourceId))
        .thenReturn(Optional.of(source(sourceId, connectionMapperId)));
    return new WatchboxSourceEligibilityService(
        serverLayerRepository, dataSourceRepository, mapperProfileService, mapperIdentity);
  }

  private static MapperProfileFullMeta latestRevision(MapperProfile mapper) {
    return new MapperProfileFullMeta(mapper, null, null);
  }

  private static MapperProfile pluginMapper(UUID mapperId, String name) {
    return MapperProfile.builder()
        .withId(mapperId)
        .withName(name)
        .withEditable(false)
        .withIntegrationId(INTEGRATION_ID)
        .withVersion(1)
        .withArchived(false)
        .build();
  }

  private static MapperProfile mapperWithRules(boolean editable, MappingRule... rules) {
    return mapperWithRules(editable, true, rules);
  }

  private static MapperProfile mapperWithRules(
      boolean editable, boolean mapperEnabled, MappingRule... rules) {
    return MapperProfile.builder()
        .withId(UUID.randomUUID())
        .withName("json-rule-mapper")
        .withDescription(null)
        .withEditable(editable)
        .withVersion(1)
        .withDynamicMapper(
            new DynamicMapper(UUID.randomUUID(), mapperEnabled, null, null, List.of(rules)))
        .withArchived(false)
        .build();
  }

  private static MappingRule safeObjectIdRule() {
    return objectIdRule(new JsonPathSource("$.id"));
  }

  private static MappingRule objectIdRule(
      com.opencop.core.sdk.model.mapper.source.SourceRef source) {
    return rule(TargetField.OBJECT_ID, source, OnMissing.ERROR);
  }

  private static MappingRule objectIdRuleOnMissing(OnMissing onMissing) {
    return rule(TargetField.OBJECT_ID, new JsonPathSource("$.id"), onMissing);
  }

  private static MappingRule displayNameRule() {
    return rule(TargetField.DISPLAY_NAME, new JsonPathSource("$.name"), OnMissing.NULL);
  }

  private static MappingRule rule(
      TargetField targetField,
      com.opencop.core.sdk.model.mapper.source.SourceRef source,
      OnMissing onMissing) {
    return new MappingRule(
        UUID.randomUUID(),
        true,
        new MappedTargetField(targetField.name()),
        null,
        source,
        List.of(),
        onMissing,
        null);
  }

  /** Same shape as {@link #source(UUID, UUID)} with the two lifecycle flags under test exposed. */
  private static SocketDataSource source(
      UUID sourceId, UUID connectionMapperId, boolean enabled, Instant archivedAt) {
    return source(sourceId, "source", connectionMapperId, enabled, archivedAt);
  }

  private static SocketDataSource source(
      UUID sourceId, String name, UUID connectionMapperId, boolean enabled, Instant archivedAt) {
    return new SocketDataSource(
        sourceId,
        name,
        "tenant",
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
        connectionMapperId,
        null,
        null,
        AccessMode.READ,
        enabled,
        null,
        archivedAt,
        archivedAt == null ? null : UUID.randomUUID(),
        Instant.now(),
        null,
        Instant.now(),
        null,
        null,
        true);
  }

  private static SocketDataSource source(UUID sourceId, UUID connectionMapperId) {
    return source(sourceId, "source", connectionMapperId, true, null);
  }
}

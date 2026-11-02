// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_BUSY;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_CLASS_CONFLICT;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_INVALID_NAME;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_INVALID_USER_GROUP_TARGETS;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_REQUIRED_ROW_MISSING;
import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_RESERVED_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureAudiencePolicy;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureNamePolicy;
import com.opencop.core.api.general.business.usecase.runtimefeature.CheckRuntimeFeatureAvailabilityUsecase;
import com.opencop.core.api.general.business.usecase.runtimefeature.GetRuntimeFeatureMetadataUsecase;
import com.opencop.core.api.general.business.usecase.runtimefeature.ManageRuntimeFeaturesUsecase;
import com.opencop.core.api.general.ports.gateway.runtimefeature.RuntimeFeatureUserGroupAccess;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetGateway;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetTelemetry;
import com.opencop.core.sdk.model.runtimefeature.BackendRuntimeFeature;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureConsumerClass;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureRegistrationResult;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureWriteResult;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureBusyException;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureMutationGateway;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureStateReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeFeatureUsecasesTest {

  private static final RuntimeFeatureNamePolicy NAME_POLICY = new RuntimeFeatureNamePolicy();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID GROUP_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID GROUP_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Test
  void audiencePolicyEvaluatesEveryMode() {
    var access = mock(RuntimeFeatureUserGroupAccess.class);
    var policy = new RuntimeFeatureAudiencePolicy(access);
    var groups = new RuntimeFeatureAudience.UserGroups(new TreeSet<>(Set.of(GROUP_A, GROUP_B)));
    when(access.hasMembership(USER_ID, groups.userGroupIds())).thenReturn(true);

    assertThat(policy.isAvailable(new RuntimeFeatureAudience.None(), USER_ID)).isFalse();
    assertThat(policy.isAvailable(new RuntimeFeatureAudience.All(), USER_ID)).isTrue();
    assertThat(policy.isAvailable(groups, USER_ID)).isTrue();
    assertThat(policy.isAvailable(groups, UUID.randomUUID())).isFalse();
    assertThat(policy.isAvailable(groups, null)).isFalse();
  }

  @Test
  void audiencePolicyValidatesOnlyUserGroupTargets() {
    var access = mock(RuntimeFeatureUserGroupAccess.class);
    var policy = new RuntimeFeatureAudiencePolicy(access);

    policy.assertValidTargets(new RuntimeFeatureAudience.None());
    policy.assertValidTargets(new RuntimeFeatureAudience.All());
    verifyNoInteractions(access);

    var groups = new RuntimeFeatureAudience.UserGroups(new TreeSet<>(Set.of(GROUP_A, GROUP_B)));
    when(access.stabilizeAndFindMissingTargets(groups.userGroupIds())).thenReturn(Set.of());
    policy.assertValidTargets(groups);
    verify(access).stabilizeAndFindMissingTargets(groups.userGroupIds());
  }

  @Test
  void metadataIsOrderedAndAdminDoesNotBypassAudience() {
    var reader = mock(RuntimeFeatureStateReader.class);
    var policy = new RuntimeFeatureAudiencePolicy(mock(RuntimeFeatureUserGroupAccess.class));
    var states =
        List.of(
            state(
                "rtsp",
                RuntimeFeatureConsumerClass.FRONTEND_ONLY,
                new RuntimeFeatureAudience.All()),
            state(
                "watchbox",
                RuntimeFeatureConsumerClass.BACKEND_ENFORCED,
                new RuntimeFeatureAudience.None()));
    when(reader.findAll()).thenReturn(states);

    var metadata = new GetRuntimeFeatureMetadataUsecase(reader, policy).getFor(USER_ID, true);

    assertThat(metadata.features())
        .extracting(feature -> feature.name() + ":" + feature.enabled())
        .containsExactly("rtsp:true", "watchbox:false");
    assertThat(metadata.adminRuntimeFeatures()).contains(states);
  }

  @Test
  void typedBackendCheckFailsLoudlyForMissingOrWrongConsumerClass() {
    var reader = mock(RuntimeFeatureStateReader.class);
    var policy = new RuntimeFeatureAudiencePolicy(mock(RuntimeFeatureUserGroupAccess.class));
    var usecase = new CheckRuntimeFeatureAvailabilityUsecase(reader, policy);

    when(reader.findByName("watchbox")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> usecase.isAvailable(BackendRuntimeFeature.WATCHBOX, USER_ID))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception ->
                assertThat(exception.getCode())
                    .isEqualTo(RUNTIME_FEATURE_REQUIRED_ROW_MISSING.name()));

    when(reader.findByName("watchbox"))
        .thenReturn(
            Optional.of(
                state(
                    "watchbox",
                    RuntimeFeatureConsumerClass.FRONTEND_ONLY,
                    new RuntimeFeatureAudience.All())));
    assertThatThrownBy(() -> usecase.isAvailable(BackendRuntimeFeature.WATCHBOX, USER_ID))
        .isInstanceOf(RuntimeFeatureException.class);
  }

  @Test
  void managementMapsAppliedAndResetsWatchboxTracking() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var policy = mock(RuntimeFeatureAudiencePolicy.class);
    var trackingReset = mock(WatchboxTrackingResetGateway.class);
    var trackingTelemetry = mock(WatchboxTrackingResetTelemetry.class);
    var usecase =
        new ManageRuntimeFeaturesUsecase(
            mutation, policy, NAME_POLICY, trackingReset, trackingTelemetry);
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    var audience = new RuntimeFeatureAudience.All();
    var state = state("watchbox", RuntimeFeatureConsumerClass.BACKEND_ENFORCED, audience);
    when(mutation.replaceAudience("watchbox", audience, OptionalLong.of(0), actor))
        .thenReturn(
            new RuntimeFeatureWriteResult.Applied(state, new RuntimeFeatureAudience.None()));
    when(trackingReset.bumpAllActiveConfigEpochs()).thenReturn(3);

    assertThat(usecase.replaceAudience("watchbox", audience, OptionalLong.of(0), actor))
        .isEqualTo(state);
    var order = inOrder(policy, mutation);
    order.verify(policy).assertValidTargets(audience);
    order.verify(mutation).replaceAudience("watchbox", audience, OptionalLong.of(0), actor);
    verify(trackingReset).bumpAllActiveConfigEpochs();
    verify(trackingTelemetry).recordResetAfterCommit(audience, 3);
  }

  @Test
  void replaceAudience_whenWatchboxMovesFromNoneToUserGroups_resetsTracking() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var policy = mock(RuntimeFeatureAudiencePolicy.class);
    var trackingReset = mock(WatchboxTrackingResetGateway.class);
    var trackingTelemetry = mock(WatchboxTrackingResetTelemetry.class);
    var usecase =
        new ManageRuntimeFeaturesUsecase(
            mutation, policy, NAME_POLICY, trackingReset, trackingTelemetry);
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    var audience = new RuntimeFeatureAudience.UserGroups(new TreeSet<>(Set.of(GROUP_A)));
    var state = state("watchbox", RuntimeFeatureConsumerClass.BACKEND_ENFORCED, audience);
    when(mutation.replaceAudience("watchbox", audience, OptionalLong.empty(), actor))
        .thenReturn(
            new RuntimeFeatureWriteResult.Applied(state, new RuntimeFeatureAudience.None()));
    when(trackingReset.bumpAllActiveConfigEpochs()).thenReturn(2);

    assertThat(usecase.replaceAudience("watchbox", audience, OptionalLong.empty(), actor))
        .isEqualTo(state);

    verify(trackingReset).bumpAllActiveConfigEpochs();
    verify(trackingTelemetry).recordResetAfterCommit(audience, 2);
  }

  @Test
  void replaceAudience_whenWatchboxAudienceIsAlreadyLive_doesNotResetTracking() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var policy = mock(RuntimeFeatureAudiencePolicy.class);
    var trackingReset = mock(WatchboxTrackingResetGateway.class);
    var trackingTelemetry = mock(WatchboxTrackingResetTelemetry.class);
    var usecase =
        new ManageRuntimeFeaturesUsecase(
            mutation, policy, NAME_POLICY, trackingReset, trackingTelemetry);
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    var previous = new RuntimeFeatureAudience.UserGroups(new TreeSet<>(Set.of(GROUP_A)));
    var audience = new RuntimeFeatureAudience.All();
    var state = state("watchbox", RuntimeFeatureConsumerClass.BACKEND_ENFORCED, audience);
    when(mutation.replaceAudience("watchbox", audience, OptionalLong.empty(), actor))
        .thenReturn(new RuntimeFeatureWriteResult.Applied(state, previous));

    assertThat(usecase.replaceAudience("watchbox", audience, OptionalLong.empty(), actor))
        .isEqualTo(state);

    verifyNoInteractions(trackingReset, trackingTelemetry);
  }

  @Test
  void replaceAudience_whenAnotherFeatureIsEnabled_doesNotResetWatchboxTracking() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var policy = mock(RuntimeFeatureAudiencePolicy.class);
    var trackingReset = mock(WatchboxTrackingResetGateway.class);
    var trackingTelemetry = mock(WatchboxTrackingResetTelemetry.class);
    var usecase =
        new ManageRuntimeFeaturesUsecase(
            mutation, policy, NAME_POLICY, trackingReset, trackingTelemetry);
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    var audience = new RuntimeFeatureAudience.All();
    var state = state("rtsp", RuntimeFeatureConsumerClass.FRONTEND_ONLY, audience);
    when(mutation.replaceAudience("rtsp", audience, OptionalLong.empty(), actor))
        .thenReturn(
            new RuntimeFeatureWriteResult.Applied(state, new RuntimeFeatureAudience.None()));

    assertThat(usecase.replaceAudience("rtsp", audience, OptionalLong.empty(), actor))
        .isEqualTo(state);

    verifyNoInteractions(trackingReset, trackingTelemetry);
  }

  @Test
  void managementRejectsMissingTargetsBeforeMutation() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var access = mock(RuntimeFeatureUserGroupAccess.class);
    var policy = new RuntimeFeatureAudiencePolicy(access);
    var usecase = managementUsecase(mutation, policy);
    var audience = new RuntimeFeatureAudience.UserGroups(new TreeSet<>(Set.of(GROUP_B, GROUP_A)));
    when(access.stabilizeAndFindMissingTargets(audience.userGroupIds()))
        .thenReturn(Set.of(GROUP_B, GROUP_A));

    assertThatThrownBy(
            () ->
                usecase.replaceAudience(
                    "watchbox",
                    audience,
                    OptionalLong.empty(),
                    new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false)))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(RUNTIME_FEATURE_INVALID_USER_GROUP_TARGETS.name());
              assertThat(exception.getMessage())
                  .contains(GROUP_A.toString())
                  .contains(GROUP_B.toString())
                  .contains(GROUP_A + ", " + GROUP_B);
            });
    verify(mutation, never())
        .replaceAudience(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void managementMapsTargetLockFailureToBusyBeforeMutation() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var policy = mock(RuntimeFeatureAudiencePolicy.class);
    var usecase = managementUsecase(mutation, policy);
    var audience = new RuntimeFeatureAudience.UserGroups(new TreeSet<>(Set.of(GROUP_A)));
    doThrow(new RuntimeFeatureBusyException(new IllegalStateException("contended")))
        .when(policy)
        .assertValidTargets(audience);

    assertThatThrownBy(
            () ->
                usecase.replaceAudience(
                    "watchbox",
                    audience,
                    OptionalLong.empty(),
                    new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false)))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_BUSY.name());
              assertThat(exception.getMessage())
                  .contains("Runtime feature or one of its target groups");
            });
    verify(mutation, never())
        .replaceAudience(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void managementMapsFeatureContentionToBusy() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var policy = mock(RuntimeFeatureAudiencePolicy.class);
    var usecase = managementUsecase(mutation, policy);
    var audience = new RuntimeFeatureAudience.All();
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    when(mutation.replaceAudience("watchbox", audience, OptionalLong.empty(), actor))
        .thenThrow(new RuntimeFeatureBusyException(new IllegalStateException("contended")));

    assertThatThrownBy(
            () -> usecase.replaceAudience("watchbox", audience, OptionalLong.empty(), actor))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_BUSY.name()));
  }

  @Test
  void registrationValidatesTheNameBeforeTouchingTheCatalog() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var usecase = managementUsecase(mutation, mock(RuntimeFeatureAudiencePolicy.class));
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);

    assertThatThrownBy(() -> usecase.register("themesPage", actor))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_INVALID_NAME.name()));
    assertThatThrownBy(() -> usecase.register("watchbox", actor))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_RESERVED_NAME.name()));

    verifyNoInteractions(mutation);
  }

  @Test
  void registrationRejectsAnExistingBackendEnforcedNameAsAClassConflict() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var usecase = managementUsecase(mutation, mock(RuntimeFeatureAudiencePolicy.class));
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    // A backend-owned row whose name predates this build's BackendRuntimeFeature enum, so the
    // reserved-name guard cannot catch it and the stored class is the only signal.
    var backendOwned =
        state(
            "legacy-backend-flag",
            RuntimeFeatureConsumerClass.BACKEND_ENFORCED,
            new RuntimeFeatureAudience.All());
    when(mutation.register("legacy-backend-flag", actor))
        .thenReturn(new RuntimeFeatureRegistrationResult.AlreadyExists(backendOwned));

    assertThatThrownBy(() -> usecase.register("legacy-backend-flag", actor))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_CLASS_CONFLICT.name());
              assertThat(exception.getMessage()).contains("BACKEND_ENFORCED");
            });
  }

  @Test
  void registrationReturnsCreatedAndConvergedOutcomesForFrontendOnlyNames() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var usecase = managementUsecase(mutation, mock(RuntimeFeatureAudiencePolicy.class));
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    var fresh =
        state(
            "themes-page",
            RuntimeFeatureConsumerClass.FRONTEND_ONLY,
            new RuntimeFeatureAudience.None());
    var existing =
        state(
            "plugin-loader",
            RuntimeFeatureConsumerClass.FRONTEND_ONLY,
            new RuntimeFeatureAudience.All());
    when(mutation.register("themes-page", actor))
        .thenReturn(new RuntimeFeatureRegistrationResult.Created(fresh));
    when(mutation.register("plugin-loader", actor))
        .thenReturn(new RuntimeFeatureRegistrationResult.AlreadyExists(existing));

    assertThat(usecase.register("themes-page", actor))
        .isEqualTo(new RuntimeFeatureRegistrationResult.Created(fresh));
    // Re-registering an existing frontend-only name is convergence, not an error.
    assertThat(usecase.register("plugin-loader", actor))
        .isEqualTo(new RuntimeFeatureRegistrationResult.AlreadyExists(existing));
  }

  @Test
  void registrationMapsCatalogContentionToBusy() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var usecase = managementUsecase(mutation, mock(RuntimeFeatureAudiencePolicy.class));
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    when(mutation.register("themes-page", actor))
        .thenThrow(new RuntimeFeatureBusyException(new IllegalStateException("contended")));

    assertThatThrownBy(() -> usecase.register("themes-page", actor))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_BUSY.name()));
  }

  // A Rejection is keyed by name, so a null entry has nothing to report under. It must fail as a
  // contract violation up front rather than as an opaque NPE from inside Rejection's constructor
  // after earlier names have already been committed.
  @Test
  void reconcileRejectsANullNameEntryBeforeAttemptingAnything() {
    var mutation = mock(RuntimeFeatureMutationGateway.class);
    var usecase = managementUsecase(mutation, mock(RuntimeFeatureAudiencePolicy.class));
    var actor = new RuntimeFeatureActor(USER_ID, Optional.empty(), Optional.empty(), false);
    var names = new ArrayList<String>();
    names.add("themes-page");
    names.add(null);

    assertThatThrownBy(() -> usecase.reconcile(names, actor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");

    verifyNoInteractions(mutation);
  }

  private static ManageRuntimeFeaturesUsecase managementUsecase(
      RuntimeFeatureMutationGateway mutation, RuntimeFeatureAudiencePolicy policy) {
    return new ManageRuntimeFeaturesUsecase(
        mutation,
        policy,
        NAME_POLICY,
        mock(WatchboxTrackingResetGateway.class),
        mock(WatchboxTrackingResetTelemetry.class));
  }

  private static RuntimeFeatureState state(
      String name, RuntimeFeatureConsumerClass consumerClass, RuntimeFeatureAudience audience) {
    return new RuntimeFeatureState(name, consumerClass, audience, 0, Instant.EPOCH);
  }
}

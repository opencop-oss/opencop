// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.watchbox.business.model.WatchboxLayerRole;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchboxLayerAccessPolicyTest {

  @Mock WatchboxLayerAccess layerAccess;

  private final UUID userId = UUID.randomUUID();
  private final UUID layerId = UUID.randomUUID();
  private final UUID otherLayerId = UUID.randomUUID();

  @Test
  void globalAdminBypassesGatewayAndManagesAllLayers() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var layerIds = Set.of(layerId, otherLayerId);

    var decision = policy.decide(layerIds, new WatchboxCaller(userId, true));

    assertThat(decision.fullyVisible()).isTrue();
    assertThat(decision.managesAll()).isTrue();
    verifyNoInteractions(layerAccess);
  }

  @Test
  void decide_whenNonAdminCannotSeeEveryLayer_marksWatchboxInvisible() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId, otherLayerId);
    when(layerAccess.rolesForLayers(layerIds, caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.MEMBER));

    var decision = policy.decide(layerIds, caller);

    assertThat(decision.fullyVisible()).isFalse();
    assertThat(decision.managesAll()).isFalse();
    verify(layerAccess).rolesForLayers(layerIds, caller);
  }

  @Test
  void ownerAndAdminOnEveryRequestedLayerCanManageAll() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId, otherLayerId);
    when(layerAccess.rolesForLayers(layerIds, caller))
        .thenReturn(
            Map.of(layerId, WatchboxLayerRole.OWNER, otherLayerId, WatchboxLayerRole.ADMIN));

    var decision = policy.decide(layerIds, caller);

    assertThat(decision.fullyVisible()).isTrue();
    assertThat(decision.managesAll()).isTrue();
  }

  @Test
  void editorAndMemberCanViewButCannotManage() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId, otherLayerId);
    when(layerAccess.rolesForLayers(layerIds, caller))
        .thenReturn(
            Map.of(layerId, WatchboxLayerRole.EDITOR, otherLayerId, WatchboxLayerRole.MEMBER));

    var decision = policy.decide(layerIds, caller);

    assertThat(decision.fullyVisible()).isTrue();
    assertThat(decision.managesAll()).isFalse();
  }

  @Test
  void assertCanManageAllowsOwnerAndAdminOnEveryRequestedLayer() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId, otherLayerId);
    when(layerAccess.rolesForLayers(layerIds, caller))
        .thenReturn(
            Map.of(layerId, WatchboxLayerRole.OWNER, otherLayerId, WatchboxLayerRole.ADMIN));

    policy.assertCanManage(layerIds, caller);

    verify(layerAccess).rolesForLayers(layerIds, caller);
  }

  @Test
  void assertCanManageRejectsCallerWithoutManagementRoleOnEveryRequestedLayer() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId, otherLayerId);
    when(layerAccess.rolesForLayers(layerIds, caller))
        .thenReturn(
            Map.of(layerId, WatchboxLayerRole.OWNER, otherLayerId, WatchboxLayerRole.EDITOR));

    assertThatThrownBy(() -> policy.assertCanManage(layerIds, caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code().name()).isEqualTo("WATCHBOX_MANAGEMENT_ROLE_REQUIRED"));
  }

  @Test
  void assertVisible_whenViewerCannotSeeEveryLayer_throwsNotFound() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId, otherLayerId);
    when(layerAccess.rolesForLayers(layerIds, caller))
        .thenReturn(Map.of(layerId, WatchboxLayerRole.MEMBER));

    assertThatThrownBy(() -> policy.assertVisible(layerIds, caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code().name()).isEqualTo("WATCHBOX_NOT_FOUND"));
  }

  @Test
  void assertVisibleRejectsCallerWithoutAnyVisibleLayerAsNotFound() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId);
    when(layerAccess.rolesForLayers(layerIds, caller)).thenReturn(Map.of());

    assertThatThrownBy(() -> policy.assertVisible(layerIds, caller))
        .isInstanceOfSatisfying(
            WatchboxException.class,
            ex -> assertThat(ex.code().name()).isEqualTo("WATCHBOX_NOT_FOUND"));
  }

  @Test
  void emptyRoleMapIsInvisibleAndNotManageable() {
    var policy = new WatchboxLayerAccessPolicy(layerAccess);
    var caller = new WatchboxCaller(userId, false);
    var layerIds = Set.of(layerId);
    when(layerAccess.rolesForLayers(layerIds, caller)).thenReturn(Map.of());

    var decision = policy.decide(layerIds, caller);

    assertThat(decision.fullyVisible()).isFalse();
    assertThat(decision.managesAll()).isFalse();
    verify(layerAccess).rolesForLayers(layerIds, caller);
  }
}

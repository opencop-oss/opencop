// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.websocket.ports.gateway.iam.LiveEventResourceAccessGateway;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveEventAccessPolicyTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554410a1");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554410a2");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554410a3");

  @Test
  void allowsSubscriptionWhenUserHasLayerAccessAndLayerIsActive() {
    FakeGateway gateway = new FakeGateway();
    gateway.userHasLayerAccess = true;
    gateway.layerIsActive = true;
    LiveEventAccessPolicy policy = new LiveEventAccessPolicy(gateway);

    assertTrue(policy.userCanSubscribe(USER_ID, LAYER_ID));
  }

  @Test
  void deniesSubscriptionWhenUserHasLayerAccessButLayerIsArchived() {
    FakeGateway gateway = new FakeGateway();
    gateway.userHasLayerAccess = true;
    gateway.layerIsActive = false;
    LiveEventAccessPolicy policy = new LiveEventAccessPolicy(gateway);

    assertFalse(policy.userCanSubscribe(USER_ID, LAYER_ID));
  }

  @Test
  void allowsSubscriptionWhenNoLayerAccessButUserHasActiveSourceAccess() {
    FakeGateway gateway = new FakeGateway();
    gateway.userHasLayerAccess = false;
    gateway.userHasSourceAccess = true;
    gateway.sourceIsActive = true;
    LiveEventAccessPolicy policy = new LiveEventAccessPolicy(gateway);

    assertTrue(policy.userCanSubscribe(USER_ID, SOURCE_ID));
  }

  @Test
  void deniesSubscriptionWhenSourceAccessExistsButSourceIsArchived() {
    FakeGateway gateway = new FakeGateway();
    gateway.userHasLayerAccess = false;
    gateway.userHasSourceAccess = true;
    gateway.sourceIsActive = false;
    LiveEventAccessPolicy policy = new LiveEventAccessPolicy(gateway);

    assertFalse(policy.userCanSubscribe(USER_ID, SOURCE_ID));
  }

  @Test
  void deniesSubscriptionWithoutPermissionAndSkipsSourceLifecycleLookup() {
    FakeGateway gateway = new FakeGateway();
    gateway.userHasLayerAccess = false;
    gateway.userHasSourceAccess = false;
    LiveEventAccessPolicy policy = new LiveEventAccessPolicy(gateway);

    assertFalse(policy.userCanSubscribe(USER_ID, SOURCE_ID));
    assertFalse(gateway.sourceIsActiveCalled);
  }

  private static final class FakeGateway implements LiveEventResourceAccessGateway {
    boolean userHasLayerAccess;
    boolean userHasSourceAccess;
    boolean layerIsActive;
    boolean sourceIsActive;
    boolean sourceIsActiveCalled;

    @Override
    public boolean userHasLayerAccess(UUID userId, UUID layerId) {
      return userHasLayerAccess;
    }

    @Override
    public boolean userHasSourceAccess(UUID userId, UUID sourceId) {
      return userHasSourceAccess;
    }

    @Override
    public boolean layerIsActive(UUID layerId) {
      return layerIsActive;
    }

    @Override
    public boolean sourceIsActive(UUID sourceId) {
      sourceIsActiveCalled = true;
      return sourceIsActive;
    }
  }
}

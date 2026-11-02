// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LayerBroadcastPolicyTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");

  private UserSpecificManager userManager;
  private LayerBroadcastPolicy policy;

  @BeforeEach
  void setUp() {
    userManager = mock(UserSpecificManager.class);
    policy = new LayerBroadcastPolicy(userManager);
  }

  @Test
  void assertLayerEditor_whenUserIsEditor_passes() {
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    LAYER_ID,
                    "test",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    assertDoesNotThrow(() -> policy.assertLayerEditor(USER_ID, LAYER_ID));
  }

  @Test
  void assertLayerEditor_whenUserIsMember_throwsUnauthorized() {
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    LAYER_ID,
                    "test",
                    LayerRole.MEMBER,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    var ex =
        assertThrows(
            OpencopEventException.class, () -> policy.assertLayerEditor(USER_ID, LAYER_ID));
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
  }

  @Test
  void assertLayerEditor_whenUserHasNoLayerRole_throwsUnauthorized() {
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            OpencopEventException.class, () -> policy.assertLayerEditor(USER_ID, LAYER_ID));
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
  }
}

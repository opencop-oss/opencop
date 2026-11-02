// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.UNAUTHORIZED;

import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LayerBroadcastPolicy {

  private final UserSpecificManager userManager;

  public LayerBroadcastPolicy(UserSpecificManager userManager) {
    this.userManager = userManager;
  }

  public void assertLayerEditor(UUID userId, UUID layerId) {
    boolean editor =
        userManager.getUserLayers(userId).stream()
            .anyMatch(
                info -> info.layerId().equals(layerId) && info.role().isAtLeast(LayerRole.EDITOR));
    if (!editor) {
      throw new OpencopEventException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }
  }
}

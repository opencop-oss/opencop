// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationInUseException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationNotDeactivatableException;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class IntegrationActivationPolicy {

  private static final Comparator<ConnectionMetadata> BLOCKER_ORDER =
      Comparator.comparing(ConnectionMetadata::name).thenComparing(ConnectionMetadata::id);

  private IntegrationActivationPolicy() {}

  public static boolean isNoOp(IntegrationIdentity integration, boolean requestedActive) {
    return integration.active() == requestedActive;
  }

  /**
   * Requires the integration to be one an operator may deactivate. Native is never: it sets up
   * every connection no plugin owns, so deactivating it would leave the platform unable to create
   * any.
   */
  public static void requireDeactivatable(UUID integrationId) {
    if (NativeIntegration.NATIVE_ID.equals(integrationId)) {
      throw new IntegrationNotDeactivatableException(integrationId);
    }
  }

  public static void requireUnused(List<ConnectionMetadata> connections) {
    if (!connections.isEmpty()) {
      throw new IntegrationInUseException(connections.stream().sorted(BLOCKER_ORDER).toList());
    }
  }
}

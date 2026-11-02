// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Authoritative rules for what mutations are permitted on archived layers and data sources.
 *
 * <p><b>Operational wiring is frozen on archived rows.</b> Once a layer or data source is archived,
 * the following are rejected with {@link HttpStatus#CONFLICT}: broadcasts, runtime mutations,
 * workflow execution, websocket live subscriptions, source-to-layer relationships, and any other
 * code path that would create or alter a resource-to-resource connection. {@link
 * #assertLayerActive} and {@link #assertDataSourceActive} are the guard points; callers reach them
 * via {@link ResourceLifecycleGuard}.
 *
 * <p><b>ACL grants on archived layers remain mutable</b> by design. Admins can add, modify, or
 * revoke user and group access on an archived layer after archival so analysts can review
 * historical data through the time scrubber. This carve-out is intentional and is the reason {@code
 * AppGroupLayerService.assignGroupToLayer}, the layer-member endpoints, and {@code
 * EffectiveLayerAccessService.getEffectiveRole} deliberately do not call {@link #assertLayerActive}
 * and read layers with {@code RecordVisibility.ALL}. Aggregate access listings, source-access
 * lookups, and live-event authorization still hide archived layers from non-admin runtime paths;
 * the open ACL surface is only for explicit admin grant management.
 *
 * <p><b>Asymmetry with data sources.</b> Data-source archival freezes its source-to-layer wiring
 * (handled in {@code SourceLayerRelationshipUsecase}) because that link is operational wiring, not
 * an access grant. Data-source ACL rows (source-user, source-layer access) remain mutable on
 * archived sources for the same historical-analysis reason as layer ACLs.
 */
public final class ResourceLifecyclePolicy {

  private static final String ARCHIVED_LAYER_MESSAGE = "Archived layer is read-only";
  private static final String INACTIVE_LAYER_MESSAGE = "Layer is not active";
  private static final String ARCHIVED_DATA_SOURCE_MESSAGE = "Archived data source is read-only";
  private static final String OPERATION_PREFIX = " for operation: ";
  private static final HttpStatus LIFECYCLE_CONFLICT_STATUS = HttpStatus.CONFLICT;

  private ResourceLifecyclePolicy() {}

  public static void assertLayerActive(Layer layer, String operation) {
    if (layer.getLifecycleState() != LayerLifecycleState.ACTIVE) {
      throw new GlobalOpenCopException(
          INACTIVE_LAYER_MESSAGE + OPERATION_PREFIX + operation, LIFECYCLE_CONFLICT_STATUS);
    }
    if (layer.getArchivedAt() != null) {
      throw new GlobalOpenCopException(
          ARCHIVED_LAYER_MESSAGE + OPERATION_PREFIX + operation, LIFECYCLE_CONFLICT_STATUS);
    }
  }

  public static void assertDataSourceActive(DataSource dataSource, String operation) {
    assertDataSourceActive(dataSource.archivedAt(), operation);
  }

  public static void assertDataSourceActive(Instant archivedAt, String operation) {
    if (archivedAt != null) {
      throw new GlobalOpenCopException(
          ARCHIVED_DATA_SOURCE_MESSAGE + OPERATION_PREFIX + operation, LIFECYCLE_CONFLICT_STATUS);
    }
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.SecurityKind;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.exception.ErrorDetail;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorStateReason;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data-source connection information.
 *
 * @param stateReason why {@code state} is {@code FAILED}, when the runtime reported a reason;
 *     {@code null} for every other state
 * @param lastChange existing response alias for {@code updatedAt}; both expose the data source's
 *     {@code updated_at}. New consumers should prefer {@code updatedAt}.
 * @param settings the integration settings the connection overrides, keyed by section then field,
 *     or {@code null} when it overrides none
 * @param effectiveSettings every declared integration setting as Core resolves it, the stored
 *     override or else Core's deployment default; not reported back by the running connection.
 *     {@code null} when they cannot be resolved
 * @param settingsStatus whether Core resolves the stored overrides; {@code REJECTED} explains a
 *     {@code null} {@code effectiveSettings} for a connection that has settings
 * @param settingsViolations why the stored overrides no longer resolve; empty unless {@code
 *     settingsStatus} is {@code REJECTED}
 * @param setup the connection's setup in the shape a create request takes, to fill a setup form
 *     with
 */
public record ConnectionInfo(
    UUID id,
    String name,
    IntegrationConnectionSchemaReference integration,
    ConnectorState state,
    ConnectorStateReason stateReason,
    TransportKind transportType,
    SecurityKind securityType,
    Instant createdAt,
    UUID createdBy,
    Instant archivedAt,
    UUID archivedBy,
    Instant lastChange,
    Instant lastMessageAt,
    Instant updatedAt,
    UUID updatedBy,
    Instant lastEventAt,
    String assignedInstance,
    boolean defaultVisible,
    AccessMode access,
    Map<String, Map<String, Object>> settings,
    Map<String, Map<String, Object>> effectiveSettings,
    ConnectionSettingsStatus settingsStatus,
    List<ErrorDetail> settingsViolations,
    ConnectionSetupInfo setup) {

  public ConnectionInfo {
    settingsViolations = settingsViolations == null ? List.of() : List.copyOf(settingsViolations);
  }
}

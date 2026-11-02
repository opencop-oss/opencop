// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.source;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.time.Instant;
import java.util.UUID;

/**
 * A data source as seen from one user's access: the user's effective role plus the source's audit
 * metadata.
 *
 * <p>{@code lastEventAt} is null when no activity has been recorded. {@code createdBy} and {@code
 * updatedBy} are ids; resolving them to usernames is the caller's job and is meant for
 * single-record views only.
 */
public record SourceInfo(
    UUID serverId,
    String serverName,
    String serverState,
    ServerRole role,
    ServerRole directRole,
    AccessVia via,
    AccessMode access,
    boolean defaultVisible,
    TransportKind transportType,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy,
    Instant lastEventAt) {
  public SourceInfo(
      UUID serverId,
      String serverName,
      String serverState,
      ServerRole role,
      AccessVia via,
      AccessMode access,
      boolean defaultVisible,
      TransportKind transportType) {
    this(
        serverId,
        serverName,
        serverState,
        role,
        null,
        via,
        access,
        defaultVisible,
        transportType,
        null,
        null,
        null,
        null,
        null);
  }

  public SourceInfo(
      UUID serverId,
      String serverName,
      String serverState,
      ServerRole role,
      AccessVia via,
      AccessMode access,
      boolean defaultVisible) {
    this(serverId, serverName, serverState, role, via, access, defaultVisible, null);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.me.permissions;

import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserSources;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.model.iam.source.SourceInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetrieveUserSourcesUsecase implements RetrieveUserSources {

  private static final Logger log = LoggerFactory.getLogger(RetrieveUserSourcesUsecase.class);

  private final UserSpecificManager userSpecificManager;
  private final DataSourceRuntimeService runtimeService;

  public RetrieveUserSourcesUsecase(
      final UserSpecificManager userSpecificManager,
      final DataSourceRuntimeService runtimeService) {
    this.userSpecificManager = userSpecificManager;
    this.runtimeService = runtimeService;
  }

  /**
   * Gets data sources for a user (there is a different admin endpoint for all data sources).
   *
   * <p>Runtime state is read from Postgres observations via DataSourceRuntimeService. KAFKA state
   * is derived from broker checks.
   */
  @Override
  public List<SourceInfo> get(UUID userId, RecordVisibility recordVisibility) {
    var sourcesRole = this.userSpecificManager.getUserServers(userId);
    var directRolesBySourceId =
        this.userSpecificManager.getUserDirectServers(userId).stream()
            .collect(Collectors.toMap(SourceRole::serverId, SourceRole::role));

    var sourceIds = sourcesRole.stream().map(SourceRole::serverId).collect(Collectors.toSet());
    var runtimeMap = runtimeService.getWithRuntime(sourceIds, recordVisibility);

    List<SourceInfo> sources = new ArrayList<>();
    sourcesRole.forEach(
        sourceRole -> {
          var dsr = runtimeMap.get(sourceRole.serverId());
          if (dsr != null) {
            sources.add(
                new SourceInfo(
                    sourceRole.serverId(),
                    dsr.name(),
                    dsr.runtime().state().name(),
                    sourceRole.role(),
                    directRolesBySourceId.get(sourceRole.serverId()),
                    sourceRole.via(),
                    dsr.dataSource().access(),
                    dsr.dataSource().defaultVisible(),
                    dsr.dataSource().provider(),
                    dsr.dataSource().createdAt(),
                    dsr.dataSource().createdBy(),
                    dsr.dataSource().updatedAt(),
                    dsr.dataSource().updatedBy(),
                    dsr.dataSource().lastEventAt()));
          } else {
            // Not an anomaly, and never a missing row: iam.effective_source_roles joins
            // profiles.data_source, so every id here belongs to a source that exists. The runtime
            // read applies the requested visibility on top of that, so a miss just means this
            // source is filtered out of this listing — an archived source under ACTIVE, an active
            // one under ARCHIVED. Warning per miss made every read cost one line per source the
            // filter excluded.
            log.debug(
                "Source {} is held by user {} but excluded from this listing by visibility {}",
                sourceRole.serverId(),
                userId,
                recordVisibility);
          }
        });
    return sources;
  }
}

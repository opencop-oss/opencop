// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.iam;

import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SourceAccessService implements SourceAccessRepository {

  private final EffectiveSourceRoleRepository sourceRoleRepository;

  public SourceAccessService(EffectiveSourceRoleRepository sourceRoleRepository) {
    this.sourceRoleRepository = sourceRoleRepository;
  }

  @Override
  public boolean userHasAccess(UUID userId, UUID sourceId) {
    return userRole(userId, sourceId) != null;
  }

  @Override
  public ServerRole userRole(UUID userId, UUID sourceId) {
    return sourceRoleRepository
        .findActiveEffectiveRole(userId, sourceId)
        .map(EffectiveSourceRoleRepository.EffectiveSourceRoleRow::getRole)
        .map(ServerRole::valueOf)
        .orElse(null);
  }

  @Override
  public List<SourceRole> userRoles(UUID userId) {
    return sourceRoleRepository.findEffectiveRolesIncludingArchivedSources(userId).stream()
        .map(SourceAccessService::toSourceRole)
        .toList();
  }

  private static SourceRole toSourceRole(EffectiveSourceRoleRepository.EffectiveSourceRoleRow row) {
    return new SourceRole(
        row.getSourceId(), ServerRole.valueOf(row.getRole()), AccessVia.valueOf(row.getVia()));
  }
}

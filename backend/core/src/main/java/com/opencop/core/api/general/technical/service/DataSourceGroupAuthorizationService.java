// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupMemberIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DataSourceGroupAuthorizationService implements DataSourceGroupAuthorization {

  private final IamDataSourceGroupMembersRepository membersRepository;
  private final IamGroupMembersRepository groupMembersRepository;
  private final IamDataSourceGroupUserGroupRepository dataSourceGroupUserGroupRepository;

  public DataSourceGroupAuthorizationService(
      IamDataSourceGroupMembersRepository membersRepository,
      IamGroupMembersRepository groupMembersRepository,
      IamDataSourceGroupUserGroupRepository dataSourceGroupUserGroupRepository) {
    this.membersRepository = membersRepository;
    this.groupMembersRepository = groupMembersRepository;
    this.dataSourceGroupUserGroupRepository = dataSourceGroupUserGroupRepository;
  }

  @Override
  public Optional<DataSourceGroupRole> getEffectiveContainerRole(
      UUID dataSourceGroupId, OpenCopPrincipal principal) {
    var memberId = new DataSourceGroupMemberIdEntity(dataSourceGroupId, principal.internalUserId());
    var directMember = membersRepository.findById(memberId);
    DataSourceGroupRole highest = directMember.map(m -> m.getRole()).orElse(null);

    Set<UUID> userGroupIds =
        groupMembersRepository.findGroupIdsByUserId(principal.internalUserId());
    if (!userGroupIds.isEmpty()) {
      var assignments =
          dataSourceGroupUserGroupRepository.findAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
              dataSourceGroupId, userGroupIds);
      for (var assignment : assignments) {
        DataSourceGroupRole role = assignment.getRole();
        if (highest == null || role.isHigherThan(highest)) {
          highest = role;
        }
      }
    }

    return Optional.ofNullable(highest);
  }
}

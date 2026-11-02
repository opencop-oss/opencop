// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.iam;

import com.opencop.core.api.general.ports.gateway.runtimefeature.RuntimeFeatureUserGroupAccess;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureBusyException;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.PessimisticLockingFailureException;

public class IamRuntimeFeatureUserGroupAccess implements RuntimeFeatureUserGroupAccess {

  private final IamGroupRepository groupRepository;
  private final IamGroupMembersRepository groupMembersRepository;

  public IamRuntimeFeatureUserGroupAccess(
      IamGroupRepository groupRepository, IamGroupMembersRepository groupMembersRepository) {
    this.groupRepository = groupRepository;
    this.groupMembersRepository = groupMembersRepository;
  }

  @Override
  public Set<UUID> stabilizeAndFindMissingTargets(Set<UUID> userGroupIds) {
    Set<UUID> existingIds;
    try {
      existingIds =
          groupRepository.findAllByIdInForUpdate(userGroupIds).stream()
              .map(GroupEntity::getId)
              .collect(Collectors.toSet());
    } catch (PessimisticLockingFailureException exception) {
      throw new RuntimeFeatureBusyException(exception);
    }
    var missingIds = new TreeSet<>(userGroupIds);
    missingIds.removeAll(existingIds);
    return Collections.unmodifiableSet(missingIds);
  }

  @Override
  public boolean hasMembership(UUID userId, Set<UUID> userGroupIds) {
    return groupMembersRepository.existsByUserIdAndGroupIds(userId, userGroupIds);
  }
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.iam;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureBusyException;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;

class IamRuntimeFeatureUserGroupAccessTest {

  @Test
  void translatesTargetContentionToNeutralBusyException() {
    var groupId = UUID.randomUUID();
    var groupRepository = mock(IamGroupRepository.class);
    var lockFailure = new PessimisticLockingFailureException("contended");
    when(groupRepository.findAllByIdInForUpdate(Set.of(groupId))).thenThrow(lockFailure);
    var access =
        new IamRuntimeFeatureUserGroupAccess(
            groupRepository, mock(IamGroupMembersRepository.class));

    assertThatThrownBy(() -> access.stabilizeAndFindMissingTargets(Set.of(groupId)))
        .isInstanceOf(RuntimeFeatureBusyException.class)
        .hasCause(lockFailure);
  }
}

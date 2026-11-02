// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_INVALID_USER_GROUP_TARGETS;

import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureException;
import com.opencop.core.api.general.ports.gateway.runtimefeature.RuntimeFeatureUserGroupAccess;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public class RuntimeFeatureAudiencePolicy {

  private final RuntimeFeatureUserGroupAccess userGroupAccess;

  public RuntimeFeatureAudiencePolicy(RuntimeFeatureUserGroupAccess userGroupAccess) {
    this.userGroupAccess = userGroupAccess;
  }

  public boolean isAvailable(RuntimeFeatureAudience audience, UUID userId) {
    return switch (audience) {
      case RuntimeFeatureAudience.None _ -> false;
      case RuntimeFeatureAudience.All _ -> true;
      // Anonymous or otherwise unresolved callers fail closed instead of reaching IAM persistence.
      case RuntimeFeatureAudience.UserGroups(var userGroupIds) ->
          userId != null && userGroupAccess.hasMembership(userId, userGroupIds);
    };
  }

  public void assertValidTargets(RuntimeFeatureAudience audience) {
    if (!(audience instanceof RuntimeFeatureAudience.UserGroups(var userGroupIds))) {
      return;
    }
    var missingIds = userGroupAccess.stabilizeAndFindMissingTargets(userGroupIds);
    if (missingIds.isEmpty()) {
      return;
    }
    var missingIdList =
        missingIds.stream().sorted().map(UUID::toString).collect(Collectors.joining(", "));
    throw new RuntimeFeatureException(
        RUNTIME_FEATURE_INVALID_USER_GROUP_TARGETS,
        "Runtime feature audience references missing user groups: " + missingIdList,
        HttpStatus.BAD_REQUEST);
  }
}

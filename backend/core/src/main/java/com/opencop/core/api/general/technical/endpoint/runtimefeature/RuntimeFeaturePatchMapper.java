// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_INVALID_REQUEST;

import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureException;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import java.util.HashSet;
import java.util.OptionalLong;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;

final class RuntimeFeaturePatchMapper {

  private RuntimeFeaturePatchMapper() {}

  static PatchCommand toCommand(RuntimeFeaturePatchRequest request) {
    if (request == null
        || !request.getUnknownProperties().isEmpty()
        || request.getAudience() == null
        || !request.getAudience().getUnknownProperties().isEmpty()) {
      throw invalidRequest();
    }
    var expectedRevision = request.getExpectedRevision();
    if (expectedRevision != null && expectedRevision < 0) {
      throw invalidRequest();
    }

    var audience = request.getAudience();
    if (audience.getMode() == null || audience.getMode().isBlank()) {
      throw invalidRequest();
    }
    var mappedAudience =
        switch (audience.getMode()) {
          case "NONE" -> {
            assertNoUserGroupIds(audience);
            yield new RuntimeFeatureAudience.None();
          }
          case "ALL" -> {
            assertNoUserGroupIds(audience);
            yield new RuntimeFeatureAudience.All();
          }
          case "USER_GROUPS" -> mapUserGroups(audience);
          default -> throw invalidRequest();
        };
    return new PatchCommand(
        mappedAudience,
        expectedRevision == null ? OptionalLong.empty() : OptionalLong.of(expectedRevision));
  }

  private static RuntimeFeatureAudience.UserGroups mapUserGroups(
      RuntimeFeaturePatchRequest.Audience audience) {
    var userGroupIds = audience.getUserGroupIds();
    if (!audience.isUserGroupIdsPresent()
        || userGroupIds == null
        || userGroupIds.isEmpty()
        || userGroupIds.size() > RuntimeFeatureAudience.UserGroups.MAX_USER_GROUP_IDS
        || userGroupIds.stream().anyMatch(java.util.Objects::isNull)
        || new HashSet<>(userGroupIds).size() != userGroupIds.size()) {
      throw invalidRequest();
    }
    return new RuntimeFeatureAudience.UserGroups(new TreeSet<>(userGroupIds));
  }

  private static void assertNoUserGroupIds(RuntimeFeaturePatchRequest.Audience audience) {
    if (audience.isUserGroupIdsPresent()) {
      throw invalidRequest();
    }
  }

  private static RuntimeFeatureException invalidRequest() {
    return new RuntimeFeatureException(
        RUNTIME_FEATURE_INVALID_REQUEST,
        "Invalid runtime feature replacement request",
        HttpStatus.BAD_REQUEST);
  }

  record PatchCommand(RuntimeFeatureAudience audience, OptionalLong expectedRevision) {}
}

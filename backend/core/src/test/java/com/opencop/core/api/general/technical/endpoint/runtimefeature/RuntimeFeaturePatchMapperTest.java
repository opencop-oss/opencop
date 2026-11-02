// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_INVALID_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureException;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import java.util.List;
import java.util.OptionalLong;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RuntimeFeaturePatchMapperTest {

  private static final UUID GROUP_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID GROUP_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void mapsEveryCanonicalAudience() {
    assertThat(map("{\"audience\":{\"mode\":\"NONE\"},\"expectedRevision\":0}"))
        .isEqualTo(
            new RuntimeFeaturePatchMapper.PatchCommand(
                new RuntimeFeatureAudience.None(), OptionalLong.of(0)));
    assertThat(map("{\"audience\":{\"mode\":\"ALL\"}}"))
        .isEqualTo(
            new RuntimeFeaturePatchMapper.PatchCommand(
                new RuntimeFeatureAudience.All(), OptionalLong.empty()));
    assertThat(
            map(
                "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
                    + GROUP_B
                    + "\",\""
                    + GROUP_A
                    + "\"]}}"))
        .isEqualTo(
            new RuntimeFeaturePatchMapper.PatchCommand(
                new RuntimeFeatureAudience.UserGroups(new TreeSet<>(List.of(GROUP_A, GROUP_B))),
                OptionalLong.empty()));
  }

  @Test
  void unknownRootAndNestedPropertiesAreRejectedLocally() {
    assertInvalid("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevison\":0}");
    assertInvalid("{\"audience\":{\"mode\":\"ALL\",\"future\":true}}");
  }

  @Test
  void malformedShapesAndUnknownModesAreRejected() {
    assertInvalid("{}");
    assertInvalid("{\"audience\":{\"mode\":\"NONE\",\"layerIds\":[]}}");
    assertInvalid("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":-1}");
    assertInvalid(
        "{\"audience\":{\"mode\":\"LAYERS\",\"layerIds\":[\"11111111-1111-4111-8111-111111111111\"]}}");
  }

  @Test
  void userGroupsRequiresAUniqueNonEmptyListOfNonNullIds() {
    assertInvalid("{\"audience\":{\"mode\":\"USER_GROUPS\"}}");
    assertInvalid("{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":null}}");
    assertInvalid("{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[]}}");
    assertInvalid("{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[null]}}");
    assertInvalid(
        "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
            + GROUP_A
            + "\",\""
            + GROUP_A
            + "\"]}}");
  }

  @Test
  void userGroupsAcceptsAtMostOneHundredTargets() {
    assertThat(
            map(userGroupsRequest(RuntimeFeatureAudience.UserGroups.MAX_USER_GROUP_IDS)).audience())
        .isInstanceOfSatisfying(
            RuntimeFeatureAudience.UserGroups.class,
            audience ->
                assertThat(audience.userGroupIds())
                    .hasSize(RuntimeFeatureAudience.UserGroups.MAX_USER_GROUP_IDS));
    assertInvalid(userGroupsRequest(RuntimeFeatureAudience.UserGroups.MAX_USER_GROUP_IDS + 1));
  }

  @Test
  void noneAndAllRejectUserGroupIdsEvenWhenExplicitlyNull() {
    assertInvalid("{\"audience\":{\"mode\":\"NONE\",\"userGroupIds\":null}}");
    assertInvalid("{\"audience\":{\"mode\":\"ALL\",\"userGroupIds\":[]}}");
  }

  private RuntimeFeaturePatchMapper.PatchCommand map(String json) {
    return RuntimeFeaturePatchMapper.toCommand(
        objectMapper.readValue(json, RuntimeFeaturePatchRequest.class));
  }

  private void assertInvalid(String json) {
    assertThatThrownBy(() -> map(json))
        .isInstanceOfSatisfying(
            RuntimeFeatureException.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo(RUNTIME_FEATURE_INVALID_REQUEST.name()));
  }

  private static String userGroupsRequest(int size) {
    var ids =
        IntStream.range(0, size)
            .mapToObj(index -> "\"" + new UUID(0, index) + "\"")
            .collect(Collectors.joining(","));
    return "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[" + ids + "]}}";
  }
}

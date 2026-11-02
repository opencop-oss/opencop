// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import java.util.TreeSet;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

final class RuntimeFeatureAudienceJsonMapper {

  private final ObjectMapper objectMapper;

  RuntimeFeatureAudienceJsonMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  String write(RuntimeFeatureAudience audience) {
    var node = objectMapper.createObjectNode();
    switch (audience) {
      case RuntimeFeatureAudience.None _ -> node.put("mode", "NONE");
      case RuntimeFeatureAudience.All _ -> node.put("mode", "ALL");
      case RuntimeFeatureAudience.UserGroups(var userGroupIds) -> {
        node.put("mode", "USER_GROUPS");
        var ids = node.putArray("userGroupIds");
        userGroupIds.forEach(id -> ids.add(id.toString()));
      }
    }
    return objectMapper.writeValueAsString(node);
  }

  RuntimeFeatureAudience read(String json) {
    var node = objectMapper.readTree(json);
    return switch (node.required("mode").asString()) {
      case "NONE" -> new RuntimeFeatureAudience.None();
      case "ALL" -> new RuntimeFeatureAudience.All();
      case "USER_GROUPS" -> {
        var userGroupIds = new TreeSet<UUID>();
        node.required("userGroupIds")
            .forEach(idNode -> userGroupIds.add(UUID.fromString(idNode.asString())));
        yield new RuntimeFeatureAudience.UserGroups(userGroupIds);
      }
      default ->
          throw new IllegalStateException("Unknown persisted runtime feature audience: " + json);
    };
  }
}

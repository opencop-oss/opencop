// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.appmetadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.api.general.technical.endpoint.runtimefeature.RuntimeFeatureRecordResponse;
import jakarta.annotation.Nullable;
import java.util.List;

/** Response for /v1/app-metadata. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppMetadataResponse(
    @Nullable String appVersion, List<FeatureNode> features, @Nullable AdminNode admin) {

  public record FeatureNode(String name, boolean enabled) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AdminNode(
      @Nullable BffNode bff, @Nullable List<RuntimeFeatureRecordResponse> runtimeFeatures) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record BffNode(
      @Nullable String revision, @Nullable String branch, @Nullable String buildDate) {}
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Name-only registration request.
 *
 * <p>There is deliberately no consumer-class or audience property, and none is needed to keep the
 * caller from escalating: the endpoint passes only the name across the business boundary, and
 * consumer class, audience, and revision are supplied by the server. An extra property therefore
 * cannot influence the registered state, so unsupported fields are ignored rather than rejected.
 *
 * <p>The schema still advertises the supported contract as name-only even though the service
 * tolerates an unadvertised superset at runtime.
 */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class RuntimeFeatureRegisterRequest {

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "Lowercase wire name matching ^[a-z0-9._-]+$, at most 64 characters.",
      example = "themes-page")
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

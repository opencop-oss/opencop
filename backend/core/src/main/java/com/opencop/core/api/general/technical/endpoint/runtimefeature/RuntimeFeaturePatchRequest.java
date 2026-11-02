// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class RuntimeFeaturePatchRequest {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Audience audience;

  @Schema(minimum = "0", description = "Optional revision guard; omit for last-write-wins.")
  private Long expectedRevision;

  private final Map<String, Object> unknownProperties = new LinkedHashMap<>();

  public Audience getAudience() {
    return audience;
  }

  public void setAudience(Audience audience) {
    this.audience = audience;
  }

  public Long getExpectedRevision() {
    return expectedRevision;
  }

  public void setExpectedRevision(Long expectedRevision) {
    this.expectedRevision = expectedRevision;
  }

  // Package-private: read by the mapper but not a bean property, so the capture
  // map stays out of the generated OpenAPI schema.
  Map<String, Object> getUnknownProperties() {
    return Map.copyOf(unknownProperties);
  }

  @JsonAnySetter
  public void captureUnknownProperty(String name, Object value) {
    unknownProperties.put(name, value);
  }

  @Schema(
      oneOf = {NoneAudienceSchema.class, AllAudienceSchema.class, UserGroupsAudienceSchema.class},
      discriminatorProperty = "mode",
      discriminatorMapping = {
        @DiscriminatorMapping(value = "NONE", schema = NoneAudienceSchema.class),
        @DiscriminatorMapping(value = "ALL", schema = AllAudienceSchema.class),
        @DiscriminatorMapping(value = "USER_GROUPS", schema = UserGroupsAudienceSchema.class)
      },
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public static class Audience {

    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        allowableValues = {"NONE", "ALL", "USER_GROUPS"})
    private String mode;

    private List<UUID> userGroupIds;

    private boolean userGroupIdsPresent;

    private final Map<String, Object> unknownProperties = new LinkedHashMap<>();

    public String getMode() {
      return mode;
    }

    public void setMode(String mode) {
      this.mode = mode;
    }

    public List<UUID> getUserGroupIds() {
      return userGroupIds;
    }

    public void setUserGroupIds(List<UUID> userGroupIds) {
      this.userGroupIdsPresent = true;
      this.userGroupIds = userGroupIds;
    }

    boolean isUserGroupIdsPresent() {
      return userGroupIdsPresent;
    }

    // Package-private: read by the mapper but not a bean property, so the capture
    // map stays out of the generated OpenAPI schema.
    Map<String, Object> getUnknownProperties() {
      return Map.copyOf(unknownProperties);
    }

    @JsonAnySetter
    public void captureUnknownProperty(String name, Object value) {
      unknownProperties.put(name, value);
    }
  }

  // OpenAPI-only variants keep the generated request contract exact without changing Jackson's
  // local invalid-request mapping for missing, null, or unknown audience fields.
  @Schema(
      name = "RuntimeFeatureNoneAudience",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record NoneAudienceSchema(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "NONE") String mode) {}

  @Schema(
      name = "RuntimeFeatureAllAudience",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record AllAudienceSchema(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "ALL") String mode) {}

  @Schema(
      name = "RuntimeFeatureUserGroupsAudience",
      requiredProperties = {"mode", "userGroupIds"},
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record UserGroupsAudienceSchema(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "USER_GROUPS")
          String mode,
      @ArraySchema(
              minItems = 1,
              maxItems = RuntimeFeatureAudience.UserGroups.MAX_USER_GROUP_IDS,
              uniqueItems = true,
              schema = @Schema(type = "string", format = "uuid"))
          List<UUID> userGroupIds) {}
}

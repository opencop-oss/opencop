// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "idp")
public class IdpRelationshipProperties {

  @NotBlank private String userIdClaim;
  private String audience = "";
  private Map<String, RelationshipConfig> allowed = new HashMap<>();

  public String getUserIdClaim() {
    return userIdClaim;
  }

  public void setUserIdClaim(String userIdClaim) {
    this.userIdClaim = userIdClaim;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public Map<String, RelationshipConfig> getAllowed() {
    return allowed;
  }

  public void setAllowed(Map<String, RelationshipConfig> allowed) {
    this.allowed = allowed;
  }

  public static class RelationshipConfig {
    private String group;
    private List<String> roles = List.of();

    public String getGroup() {
      return group;
    }

    public void setGroup(String group) {
      this.group = group;
    }

    public List<String> getRoles() {
      return roles;
    }

    public void setRoles(List<String> roles) {
      this.roles = roles;
    }
  }

  public List<RelationshipConfig> getRelationshipConfigs() {
    return List.copyOf(allowed.values());
  }
}

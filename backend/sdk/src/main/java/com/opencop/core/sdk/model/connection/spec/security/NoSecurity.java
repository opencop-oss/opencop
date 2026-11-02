// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;

@JsonTypeName("none")
public record NoSecurity() implements TcpSecurity {

  @Override
  @JsonIgnore
  public List<String> secretRefs() {
    return List.of();
  }
}

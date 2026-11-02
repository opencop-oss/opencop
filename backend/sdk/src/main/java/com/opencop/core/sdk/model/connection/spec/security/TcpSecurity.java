// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.security;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * The security a TCP connection runs with. Only the kinds a TCP connector can open are permitted,
 * so a stored TCP spec cannot name a security Connect has no connector for.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = NoSecurity.class, name = "none"),
  @JsonSubTypes.Type(value = MtlsConfig.class, name = "mtls")
})
public sealed interface TcpSecurity permits NoSecurity, MtlsConfig {

  /**
   * The refs of every stored secret this security reads, which the connection owns. A security that
   * gains a secret must name it here, or archiving and rotating the connection leave it behind.
   */
  List<String> secretRefs();
}

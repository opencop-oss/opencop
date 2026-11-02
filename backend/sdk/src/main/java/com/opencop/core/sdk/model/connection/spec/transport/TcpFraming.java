// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * How a TCP connection splits its byte stream into messages: a framing an integration provides in
 * code, or a delimiter stored inline on the connection.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = IntegrationTcpFraming.class, name = IntegrationTcpFraming.KIND),
  @JsonSubTypes.Type(value = CustomTcpFraming.class, name = CustomTcpFraming.KIND)
})
public sealed interface TcpFraming permits IntegrationTcpFraming, CustomTcpFraming {}

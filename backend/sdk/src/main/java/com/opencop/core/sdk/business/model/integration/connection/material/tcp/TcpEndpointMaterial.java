// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.tcp;

/** TCP destination material returned by a TCP connection extractor. */
public record TcpEndpointMaterial(String host, int port) {}

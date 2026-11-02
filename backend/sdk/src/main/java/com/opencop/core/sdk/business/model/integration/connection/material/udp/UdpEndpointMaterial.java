// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.udp;

/** The endpoint a UDP connection uses: its host and the port it listens on. */
public record UdpEndpointMaterial(String host, int port) {}

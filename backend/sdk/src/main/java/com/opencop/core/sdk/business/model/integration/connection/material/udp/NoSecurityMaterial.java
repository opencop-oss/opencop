// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.udp;

/** A plain UDP connection: no security material. */
public record NoSecurityMaterial() implements UdpSecurityMaterial {}

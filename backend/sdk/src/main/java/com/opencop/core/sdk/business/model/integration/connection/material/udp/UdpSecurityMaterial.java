// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.udp;

/**
 * The security material a UDP connection is created with. Each kind maps to one security a UDP
 * connection can run with; a new kind is added here and handled wherever these are switched on.
 */
public sealed interface UdpSecurityMaterial permits NoSecurityMaterial {}

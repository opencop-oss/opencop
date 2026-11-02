// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.tcp;

/** A plain TCP connection: no security material. */
public record NoSecurityMaterial() implements TcpSecurityMaterial {}

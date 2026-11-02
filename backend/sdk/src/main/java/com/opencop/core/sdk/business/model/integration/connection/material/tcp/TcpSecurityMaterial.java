// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.tcp;

/**
 * The security material a TCP connection is created with. Each kind maps to one security the
 * connection's spec can hold; a new kind is added here and handled wherever these are switched on.
 */
public sealed interface TcpSecurityMaterial permits NoSecurityMaterial, MtlsCredentialMaterial {}

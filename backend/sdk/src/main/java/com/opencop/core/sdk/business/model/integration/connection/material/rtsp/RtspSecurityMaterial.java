// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.rtsp;

/**
 * The security material an RTSP connection is created with. Each kind maps to one security an RTSP
 * connection can run with; a new kind is added here and handled wherever these are switched on.
 */
public sealed interface RtspSecurityMaterial permits NoSecurityMaterial {}

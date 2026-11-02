// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.rtsp;

import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.RtspSecurityMaterial;
import java.util.UUID;

/** Turns one kind of RTSP security material into what a new connection needs. */
public interface RtspSecurityProvisioner<M extends RtspSecurityMaterial> {

  ProvisionedRtspSecurity provision(UUID sourceId, M material);
}

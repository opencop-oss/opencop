// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import java.util.List;
import java.util.UUID;

/**
 * Gateway to Connect service for PUSH payload delivery.
 *
 * <p>START/STOP desired-state convergence is driven by Postgres truth plus Postgres nudges; Redis
 * remains a payload transport for PUSH.
 */
public interface ConnectService {

  RuntimeStatus pushOnInstance(UUID sourceId, List<byte[]> data, String instanceId)
      throws ConnectServiceException;
}

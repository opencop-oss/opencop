// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.rtsp;

import java.util.Optional;

/**
 * What a RTSP connection's security material became once provisioned. A RTSP spec stores no
 * security yet, so only what provisioning stored outside the database is carried.
 *
 * @param cleanup deletes what provisioning stored outside the database, should the connection fail
 *     to persist; empty when it stored nothing
 */
public record ProvisionedRtspSecurity(Optional<Runnable> cleanup) {}

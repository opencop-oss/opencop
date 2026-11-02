// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.tcp;

import com.opencop.core.api.general.business.model.connection.TcpTimeouts;
import com.opencop.core.sdk.model.connection.spec.security.TcpSecurity;
import java.util.Optional;

/**
 * What a TCP connection's security material became once provisioned.
 *
 * @param security the security the connection's spec stores
 * @param timeouts the timeouts this security runs with
 * @param cleanup deletes what provisioning stored outside the database, should the connection fail
 *     to persist; empty when it stored nothing
 */
public record ProvisionedTcpSecurity(
    TcpSecurity security, TcpTimeouts timeouts, Optional<Runnable> cleanup) {}

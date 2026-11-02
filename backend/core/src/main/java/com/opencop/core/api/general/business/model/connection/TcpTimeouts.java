// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection;

/** How long a TCP connection waits to connect and between reads, in milliseconds. */
public record TcpTimeouts(int connectTimeoutMs, int readTimeoutMs) {

  public static final TcpTimeouts DEFAULT = new TcpTimeouts(3000, 15000);
}

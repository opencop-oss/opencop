// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.SocketDataSource;

/**
 * Identifies preserved socket rows whose stored access cannot be honored by their transport.
 *
 * <p>Cleanup status: {@code targeted}. TODO: Remove this compatibility policy once all deployed
 * versions have been fixed. It remains in place to fail closed for legacy incompatible socket rows.
 */
public final class LegacySocketAccessModePolicy {

  private LegacySocketAccessModePolicy() {}

  public static boolean isIncompatible(SocketDataSource source) {
    return source.access().canWrite()
        && (source.provider() == TransportKind.UDP || source.provider() == TransportKind.RTSP);
  }
}

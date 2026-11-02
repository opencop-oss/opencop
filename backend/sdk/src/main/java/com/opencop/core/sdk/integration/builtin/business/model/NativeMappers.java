// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.model;

import java.util.UUID;

/**
 * Identities of the mappers Native ships. A connection stores a mapper's identity, so these are
 * contract values that never change.
 */
public final class NativeMappers {

  /** Decodes MISB ST 0601 KLV metadata, the only payload an RTSP connection carries. */
  public static final UUID KLV_INGRESS = UUID.fromString("d8ba1624-ccd2-4bca-8d4b-241b38f246c4");

  private NativeMappers() {}
}

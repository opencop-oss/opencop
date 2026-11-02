// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam;

/**
 * Visibility of an imagery / weather tile source.
 *
 * <p>{@code PRIVATE} tiles are reachable only through an explicit grant. {@code GLOBAL} tiles are
 * reachable by everyone, so no explicit grant is needed when no grant applies.
 */
public enum MapTileAccessScope {
  PRIVATE,
  GLOBAL
}

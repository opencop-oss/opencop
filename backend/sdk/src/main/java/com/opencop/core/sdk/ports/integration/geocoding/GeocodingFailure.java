// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.geocoding;

/** Stable host-facing failure families for a geocoding provider call. */
public enum GeocodingFailure {
  TIMEOUT,
  UNAVAILABLE,
  RATE_LIMITED,
  MISCONFIGURED,
  BAD_RESPONSE
}

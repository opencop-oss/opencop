// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

/** Ingress-mapper-owned evidence about whether a decoded payload was expected to emit features. */
public enum DecodedOutputDisposition {
  EMITTED,
  ACCEPTED_NO_OUTPUT,
  INVALID_MAPPING,
  NO_OUTPUT_UNCLASSIFIED
}

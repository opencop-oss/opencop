// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.profile;

/** The direction a mapper works in. */
public enum MapperQualifier {
  /** Decodes what a connection reads into features. */
  INGRESS,
  /** Encodes features into what a connection writes. */
  EGRESS
}

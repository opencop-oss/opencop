// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.capability;

/** Provider-neutral integration capability identifiers used in explicit host policies. */
public enum IntegrationCapability {
  INTEGRATION_IDENTITY,
  CONNECTION_SCHEMA,
  INGRESS_MAPPING,
  EGRESS_MAPPING,
  CONNECTION_BEHAVIOR,
  CONNECTION_INPUT,
  CONNECTION_MAPPING,
  GEOCODING,
  /**
   * Per-connection settings an integration declares, with their deployment defaults, which the host
   * validates and resolves into the value its connection behavior is planned from.
   */
  CONNECTION_SETTINGS,
  /**
   * HTTP operations an integration adds to the connections of one of its schema versions, which the
   * host publishes, authorizes and dispatches.
   */
  CONNECTION_EXTENSION
}

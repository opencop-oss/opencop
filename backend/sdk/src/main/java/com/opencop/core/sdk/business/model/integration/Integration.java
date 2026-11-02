// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration;

import java.util.UUID;

/**
 * An integration a plugin provides, such as TAK or Native: its stable identity and display
 * metadata. Every plugin publishes exactly one per integration.
 *
 * <p>An integration that sets up connections also publishes the versions of its connection contract
 * as {@link IntegrationConnectionSchema}s; an integration that only contributes mappers publishes
 * none.
 */
public interface Integration {

  UUID id();

  String name();

  String description();
}

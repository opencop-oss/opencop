// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.startup;

import org.springframework.core.Ordered;

/**
 * The order Core's startup runners run in. Spring Boot reports Core ready to accept traffic only
 * once every runner has finished, and a runner that throws fails startup, so each step can rely on
 * the ones before it having completed.
 */
public final class StartupOrder {

  /** Registers every integration, which must exist before a mapper can name its owner. */
  public static final int INTEGRATION_CATALOG = Ordered.HIGHEST_PRECEDENCE;

  /** Registers the ingress and egress mappers integrations contribute. */
  public static final int MAPPER_REGISTRATION = INTEGRATION_CATALOG + 1;

  private StartupOrder() {}
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.integration.support;

import org.junit.jupiter.api.Tag;

/**
 * Marker base class for environment-backed integration tests.
 *
 * <p>These tests are reserved for real external dependencies and are executed through the dedicated
 * Maven profile `environment-integration-tests`.
 */
@Tag("environment-integration")
public abstract class EnvironmentIntegrationIT {}

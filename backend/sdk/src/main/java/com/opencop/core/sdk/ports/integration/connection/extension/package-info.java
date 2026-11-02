// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Contract through which an integration adds HTTP operations to the connections of one of its
 * schema versions, without the host knowing the integration. The host publishes, authorizes and
 * dispatches every declared operation; integrations never contribute controllers.
 */
package com.opencop.core.sdk.ports.integration.connection.extension;

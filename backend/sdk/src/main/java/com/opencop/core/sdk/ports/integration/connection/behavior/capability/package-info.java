// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Focused trigger interfaces for connection behavior contributions.
 *
 * <p>On-connect actions are ordered and sequential; recurring actions are independently
 * fixed-delay; receive-idle message and reconnect actions are singular per connection schema
 * version. Implementations may combine trigger interfaces only when they represent one cohesive
 * behavior intent.
 */
package com.opencop.core.sdk.ports.integration.connection.behavior.capability;

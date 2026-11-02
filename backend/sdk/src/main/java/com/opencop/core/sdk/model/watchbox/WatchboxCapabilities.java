// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/**
 * Caller-specific management capabilities for one watchbox, derived from the same effective-access
 * policy the PATCH and DELETE endpoints enforce (global-admin bypass, or OWNER/ADMIN on every
 * selected layer). Values reflect current-object management authorization only: payload- and
 * state-dependent PATCH validation still applies at request time, and the endpoints remain
 * authoritative if access or state changes after a response was read.
 */
public record WatchboxCapabilities(boolean canPatch, boolean canDelete) {}

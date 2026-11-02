// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Message-production interfaces shared by connection trigger capabilities.
 *
 * <p>Factories build fresh bytes for every attempt and may report expected construction or
 * serialization failures. They never own sockets, scheduling, retries, or mutable connection state.
 */
package com.opencop.core.sdk.ports.integration.connection.behavior.message;

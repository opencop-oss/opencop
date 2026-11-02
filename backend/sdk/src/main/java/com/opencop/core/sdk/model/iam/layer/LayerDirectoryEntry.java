// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.layer;

import java.time.Instant;
import java.util.UUID;

/**
 * A single row in the combined layer/layer-group directory grid. {@code archived} is always false
 * for layer groups (they have no lifecycle). {@code accessScope} is the string label of the
 * (per-type) access-scope enum.
 */
public record LayerDirectoryEntry(
    UUID id,
    LayerDirectoryType type,
    String name,
    String accessScope,
    boolean archived,
    Instant createdAt,
    Instant updatedAt,
    Instant lastEventAt) {}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.layer;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Supplies layer activity to the layer reads that serve it, keeping the lookup out of {@code
 * LayerEntity} itself.
 *
 * <p>Layers absent from the returned map have no recorded activity, which is how "never active"
 * reaches a client as null rather than as a sentinel.
 */
@FunctionalInterface
public interface LayerActivityReader {

  Map<UUID, Instant> findAll(Collection<UUID> layerIds);
}

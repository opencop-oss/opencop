// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper;

import com.opencop.core.sdk.technical.gateway.extractor.KmzIconRef;
import java.util.List;
import java.util.UUID;

/**
 * Context supplied to mapper execution.
 *
 * <p>{@code sourceId} identifies the namespace that will own mapped features, not necessarily a
 * data-source row. Real-time ingestion supplies a data-source ID, layer import supplies the target
 * layer ID, and mapper preview may supply {@code null}. Callers must establish the ingestion
 * context before resolving or enriching this value as a particular resource kind.
 */
public record MappingProperties(
    UUID sourceId, UUID importId, boolean preview, List<KmzIconRef> iconManifest) {

  public MappingProperties(UUID sourceId, UUID importId, boolean preview) {
    this(sourceId, importId, preview, List.of());
  }
}

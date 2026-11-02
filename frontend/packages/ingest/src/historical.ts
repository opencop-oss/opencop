// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { neverStale, type Element } from "@opencop/elements";
import type { ElementMessage } from "@opencop/proto";
import type { MessageProcessor } from "./MessageProcessor";

/**
 * Decode an `ElementMessage` for historical ingestion.
 *
 * Passes `elementClass: "historical"` to the processor so the Element
 * constructor keys by `source + id` (per-version UUID) instead of
 * `source + objectId`. This lets every chunk's version of an entity coexist
 * in the shared {@link ElementStore} without dedup, which is what enables
 * playhead scrubbing. `elementClass` also exempts them from stale eviction, so
 * they coexist with live elements that are still swept normally.
 *
 * The `chunkIndex` is composed into the element id as `"<uuid>:<chunkIndex>"`.
 * This chunk-scoped key is what allows multiple chunks that carry the same
 * proto id (e.g. a long-lived entity repeated across chunks) to coexist as
 * distinct entries in the store — each chunk's copy has a unique key.
 *
 * Overrides `stale` to `neverStale` so a downstream consumer that inspects
 * the element without historical context still treats it as non-stale.
 *
 * Binary-layer tagging (e.g. `Element#setFilterValue` for GPU playhead
 * filtering) is a rendering concern and should be applied by the caller —
 * it does not belong in the ingest layer.
 */
export function decodeHistoricalElement(
    message: ElementMessage,
    processor: MessageProcessor,
    chunkIndex: number,
): Element | undefined {
    const element = processor.decode(message, {
        elementClass: "historical",
        idSuffix: String(chunkIndex),
    });
    if (!element) return undefined;
    element.setStale(neverStale);
    return element;
}

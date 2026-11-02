// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element, ElementMeta } from "@opencop/elements";
import type { ElementMessage } from "@opencop/proto";

// TODO: Rework Element model to POJO to eliminate mappers.
/**
 * Maps an `ElementMessage` to an `Element`.
 *
 * Mappers receive pre-computed `meta` from the `MessageProcessor` so that
 * cross-cutting concerns (like pipeline classification for historical mode)
 * are applied uniformly without every mapper having to thread options.
 */
export interface ElementMapper {
    fromMessage(message: ElementMessage, meta: Partial<ElementMeta>): Element;
}

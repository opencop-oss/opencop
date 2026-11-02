// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyLayerInfo } from "@opencop/api";
import type { Element, PartialOCFeature } from "@opencop/elements";
import { stampBroadcastTimestamps } from "~/features/events/broadcast-feature.util";
import { EDIT_ROLES } from "~/features/map/editability";

/**
 * Whether the backend would reject this element as a copy source: a layer create returns 400
 * (`STALE_EVENT`) unless `timestampz.stale` is still in the future. Elements that never go
 * stale carry a far-future stale time, so they pass.
 *
 * A guard, not a path users normally reach: the element store evicts an element about half a
 * second after its stale time, and the selection follows, so a stale element is only ever checked
 * in the sub-second window before that eviction. It stays because the backend rejects stale
 * creates and the cost of missing one is a failed request.
 */
export function isStaleForCopy(element: Pick<Element, "stale">, now: number): boolean {
    return element.stale <= now;
}

/**
 * Whether `element` already lives on `layerId`. Copying it there would only make a duplicate,
 * which is easy to do by accident, so those are skipped and counted instead.
 */
export function isOnLayer(element: Pick<Element, "source">, layerId: string): boolean {
    return element.source === layerId;
}

/**
 * The payload that creates a copy of `element` in `targetLayerId`.
 *
 * Only `source` is retargeted: the backend requires it to equal the target layer, and mints a new
 * `id` and `objectId` itself, so the element's own ids are sent unchanged and ignored. The
 * timestamps are normalized by `stampBroadcastTimestamps`.
 *
 * What a copy carries is what `Element.toFeature()` emits: geometry, display name, attributes,
 * timestamps (stale kept, time reset) and the element payload. It does not carry remarks or
 * contacts (separate per-event resources, not held on `Element`), attachments or streams, or the
 * original author, kind and version, which the backend sets for the new event.
 */
export function buildCopyFeature(
    element: Element,
    targetLayerId: string,
    now: number,
): PartialOCFeature {
    const feature = element.toFeature();
    const { opencop } = feature.properties;
    return stampBroadcastTimestamps(
        {
            ...feature,
            properties: { opencop: { ...opencop, source: targetLayerId } },
        },
        now,
    );
}

/**
 * Layers the user may copy into: those where they hold editor-or-higher, which is what the
 * backend requires of the target. `myLayers` only lists active layers, which the backend also
 * requires.
 */
export function getCopyTargetLayers(myLayers: readonly MyLayerInfo[]): MyLayerInfo[] {
    return myLayers.filter((layer) => EDIT_ROLES.has(layer.role));
}

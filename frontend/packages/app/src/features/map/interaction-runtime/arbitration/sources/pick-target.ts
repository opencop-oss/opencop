// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The PICK TARGET — what was under the pointer, as a pick response receives
 * it (`../pick-responses.ts`). The types are the contract in `@opencop/contributions`;
 * this module builds them from a deck pick. Opaque on purpose: a response is handed the id
 * the nearest non-element hit answers to and the object it was, plus the
 * elements beneath, and nothing of deck's `PickingInfo` — not the layer, not
 * the sublayer, not the pixel.
 *
 * Identity comes from the scene: the host decodes the nearest primitive's id
 * into the ref of the object it was drawn for (`DeckPick.ref`), and the hit
 * answers to that object's store. A pickable layer outside the scene answers
 * with no id, so a click on it is not dispatched to a response. Nothing here is
 * written against a deck layer id.
 */
import type { Hit, PickTarget, StorePickTarget } from "@opencop/contributions";
import type { Element } from "@opencop/elements";
import type { DeckPick } from "../../contract";

export type { Hit, PickTarget, StorePickTarget } from "@opencop/contributions";

/** Whether the target's nearest hit was drawn for a store's object. */
export function isStorePick(target: PickTarget): target is StorePickTarget {
    return target.hit?.responseId != null;
}

/** The adapter: a deck pick, and the element the host resolved for its nearest hit, as a target. */
export function pickTargetOf(info: DeckPick, element: Element | undefined): PickTarget {
    return {
        screen: { x: info.picking.x, y: info.picking.y },
        hit: !element && info.picking.layer ? hitOf(info) : null,
        elements: info.picks,
    };
}

function hitOf({ ref }: DeckPick): Hit {
    return ref ? { responseId: ref.type, ref } : { responseId: null, ref: null };
}

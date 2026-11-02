// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Element, TextBox, type DraftObject } from "@opencop/elements";
import {
    kits,
    MercatorVector,
    positionPairs,
    type ControlPoint,
    type EditModel,
} from "@opencop/tools";
import { boxFrame } from "./box-geometry.util";
import { growToHoldText } from "./box-text.util";

type Corner = [lng: number, lat: number];

const cornersOf = (flat: readonly number[]): Corner[] => positionPairs(flat).slice(0, 4);

const rectangle = kits.rectangle.editModel as EditModel;

/** Which side of the opposite corner `corner` lies on, measured against `toward`. */
function liesToward(corner: Corner, opposite: Corner, toward: MercatorVector): boolean {
    const offset = MercatorVector.fromLngLat(corner).subtract(MercatorVector.fromLngLat(opposite));
    return offset.dot(toward) > 0;
}

/**
 * The rectangle's corner drag, with two things the textbox needs on top.
 *
 * **Every corner keeps the slot it had.** The host model rebuilds the ring from
 * the corner opposite the dragged one and then rewinds it, so which slot holds
 * which corner changes with the drag. The textbox reads slots 0 and 1 as the
 * box's first edge — that is the edge the text runs along and is measured by —
 * so an unordered ring turns the text by 90° mid-drag.
 *
 * **The box re-grows to hold its text.** Narrowing a box gives the text fewer
 * characters per line and so more lines; without this the extra lines fall
 * outside the box and overflow it until the next keystroke. The drag is only a
 * floor: once the text needs more room, a dragged corner on the far edge stays
 * off the pointer, and a drag on the first edge moves the far edge with it.
 * Holding the opposite corner fixed instead would grow the box away from the
 * dragged corner, which moves the first edge the text is measured along.
 *
 * **A drag that would degenerate the ring is refused.** Dropping a corner
 * exactly on the first edge's line collapses the ring to three vertices, and
 * the host model then ignores every later drag on it, so the box can no longer
 * be repaired. The pre-drag ring is restored rather than written.
 */
export const textBoxEditModel: EditModel = {
    ...rectangle,

    applyHandle(
        box: DraftObject,
        controlPoint: ControlPoint,
        lngLat: [number, number],
        snapshot?: readonly number[],
    ) {
        const start = [...(snapshot ?? box.geometry.getAllPos())];
        rectangle.applyHandle(box, controlPoint, lngLat, snapshot);

        // Only a textbox cares which slot a corner sits in; anything else the
        // host hands us keeps the rectangle's own result.
        if (!(box instanceof Element) || !TextBox.is(box)) return;

        // Already short of four corners: nothing to reorder, and no ring worth
        // restoring to.
        if (start.length < 8) return;

        const restore = () => {
            box.geometry.setAllPos(start);
        };
        if (box.geometry.getAllPos().length < 8) {
            restore();
            return;
        }

        const before = cornersOf(start);
        const after = cornersOf(box.geometry.getAllPos());

        // The host writes the dragged corner verbatim, so it is found exactly.
        // If that ever stops holding, leave the ring as the host left it rather
        // than reordering it on a guess.
        const dragged = after.findIndex(([lng, lat]) => lng === lngLat[0] && lat === lngLat[1]);
        if (dragged < 0) return;

        const slot = controlPoint.index ?? 0;
        const opposite = after[(dragged + 2) % 4];
        const [one, other] = [after[(dragged + 1) % 4], after[(dragged + 3) % 4]];

        // In the ring as it was, the corner after the dragged one lay this way
        // off the opposite corner. Whichever neighbour still does takes its slot.
        const toward = MercatorVector.fromLngLat(before[(slot + 1) % 4]).subtract(
            MercatorVector.fromLngLat(before[(slot + 2) % 4]),
        );
        const following = liesToward(one, opposite, toward) ? one : other;
        const preceding = following === one ? other : one;

        const ring: Corner[] = [];
        ring[slot] = after[dragged];
        ring[(slot + 1) % 4] = following;
        ring[(slot + 2) % 4] = opposite;
        ring[(slot + 3) % 4] = preceding;

        box.geometry.setAllPos(ring.flat());
        growToHoldText(box);

        // A near-degenerate drop can still leave the reordered ring with a
        // NaN corner or no width to measure text against.
        const end = box.geometry.getAllPos();
        if (end.length < 8 || !end.every((value) => Number.isFinite(value)) || !boxFrame(end)) {
            restore();
        }
    },
};

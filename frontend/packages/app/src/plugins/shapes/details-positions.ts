// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element, Position2D } from "@opencop/elements";
import type { DetailsPosition } from "@opencop/contributions";
import { mercatorMidpoint } from "@opencop/utils";

/**
 * The positions the details panel shows for shapes whose geometry is not a
 * single point.
 */

/** An arrow's middle, as it appears on the map. */
export function arrowPosition(element: Element): DetailsPosition | null {
    if (element.geometry.numPositions < 2) return null;
    return {
        position: mercatorMidpoint(
            element.geometry.getPos(0) as Position2D,
            element.geometry.getPos(1) as Position2D,
        ),
        labelKey: "events.fieldCentralLocation",
    };
}

/** A circle's or an ellipse's centre. */
export function centerPosition(element: Element): DetailsPosition | null {
    if (element.geometry.numPositions < 1) return null;
    const [lng, lat] = element.geometry.getPos(0);
    return { position: [lng, lat], labelKey: "events.fieldCenterPoint" };
}

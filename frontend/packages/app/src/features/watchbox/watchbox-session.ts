// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import {
    useInteractionRev,
    useSessionProductOf,
} from "~/features/map/interaction-runtime/session/selectors";
import { polygonFromPositions } from "./polygon.model";
import type { WatchboxPolygon } from "./watchbox.model";

/**
 * How watchbox's UI reads its session — through the runtime's public selectors,
 * narrowed to this descriptor. The element is mutated in place by the draw and
 * drag handlers; anything rendering its geometry selects the `rev` fence too.
 */

/**
 * Defined here, not on the descriptor, so the panel can name the tool without
 * importing the descriptor it is registered from.
 */
export const WATCHBOX_TOOL_ID = "watchboxTool";

/**
 * The FINISHED polygon as GeoJSON — the form's input — or null while drawing
 * or idle. Recomputed on every geometry mutation, so the form follows a drag.
 */
export function useWatchboxPolygon(): WatchboxPolygon | null {
    const product = useSessionProductOf(WATCHBOX_TOOL_ID);
    const rev = useInteractionRev();
    return useMemo(() => {
        void rev; // geometry is mutable; the fence forces recompute on vertex moves
        return product ? polygonFromPositions(product.geometry.getAllPos() as number[]) : null;
    }, [product, rev]);
}

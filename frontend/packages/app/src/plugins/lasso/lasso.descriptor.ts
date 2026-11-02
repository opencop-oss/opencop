// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as turf from "@turf/turf";
import { PiLasso } from "react-icons/pi";
import {
    INTERACTION_TOOL_GROUP,
    createTraceGrammar,
    positionPairs,
    type ToolDescriptor,
    SESSION_STORE_ID,
} from "@opencop/tools";
import { LASSO_PRODUCT_TYPE, lassoTrace } from "./lasso-trace.model";
import { lassoProductPrimitives } from "./lasso.mapper";

export const LASSO_TOOL_ID = "lassoTool";

const LASSO_CURSOR = 'url("/lasso-cursor.svg") 8 8, crosshair';

/**
 * The lasso: a freehand trace whose completion hands the enclosed elements to
 * the host as a selection.
 *
 * The tool keeps nothing — no store write, no panel, no layer. Its trace is a
 * {@link LassoTrace} drafted by the built-in trace grammar, a product of the
 * host's session store, drawn while it is laid down by `lassoRenderer`. It reaches the host through the one
 * `CompletionHost.selectElements` call, and everything that happens next (the
 * Events panel list, export, pruning on delete, the ring on the map) is the
 * selection's own business. That separation is what would let the descriptor
 * move to a plugin unchanged: it imports the leaf packages and one host constant
 * for the toolbar group it wants to sit in.
 *
 * Escape and mid-trace right-click both take the host's trace defaults (abort
 * the ring and stay armed; then leave), so no key is declared.
 */
export const lassoDescriptor: ToolDescriptor = {
    id: LASSO_TOOL_ID,
    label: "lasso",
    labelKey: "toolbar.lasso",
    icon: PiLasso,
    location: "topBarStart",
    group: INTERACTION_TOOL_GROUP,
    order: 10,
    draw: { grammar: createTraceGrammar({ create: lassoTrace }) },
    produces: { store: SESSION_STORE_ID, type: LASSO_PRODUCT_TYPE },
    mapProduct: lassoProductPrimitives,
    cursor: LASSO_CURSOR,

    // The previous selection is replaced HERE, on completion, not when the next
    // trace begins: there is no start hook, and a trace that is abandoned leaves
    // the earlier selection untouched.
    onComplete: (product, host) => {
        const vertices = positionPairs(product.geometry.getAllPos());
        if (vertices.length < 3) return "re-arm";

        const simplified = turf
            .simplify(turf.polygon([[...vertices, vertices[0]]]), {
                tolerance: 0.0001,
                highQuality: false,
            })
            .geometry.coordinates[0].slice(0, -1) as [number, number][];

        const matched = host.pickInsidePolygon(simplified.length >= 3 ? simplified : vertices);
        if (matched.length === 0) {
            // An empty hand-over clears the previous selection and re-arms, so a
            // miss reads as "nothing here" rather than leaving a stale ring.
            host.selectElements([], []);
            return "re-arm";
        }

        host.selectElements(matched, vertices);
        return "exit";
    },
};

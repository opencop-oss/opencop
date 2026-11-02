// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { v4 as uuid } from "uuid";
import { PolygonGeometry, type DraftObject, type Position2D } from "@opencop/elements";

/** The product type a lasso trace is drawn as, in the host's session store. */
export const LASSO_PRODUCT_TYPE = "lasso:trace";

/**
 * A lasso trace as the lasso drafts it: the ring laid down so far, under the
 * lasso's product type. Not an element — a trace is never stored and has no
 * source, symbol or style; it exists only until it becomes a selection.
 */
export interface LassoTrace extends DraftObject {
    readonly type: typeof LASSO_PRODUCT_TYPE;
    readonly geometry: PolygonGeometry;
}

/** A trace started at `cursor`. */
export function lassoTrace(cursor: Position2D): LassoTrace {
    return { key: uuid(), type: LASSO_PRODUCT_TYPE, geometry: new PolygonGeometry([...cursor], 2) };
}

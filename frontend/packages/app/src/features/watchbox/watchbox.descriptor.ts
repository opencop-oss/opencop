// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { PiPolygon } from "react-icons/pi";
import { kits, type DrawHint, type DrawHintState, type ToolDescriptor } from "@opencop/tools";
import { WATCHBOX_TOOL_ID } from "./watchbox-session";
import { WATCHBOX_STORE_ID, WATCHBOX_TYPE } from "./watchbox.store";

export { WATCHBOX_TOOL_ID };

/**
 * The polygon's own draw hints while placing, and none once the polygon is
 * finished: the watchbox form takes over then.
 */
function watchboxHints(state: DrawHintState, defaults: readonly DrawHint[]): readonly DrawHint[] {
    return state.phase === "editing" ? [] : defaults;
}

/**
 * The watchbox tool as ONE descriptor: a polygon draw whose product is handed
 * to the watchbox panel instead of the element store.
 *
 * It produces into the watchbox store, not the element store, so the runtime
 * holds no custody — the finished polygon is an EDIT session over a
 * session-local product (exactly like a finished measurement) that the panel
 * reads and the form saves. The host draws the polygon with the watchbox renderer,
 * exactly as a saved watchbox looks; it never enters the store until saved. Everything else is
 * the runtime's default: crosshair while drawing, double-click or Enter to
 * finish, right-click to undo a vertex, the draft completing into the edit
 * session, generic vertex + body drag while adjusting, refusal in historical
 * mode, Escape mid-draw dropping the vertices while staying armed, and Escape
 * over the finished polygon cancelling the edit — which, with no custody,
 * discards the polygon and leaves the tool (and with it the form), what the
 * shipped tool did and what a form about to be abandoned should do. The one
 * interaction field it declares is its kit's `hints` — no edit instructions once finished.
 */
export const watchboxDescriptor: ToolDescriptor = {
    id: WATCHBOX_TOOL_ID,
    label: "watchbox",
    icon: PiPolygon,
    location: "external",
    produces: { store: WATCHBOX_STORE_ID, type: WATCHBOX_TYPE },
    draw: { ...kits.polygon, hints: watchboxHints },
};

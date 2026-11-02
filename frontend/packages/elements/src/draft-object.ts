// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Geometry } from "./geometry/Geometry.ts";

/**
 * An object on the map with a live geometry — what a draw grammar draws into and
 * an edit model edits. An `Element` is one. What a tool drafts follows the store
 * its product belongs to: an `Element` for the element store, and for the session
 * store its own object with the tool's `produces.type` as its `type`.
 *
 * The geometry is mutated in place while the operator draws and drags, and each
 * change is announced through its `Reactive` signal — that signal, not a new
 * object, is how the host knows to redraw a draft.
 */
export interface DraftObject {
    /** Unique among drafts. */
    readonly key: string;
    /** The kind of object; for a session product, the tool's `produces.type`. */
    readonly type: string;
    readonly geometry: Geometry;
}

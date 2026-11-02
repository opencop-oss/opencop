// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DraftObject } from "@opencop/elements";
import type { ToolParams } from "./tool";

/**
 * Where in its lifecycle a tool's session currently is, as an editor sees it.
 *
 * Every tool passes `draw` → `edit`: `draw` from activation until the product
 * is finished (waiting for the first click included — `draft` is `null`
 * until the draft exists), `edit` while the finished product is held open for
 * adjustment. A product that is not an element goes through the same two
 * phases as a shape; whether the host stores the product is not something an
 * editor can see, and does not need to.
 */
export type ToolPhase = "idle" | "draw" | "edit";

/**
 * Everything a tool's editor needs, and nothing else.
 *
 * The **editor contract**: a `ToolDescriptor.editor` renders against this instead of
 * reaching into the host's tool store, actions, or map manager. It lives here, in a
 * leaf, so a native subject and a plugin write the same editor; the host supplies
 * the implementation through a `ToolSessionScope`, read with `useToolSession()`.
 *
 * **Scoped to one tool.** Every member speaks for the tool the scope was opened
 * for. While another tool owns the session, the view reads as idle and its
 * commands do nothing — `activate` included, so an editor cannot preempt another
 * tool. Each command checks ownership when it runs, so one kept in a closure
 * cannot reach a later session.
 *
 * The scope is a plain provider, so a later surface can open one for its owner's
 * tool outside the editor outlet, and a `save()` member waits for its first
 * consumer.
 *
 * A hook-shaped contract rather than pushed props because editors already subscribe:
 * pushing props would discard that and force re-inventing a change signal for the
 * same updates.
 */
export interface ToolSession {
    /**
     * The product being drawn or edited — whatever the tool's grammar drafted — or
     * `null` when idle. Always an element for a tool that produces into the
     * element store; for any other tool, the grammar's own draft, which may also
     * be an element. An editor that needs an element narrows it (`instanceof Element`).
     */
    readonly draft: DraftObject | null;
    /** The tool's opaque, tool-owned parameters; empty when idle. */
    readonly params: ToolParams;
    /** Editors branch on this rather than on the host's internal mode enum. */
    readonly phase: ToolPhase;
    /** Replace the tool's params (e.g. a modifier changed). Does nothing while idle. */
    setParams(params: ToolParams): void;
    /**
     * Tell the map a draft's rendering is stale after mutating it in place.
     * `attribute` names what changed, so only the layers reading it rebuild
     * (`"all"` for anything).
     */
    invalidate(draft: DraftObject, attribute: string): void;
    /**
     * Start drawing with this tool, with `params` when given — when no session runs,
     * or this tool's own does. Does nothing while another tool owns the session.
     */
    activate(params?: ToolParams): void;
    /** Abandon this tool's draw or edit. Does nothing while idle. */
    cancel(): void;
}

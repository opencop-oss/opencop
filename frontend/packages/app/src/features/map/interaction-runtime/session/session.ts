// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The interaction SESSION SHAPES — the union the store holds, the runner keys
 * on, and every derivation reads. Types and pure helpers only; the store
 * instance (a module-identity-sensitive singleton) stays app-side in
 * `./interaction.store`.
 *
 * HOST-INTERNAL by design. A tool never sees these shapes: its editor reads
 * the deliberately narrower `ToolSession` (`@opencop/tools`). Kept pure (leaf
 * packages only, lint-enforced) so the derivations built on it are testable
 * without React, a store, or a browser — not because it is destined for a
 * package.
 */
import type { DraftObject, Element, ElementRef } from "@opencop/elements";
import type { ToolParams } from "@opencop/tools";
import type { DescriptorResolver } from "../contract";

/** Edit-session hover/drag substate — drives the cursor derivation; never rebinds. */
export interface EditSub {
    readonly hotspot: "handle" | "boundary" | "body" | null;
    readonly dragging: "handle" | "boundary" | "translate" | null;
}

export interface IdleSession {
    readonly kind: "idle";
}

/**
 * The in-flight object of a draw session — whatever the tool's grammar drafts:
 * an element for a tool that produces into the element store, the tool's own
 * draft object otherwise. `placed` = the first confirmed click has happened;
 * under custody that is the moment the element entered the store and the
 * pipeline locked it.
 */
export interface Draft<T extends DraftObject = DraftObject> {
    readonly object: T;
    readonly placed: boolean;
}

/**
 * A tool's session from activation until its product is finished — every
 * tool's, whatever the product: a shape about to enter the store, a
 * measurement, a selection lasso's ring, a watchbox polygon. `draft === null`
 * is the tool waiting for its first input (the toolbar button pressed, nothing
 * on the map yet); a draft that is placed or has locked vertices is IN FLIGHT
 * ({@link isInFlight}). Neither is a phase of its own — the two differ only in
 * what the operator has done so far, and every rule that cares reads the
 * predicate.
 */
export type DrawSession = DrawSessionBase &
    (
        | {
              /**
               * This session takes custody of its draft at placement — decided once,
               * at activation, from the descriptor (see `custody.ts`), and carried here
               * so the session can release what it holds even after its descriptor is
               * deregistered. Custody is the element store's — no other store takes
               * it — so a custody draft is always an `Element`: the commands check
               * each one and admit no other into the element store.
               */
              readonly custody: true;
              readonly draft: Draft<Element> | null;
          }
        | { readonly custody: false; readonly draft: Draft | null }
    );

interface DrawSessionBase {
    readonly kind: "draw";
    readonly descriptorId: string;
    /**
     * Binding identity — bumps only when listeners must rebind: activation, a
     * params restart, a re-arm. A draft appearing, a vertex locking or a
     * trace's press never bump it: a gesture must not rebind mid-flight, so
     * the handlers bound at activation carry it through to completion.
     */
    readonly epoch: number;
    readonly params: ToolParams;
    /** Confirmed vertices/phases — substate, never rebinds. */
    readonly locked: number;
}

/**
 * A finished product held open for adjustment — a just-drawn shape, an
 * existing element the operator is editing, or a completed measurement.
 *
 * `custody` is what separates the two: with custody the element is an
 * ElementStore citizen the session has locked against WebSocket overwrites
 * (`pipeline.edit`), and leaving the session commits or rolls it back;
 * without custody the product is session-local (nothing else can write it),
 * so leaving simply drops it. The rule: an interaction that
 * modifies contested persistent state must hold custody of it for the
 * operation; a measurement needs none because there is no competing writer.
 *
 * The session holds the `product` itself rather than a key to look up: a
 * custody-less product is in no store, and a store element is either the same
 * object for the session's lifetime (while it is the draft, the store
 * suppresses events for its key) or evicted — in which case the reference outlives the entry,
 * which is the better of the two for whoever is rendering it.
 */
export type EditSession = EditSessionBase &
    (
        | {
              readonly custody: true;
              /** The element held in the store and locked for the session. */
              readonly product: Element;
              /** The element's identity when the session began — commit compares against it to detect a re-source. */
              readonly ref: ElementRef;
          }
        | { readonly custody: false; readonly product: DraftObject }
    );

interface EditSessionBase {
    readonly kind: "edit";
    /** The descriptor owning the commit (resolved by element type for existing elements). */
    readonly descriptorId: string | null;
    readonly epoch: number;
    /** The params the product was drawn with — a re-arm (`restart`) reuses them. */
    readonly params: ToolParams;
    readonly isNew: boolean;
    readonly sub: EditSub;
}

export type Session = IdleSession | DrawSession | EditSession;

/** The object a session is producing or adjusting, resolved WITHOUT the element store. */
export function sessionDraft(session: Session): DraftObject | null {
    switch (session.kind) {
        case "draw":
            return session.draft?.object ?? null;
        case "edit":
            return session.product;
        case "idle":
            return null;
    }
}

/** An EDIT session over an element the runtime holds custody of (Save/Cancel pending). */
export function isCustodyEdit(session: Session): boolean {
    return session.kind === "edit" && session.custody;
}

/**
 * A DRAW session whose product will enter the live ElementStore. What the
 * historical toggle and the mapbar's draw-time rules mean by "drawing":
 * drawing into the live store has no meaning over a historical snapshot, and
 * its completion selects the product and pins the events panel. A
 * measurement, a lasso or a watchbox polygon is a draw session too, but one
 * whose product the store never sees — nothing it does conflicts with a panel
 * or a snapshot.
 */
export function isCustodyDraw(session: Session): boolean {
    return session.kind === "draw" && session.custody;
}

/**
 * "In flight" is a PREDICATE, not a phase: the session holds something the
 * operator has started and not finished — a draw whose draft is placed or has
 * locked vertices (a trace's press places its ring and locks the first vertex,
 * so a trace is in flight from its first press). A mousemove-only preview is
 * NOT in flight: nothing was started.
 *
 * An EDIT session is never "in flight" — it is *editing*, which has its own,
 * stricter rules (activation refuses it, the pickers disable for it, Escape
 * cancels or restarts it) whether or not the runtime holds custody.
 *
 * The tool picker disables for it, the Escape ladder restarts it rather than
 * exiting, and `canActivate` refuses to preempt it.
 */
export function isInFlight(session: Session): boolean {
    switch (session.kind) {
        case "idle":
            return false;
        case "draw":
            return session.draft !== null && (session.draft.placed || session.locked > 0);
        case "edit":
            return false;
    }
}

/** What Escape means to a live session. */
export type SessionEscape = "restart" | "cancel" | "exit";

/**
 * What Escape does to the session it reached — the LIFECYCLE half of the
 * Escape ladder, kept here so the arbiter that hands Escape to the session
 * knows nothing of the session but that one is live.
 *
 * - A draw with something in flight (a placed draft, a trace mid-press)
 *   drops it and stays armed: `"restart"`. A mousemove-only preview is not in
 *   flight, so a draw that is only waiting leaves: `"exit"` — otherwise the
 *   preview would resurrect under the cursor and Escape would look
 *   unresponsive.
 * - An edit cancels — a custody edit reverts or discards its element, a
 *   custody-less product is dropped and the tool left — unless the descriptor
 *   declares `onEscapeInEdit: "restart"`, measure's re-take loop: the finished
 *   product is dropped and the same shape re-arms.
 */
export function sessionEscape(
    session: DrawSession | EditSession,
    resolve: DescriptorResolver,
): SessionEscape {
    if (session.kind === "draw") return isInFlight(session) ? "restart" : "exit";
    const descriptor = session.descriptorId === null ? undefined : resolve(session.descriptorId);
    return descriptor?.onEscapeInEdit === "restart" ? "restart" : "cancel";
}

/** The binding identity the runner keys its binder table on. */
export function bindingKey(session: Session): string {
    switch (session.kind) {
        case "idle":
            return "idle";
        case "draw":
            return `draw:${session.epoch}`;
        case "edit":
            return `edit:${session.epoch}`;
    }
}

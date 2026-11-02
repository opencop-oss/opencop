// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Named commands — the ONLY domain-lifecycle writers for the interaction
 * session. Callable from anywhere (React handlers, the runner's map handlers,
 * the keyboard router, vanilla code); every command runs synchronously and
 * leaves the store and the element's custody consistent.
 *
 * Commands return `{ ok: true } | { ok: false, reason }` — refusal is neither
 * success nor exception.
 *
 * Commands own the session and its custody. Nothing else: what the rest of
 * the app does about a session — select its product, open an editing overlay,
 * show a panel, close the map surface a session starts beneath — is a
 * reaction, heard through the synchronous session-lifecycle notification
 * (`session-lifecycle.ts`) that every lifecycle command emits. No UI call
 * lives here.
 */
import Logger from "js-logger";
import { Element, toElementRef, type DraftObject } from "@opencop/elements";
import type { CompletionHost, ToolDescriptor, ToolParams } from "@opencop/tools";
import {
    IDLE,
    emptySub,
    nextEpoch,
    bumpRev,
    getInteractionState,
    setSession,
    type DrawSession,
    type EditSession,
    type EditSub,
} from "./interaction.store";
import { abortCustody, beginCustody, commitCustody, hasCustody } from "./custody";
import type { InteractionHost } from "../contract";
import { getHost } from "../host";
import { emitSessionLifecycle, type SessionEnd } from "./session-lifecycle";
import { descriptorOf, resolveGrammarContained } from "./grammar-resolution";
import { canChangeTool } from "./derive/tool-change";

const logger = Logger.get("interaction");

export type CommandResult = { ok: true } | { ok: false; reason: string };

const OK: CommandResult = { ok: true };
function refuse(reason: string): CommandResult {
    if (import.meta.env.DEV) logger.debug(`command refused: ${reason}`);
    return { ok: false, reason };
}

/**
 * The host for a release path that only runs under a live session. Those
 * cannot see `null` — detaching ends the session before it clears the slot
 * (see `host.ts`) — so a miss here is a broken host ordering, not a state to
 * handle: it is logged rather than skipped silently, because skipping means
 * an element left locked in the pipeline with no session to free it.
 */
function hostForRelease(where: string): InteractionHost | null {
    const host = getHost();
    if (!host) logger.error(`${where}: no host under a live session — custody not released`);
    return host;
}

// ---------------------------------------------------------------------------
// Module-level session companions (owned by commands, never by React)
// ---------------------------------------------------------------------------

/** Geometry-change subscription for the current session's draft (bumps `rev`). */
let geoUnsub: (() => void) | null = null;

function watchGeometry(draft: DraftObject | null): void {
    geoUnsub?.();
    geoUnsub = null;
    if (!draft) return;
    geoUnsub = draft.geometry.listen(() => {
        bumpRev();
    });
}

// ---------------------------------------------------------------------------
// Internal transitions
// ---------------------------------------------------------------------------

/**
 * Release a placed draft: under custody it is in the store and locked, so it
 * is unwound; a preview-only draft, or a custody-less one, is session-local
 * and simply dropped with the session.
 */
function dropDraft(session: DrawSession): void {
    if (!session.custody || !session.draft?.placed) return;
    const host = hostForRelease("dropDraft");
    if (!host) return;
    abortCustody(host);
}

/**
 * The draft a custody session takes into the element store, or `null` after
 * reporting the tool's defect. A tool that produces into the element store must
 * draft an `Element`: no other object may enter the store.
 */
function custodyElement(draft: DraftObject, descriptorId: string): Element | null {
    if (draft instanceof Element) return draft;
    getHost()?.environment.reportToolDefect(
        descriptorId,
        new Error(`"${descriptorId}" produces into the element store but drafted a non-element`),
    );
    return null;
}

/**
 * Release an edit's element without saving: drop the draft. Without custody
 * the product is session-local and there is nothing to unwind.
 */
function releaseEditCustody(session: EditSession): void {
    if (!session.custody) return;
    const host = hostForRelease("releaseEditCustody");
    if (!host) return;
    abortCustody(host);
}

/**
 * The session is over: stop watching its geometry, write idle, then tell the
 * subscribers which session ended and why. The store is idle before anyone
 * hears about it.
 */
function endSession(session: DrawSession | EditSession, how: SessionEnd): void {
    watchGeometry(null);
    setSession(IDLE);
    emitSessionLifecycle({ type: "ended", session, how });
}

/** Abort whatever is in flight and return to a clean slate. */
function abortSession(): void {
    const { session } = getInteractionState();
    switch (session.kind) {
        case "draw":
            dropDraft(session);
            break;
        case "edit":
            releaseEditCustody(session);
            break;
        case "idle":
            return;
    }
    endSession(session, "exited");
}

/**
 * A fresh draw session for a descriptor — activation, a params restart and a
 * re-arm all land here: nothing on the map, nothing locked, a new epoch so the
 * runner rebinds.
 */
function arm(descriptorId: string, params: ToolParams, custody: boolean): void {
    watchGeometry(null);
    const base = { kind: "draw", descriptorId, epoch: nextEpoch(), params, locked: 0 } as const;
    setSession(
        custody
            ? { ...base, custody: true, draft: null }
            : { ...base, custody: false, draft: null },
    );
}

/** What an edit session holds: an element under custody, or any draft without it. */
type Held =
    | { readonly custody: true; readonly product: Element }
    | { readonly custody: false; readonly product: DraftObject };

/** A draw session's draft as an edit session's product, with the session's custody. */
function heldDraft(session: DrawSession): Held | null {
    if (session.custody) return session.draft && { custody: true, product: session.draft.object };
    return session.draft && { custody: false, product: session.draft.object };
}

/**
 * Draw → EDIT over the finished draft. With custody the element is already in
 * the store and locked (custody began at first placement). Without custody (a finished measurement, a watchbox
 * polygon) the product is session-local: same session kind, no store, no
 * lock — leaving drops it. Whether anything selects the product or opens an
 * overlay for it is the subscribers' business; they hear `edit-started` once
 * the session is in the store.
 */
function completeToEdit(held: Held, descriptorId: string, params: ToolParams): void {
    enterEditSession({ ...held, descriptorId, params, isNew: true });
}

/**
 * Write an EDIT session over a product, keep watching its geometry (handle
 * drags mutate it), and announce it.
 */
function enterEditSession(
    init: Held & { descriptorId: string | null; params: ToolParams; isNew: boolean },
): void {
    const base = {
        kind: "edit",
        descriptorId: init.descriptorId,
        epoch: nextEpoch(),
        params: init.params,
        isNew: init.isNew,
        sub: emptySub(),
    } as const;
    const session: EditSession = init.custody
        ? { ...base, custody: true, product: init.product, ref: toElementRef(init.product) }
        : { ...base, custody: false, product: init.product };
    setSession(session);
    watchGeometry(init.product);
    // Announced after the session is written. The map surface a session starts
    // beneath is closed by a subscriber, one step later in this same tick.
    emitSessionLifecycle({ type: "edit-started", session });
}

// ---------------------------------------------------------------------------
// Activation
// ---------------------------------------------------------------------------

export interface ActivateOptions {
    /** Immediate placement (context-menu drop point): place + complete synchronously. */
    readonly placeAt?: readonly [number, number];
    readonly params?: ToolParams;
}

/**
 * Whether `activate(id)` would be allowed right now: the descriptor must have
 * a draw grammar, and the session must allow a tool change at all.
 *
 * The session half is `canChangeTool` — the same derivation the toolbar reads
 * to decide whether its buttons are enabled, so the rule cannot drift between
 * the gate and the chrome that offers it. This function is the *imperative*
 * caller (a command, at click/keypress time); render-time callers subscribe to
 * the session and call the derivation directly.
 */
export function canActivate(id: string): boolean {
    const host = getHost();
    if (!host) return false;
    const descriptor = descriptorOf(id);
    if (!descriptor?.draw) return false;
    const { session } = getInteractionState();
    return canChangeTool(session, { isHistorical: host.environment.isHistorical() });
}

/** Enter a session for the given descriptor id. */
export function activate(id: string, opts: ActivateOptions = {}): CommandResult {
    const descriptor = descriptorOf(id);
    if (!descriptor) return refuse(`unknown descriptor "${id}"`);
    if (!canActivate(id)) return refuse(`cannot activate "${id}" now`);
    const host = getHost();
    if (!host) return refuse("runtime not attached");

    abortSession();

    // The custody rule, applied once per session: a descriptor producing into
    // the element store draws a persisted element; anything else draws a
    // product the element store never sees.
    const custody = hasCustody(descriptor);
    const params = opts.params ?? {};

    // A throwing grammar factory refuses the activation; the toolbar stays usable.
    const resolved = resolveGrammarContained(descriptor, params);
    if (resolved.kind === "refused") return refuse(resolved.message);
    if (resolved.kind === "defect") return refuse(`"${id}" grammar threw`);
    const { grammar } = resolved;
    if (!grammar) return refuse(`"${id}" has no draw grammar`);

    // Announced before the session is written (see `session-lifecycle.ts`):
    // the previous session has ended and this one is about to begin, in
    // whichever kind it lands — a subscriber that must clear state the
    // session's completion will replace (a custody draw's completion selects
    // its product) does so here, before an immediate placement completes. So
    // does the one that closes the map surface a session starts beneath: a
    // surface anchored to the click before this one cannot stay meaningful
    // now that the operator has started drawing, and left open its actions
    // would target an element while the session runs.
    emitSessionLifecycle({ type: "activated", descriptorId: id, custody });

    // Immediate placement: place + complete synchronously INSIDE the command —
    // no microtask, no effect-ordering hazard.
    if (opts.placeAt) {
        const draft = grammar.create([opts.placeAt[0], opts.placeAt[1]]);
        if (!custody) {
            completeToEdit({ custody: false, product: draft }, id, params);
            return OK;
        }
        const element = custodyElement(draft, id);
        if (!element) return refuse(`"${id}" drafted a non-element into the element store`);
        beginCustody(host, element);
        completeToEdit({ custody: true, product: element }, id, params);
        return OK;
    }

    arm(id, params, custody);
    return OK;
}

/**
 * Replace the active session's params — destroy-and-restart, one atomic
 * command: release the draft, re-arm the same descriptor, epoch++. Desync is
 * impossible because no effect-dependency teardown exists anywhere. Legal
 * from a draw or a custody-less edit (measure's shape toggle over a finished
 * measurement re-arms with the new shape); a custody edit has nothing to
 * re-arm into.
 */
export function setParams(params: ToolParams): CommandResult {
    return rearm(params, "no active session for params");
}

/**
 * Drop whatever the current session holds and arm its descriptor again with
 * `params` — the body `setParams` and `restart` share. A draw releases its
 * draft; a custody-less edit drops its product. A custody edit has nothing to
 * re-arm into (Save / Cancel resolve it), and idle has no descriptor.
 */
function rearm(params: ToolParams | null, refusal: string): CommandResult {
    const { session } = getInteractionState();
    if (session.kind === "draw") {
        dropDraft(session);
        arm(session.descriptorId, params ?? session.params, session.custody);
        return OK;
    }
    if (session.kind === "edit" && !session.custody && session.descriptorId !== null) {
        arm(session.descriptorId, params ?? session.params, false);
        return OK;
    }
    return refuse(refusal);
}

// ---------------------------------------------------------------------------
// Draw-session internals (called through the runner's store-backed DraftRefs)
//
// Each takes the epoch of the session its caller was bound for and refuses a
// write from any other. The epoch's load-bearing job is `bindingKey`: the
// runner rebinds on it, and `MapManager.onMap` goes inert on unsubscribe, so
// no retired handler can reach these. The check here is a second line of
// defence — it would catch a setup that wrote after its own command had
// replaced the session — not what keeps stale handlers out.
// ---------------------------------------------------------------------------

/** The mousemove preview created the in-flight draft (not yet placed). */
export function draftCreated(draft: DraftObject, epoch: number): CommandResult {
    const { session } = getInteractionState();
    if (session.kind !== "draw" || session.epoch !== epoch) return refuse("stale draft");
    if (session.custody) {
        const element = custodyElement(draft, session.descriptorId);
        if (!element) return refuseDefect(session);
        setSession({ ...session, draft: { object: element, placed: false } });
    } else {
        setSession({ ...session, draft: { object: draft, placed: false } });
    }
    watchGeometry(draft);
    return OK;
}

/** First confirmed click: place the draft, taking custody of it if the session does. */
export function beginDraft(draft: DraftObject, epoch: number): CommandResult {
    const { session } = getInteractionState();
    if (session.kind !== "draw" || session.epoch !== epoch) return refuse("stale placement");
    if (!session.custody) {
        setSession({ ...session, draft: { object: draft, placed: true } });
        return OK;
    }
    const element = custodyElement(draft, session.descriptorId);
    if (!element) return refuseDefect(session);
    const host = getHost();
    if (!host) return refuse("runtime not attached");
    beginCustody(host, element);
    setSession({ ...session, draft: { object: element, placed: true } });
    return OK;
}

/** End a draw whose tool broke the custody rule; the defect is already reported. */
function refuseDefect(session: DrawSession): CommandResult {
    abortSession();
    return refuse(`"${session.descriptorId}" drafted a non-element into the element store`);
}

export function isDraftPlaced(): boolean {
    const { session } = getInteractionState();
    return session.kind === "draw" && (session.draft?.placed ?? false);
}

/** Vertex/phase confirmed — substate only, never rebinds. */
export function setLocked(locked: number, epoch: number): void {
    const { session } = getInteractionState();
    if (session.kind === "draw" && session.epoch === epoch) {
        setSession({ ...session, locked });
    }
}

/**
 * The session's draft changed in a way its geometry signal does not carry — an
 * editor mutated something else it draws from — so everything drawn from the
 * draft redraws.
 */
export function draftChanged(): void {
    bumpRev();
}

/** Edit hover/drag substate (cursor derivation input; never rebinds). */
export function setEditSub(sub: EditSub): void {
    const { session } = getInteractionState();
    if (session.kind !== "edit") return;
    // Hovering calls this on every mouse move; an unchanged sub keeps the session
    // object, so readers of the whole session do not re-render or rebuild layers.
    if (session.sub.hotspot === sub.hotspot && session.sub.dragging === sub.dragging) return;
    setSession({ ...session, sub });
}

// ---------------------------------------------------------------------------
// Completion / save / cancel / exit
// ---------------------------------------------------------------------------

/**
 * Completion through the descriptor's `onComplete`: the tool does its domain
 * work with the finished draft and says whether the session leaves or re-arms.
 * Runs synchronously inside `complete()`, so the session is already idle (or
 * armed again) by the time any click the map synthesizes from the release can
 * arrive. The draft is released either way — the hook is where a product of
 * this kind is consumed, not kept.
 */
function completeOutcome(
    session: DrawSession,
    onComplete: NonNullable<ToolDescriptor["onComplete"]>,
    host: InteractionHost,
): CommandResult {
    if (!session.draft) return refuse("nothing to complete");

    const completion: CompletionHost = {
        pickInsidePolygon: (vertices) => host.actions.pickInsidePolygon(vertices),
        // The one place a tool's product becomes host state. A tool that
        // gathers a selection hands it over here and keeps nothing, which is
        // what lets the tool live in a plugin while the Events panel, export
        // and deletion-pruning stay host concerns.
        selectElements: (elements, boundary) => host.actions.selectArea(elements, boundary),
    };
    // Contributed code, like `resolveGrammar`: a throw is the tool's defect,
    // reported to the host, and the session ends as exited — it must not take
    // the runtime down mid-completion.
    let next: ReturnType<typeof onComplete>;
    try {
        next = onComplete(session.draft.object, completion);
    } catch (error) {
        host.environment.reportToolDefect(session.descriptorId, error);
        dropDraft(session);
        endSession(session, "exited");
        return refuse(`"${session.descriptorId}" onComplete threw`);
    }
    dropDraft(session);

    if (next === "re-arm") {
        arm(session.descriptorId, session.params, session.custody);
        return OK;
    }
    endSession(session, "exited");
    return OK;
}

/**
 * Complete the in-flight draft: through the descriptor's `onComplete` when it
 * declares one, otherwise into an EDIT session over the product — with
 * custody when the session holds it, without when it does not.
 */
export function complete(): CommandResult {
    const { session } = getInteractionState();
    if (session.kind !== "draw") return refuse("no completable session");
    const held = heldDraft(session);
    if (!held) return refuse("nothing to complete");

    const onComplete = descriptorOf(session.descriptorId)?.onComplete;
    if (onComplete) {
        const host = getHost();
        if (!host) return refuse("runtime not attached");
        return completeOutcome(session, onComplete, host);
    }
    completeToEdit(held, session.descriptorId, session.params);
    return OK;
}

/**
 * Escape's in-session step: release the in-flight draft but stay armed for a
 * fresh attempt (a draw with vertices, a trace mid-press), or drop a finished
 * custody-less product and re-arm its tool (measure's Escape over a result).
 * Returns `ok: false` when there was nothing to drop — the caller escalates
 * to `exit()`.
 */
export function restart(): CommandResult {
    const { session } = getInteractionState();
    if (session.kind === "draw" && !session.draft) return refuse("nothing to restart");
    return rearm(null, "no session to restart");
}

/**
 * End the current edit as a success. This is the verb for "the product is
 * kept", whoever persisted it.
 *
 * Under custody the runtime persists it here: the held element is committed
 * and `updated` carries the overlay's edits (possibly re-sourced). Without
 * custody there is nothing for the runtime to commit — the product lives
 * outside the element store and its owner has already written it away — so
 * the session simply ends as saved. Passing `updated` in that case is a
 * caller error: there is no held element to apply it to.
 *
 * Either way subscribers hear one `ended` event with `how: "saved"`, and
 * cannot tell the two apart. That is deliberate — "it was kept" is the fact
 * they react to; where it was kept is not their business.
 */
export function save(updated?: Element): CommandResult {
    const { session } = getInteractionState();
    if (session.kind !== "edit") return refuse("no edit session");
    if (!session.custody) {
        if (updated) return refuse("session holds no custody — nothing to apply `updated` to");
        endSession(session, "saved");
        return OK;
    }
    const host = getHost();
    if (!host) return refuse("runtime not attached");

    // Commit routing: the session's descriptor first, then the tool that edits the element.
    const descriptor =
        descriptorOf(session.descriptorId) ??
        (updated ? host.environment.editToolFor(updated) : undefined);
    commitCustody(host, updated, session.ref, descriptor?.onCommit);

    endSession(session, "saved");
    return OK;
}

/**
 * Cancel the current edit — revert an existing element, discard a new one.
 * Without custody there is nothing to revert: the product is dropped and the
 * tool left, which is what Escape over a finished watchbox polygon does.
 */
export function cancel(): CommandResult {
    const { session } = getInteractionState();
    if (session.kind !== "edit") return refuse("no edit session");
    releaseEditCustody(session);
    endSession(session, "cancelled");
    return OK;
}

/**
 * Leave the current session entirely, releasing anything in flight. The
 * neutral ending — neither kept nor reverted — reported as `"exited"`. A
 * session that finished successfully ends through `save()`, one the operator
 * backed out of through `cancel()`.
 */
export function exit(): CommandResult {
    abortSession();
    return OK;
}

// ---------------------------------------------------------------------------
// Editing existing elements
// ---------------------------------------------------------------------------

/**
 * Enter an EDIT session for an existing (or just-pasted) element. A store
 * element is always under custody: the session locks it for as long as it
 * holds it.
 */
export function startEdit(element: Element, isNew: boolean): CommandResult {
    const host = getHost();
    if (!host) return refuse("runtime not attached");

    abortSession();

    const descriptor = host.environment.editToolFor(element);
    const product = isNew ? element : element.clone(true);
    beginCustody(host, product);
    enterEditSession({
        custody: true,
        product,
        descriptorId: descriptor?.id ?? null,
        params: {},
        isNew,
    });
    return OK;
}

// ---------------------------------------------------------------------------
// Teardown
// ---------------------------------------------------------------------------

/** Full teardown — the runtime detaching from the map. */
export function resetInteraction(): void {
    abortSession();
}

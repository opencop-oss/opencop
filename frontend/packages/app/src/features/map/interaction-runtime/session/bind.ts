// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The session's input bindings — what a DRAW session and an EDIT session each
 * bind on the host while they own the pointer. The runner
 * (`../arbitration/runner.ts`) decides WHEN to bind and unbind; these decide
 * WHAT: the draw grammar in the tool's kit, or the edit
 * interpreter for the descriptor's edit model. Both talk to the session only through the
 * store and the commands.
 *
 * The session is one owner of the pointer, registered by the composition
 * point (`../create.ts`) with `bindSession` as its handler set and
 * `bindingKey` as the set's identity.
 */
import Logger from "js-logger";
import { Element } from "@opencop/elements";
import type { DraftRefs, ToolDescriptor } from "@opencop/tools";
import type { InputSubscription, InteractionHost } from "../contract";
import { containedDrawInput, drawInputOf } from "./draw-input";
import {
    getInteractionState,
    type DrawSession,
    type EditSession,
    type Session,
} from "./interaction.store";
import {
    beginDraft,
    complete,
    draftCreated,
    exit,
    isDraftPlaced,
    restart,
    setLocked,
} from "./commands";
import { descriptorOf, editModelOf, resolveGrammarContained } from "./grammar-resolution";
import { bindEditSession } from "./edit-session";

const logger = Logger.get("interaction");

// ---------------------------------------------------------------------------
// Store-backed DraftRefs — a grammar's refs ARE the session store
// ---------------------------------------------------------------------------

function storeBackedRefs(
    epoch: number,
    descriptor: ToolDescriptor | undefined,
    host: InteractionHost,
): DraftRefs {
    const draftOf = () => {
        const { session } = getInteractionState();
        return session.kind === "draw" && session.epoch === epoch ? session : null;
    };
    return {
        getDraft: () => draftOf()?.draft?.object,
        setDraft: (draft) => {
            draftCreated(draft, epoch);
        },
        getLocked: () => draftOf()?.locked ?? 0,
        setLocked: (locked) => setLocked(locked, epoch),
        isPlaced: () => isDraftPlaced(),
        onPlace: (draft) => {
            const { session } = getInteractionState();
            if (session.kind === "draw") beginDraft(draft, session.epoch);
        },
        onComplete: () => {
            complete();
        },
        onAbort: () => {
            restart();
        },
        onUpdate: () => {
            // Style inputs write element style; a tool may declare them only
            // when its product is an element (`styleError`).
            const el = draftOf()?.draft?.object;
            if (!(el instanceof Element) || !descriptor?.style) return;
            for (const s of descriptor.style) {
                const value = host.environment.styleValue(
                    descriptor.id,
                    s.label,
                    s.input.defaultValue,
                );
                s.input.onChange(el, value as never);
            }
        },
    };
}

// ---------------------------------------------------------------------------
// Per-session bindings
// ---------------------------------------------------------------------------

/**
 * Bind a LIVE session's set: the draw grammar or the edit interpreter. Idle
 * binds nothing of the session's — what the pointer means then is ambient's,
 * another owner in the pointer order.
 */
export function bindSession(session: Session, host: InteractionHost): InputSubscription[] {
    switch (session.kind) {
        case "idle":
            return [];
        case "draw":
            return bindDraw(session, host);
        case "edit":
            return bindEdit(session, host);
    }
}

/**
 * Bind a DRAW session: the grammar in the tool's draw kit. The kit names the
 * grammar itself, so a new way of drawing is a new grammar and never an edit
 * here.
 *
 * A grammar is contributed code, so it is contained: a `bind` that throws
 * releases what it had bound, is reported against the tool, and leaves the
 * session armed with no draw bindings; a listener that throws is reported and
 * ends the session. Either way the draw is lost, never the map.
 */
export function bindDraw(session: DrawSession, host: InteractionHost): InputSubscription[] {
    const descriptor = descriptorOf(session.descriptorId);
    if (!descriptor) return [];

    // A refusal (e.g. measure armed with no shape) or a defect leaves the
    // session armed with no draw subscriptions.
    const resolved = resolveGrammarContained(descriptor, session.params);
    if (resolved.kind === "refused") {
        logger.debug(`${resolved.message}; session stays armed without draw bindings.`);
        return [];
    }
    if (resolved.kind === "defect" || !resolved.grammar) return [];
    const { grammar } = resolved;
    const contained = containedDrawInput(drawInputOf(host), (error) => {
        host.environment.reportToolDefect(descriptor.id, error);
        exit();
    });
    try {
        return grammar.bind(contained.input, storeBackedRefs(session.epoch, descriptor, host));
    } catch (error) {
        for (const subscription of contained.bound) subscription.unsub();
        host.environment.reportToolDefect(descriptor.id, error);
        return [];
    }
}

/** Bind an EDIT session: the interpreter for the descriptor's edit model (the generic drag model by default). */
export function bindEdit(session: EditSession, host: InteractionHost): InputSubscription[] {
    const model = editModelOf(descriptorOf(session.descriptorId));
    return bindEditSession(model, {
        sources: host.sources,
        project: (lngLat) => host.actions.projectToScreen(lngLat),
        getDraft: () => {
            const { session: current } = getInteractionState();
            return current.kind === "edit" ? current.product : null;
        },
    });
}

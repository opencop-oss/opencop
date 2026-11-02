// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * What the interaction session draws besides the element store, derived purely
 * from the session:
 *
 * 1. **The product** — what the session is making, when the session store holds
 *    it. A product bound for another store (an element, a watchbox) is held by
 *    that store as its draft and draws through that store's pipeline.
 * 2. **The construction path** — a dashed path through the draft's points, for
 *    a grammar that asks for one (`construction: "path"`), while the draft has
 *    fewer points than the grammar's minimum and so cannot be drawn yet.
 * 3. **The handles** — the session's control points. They come from the same
 *    edit model the drag loop hit-tests (`editModelOf`), so a handle is drawn
 *    exactly where the operator can grab one.
 */
import type { DraftObject } from "@opencop/elements";
import { SESSION_STORE_ID, type ControlPoint, type ToolDescriptor } from "@opencop/tools";
import { grammarFor } from "../../interaction-runtime/session/derive/draw-hints";
import { editModelOf } from "../../interaction-runtime/session/grammar-resolution";
import { sessionDraft, type Session } from "../../interaction-runtime/session/session";

/**
 * The handles to draw for a session: the resolved edit model's control points
 * over the session's draft, or none.
 *
 * - An idle session draws none.
 * - A draw session draws none until its draft is placed — the first mouse move
 *   creates an unplaced draft, and nothing is drawn before the first click.
 * - A tool that declares `onComplete` never reaches an edit session (the lasso
 *   hands its product to the host), so it has nothing to grab and draws none.
 */
export function sessionControlPoints(
    session: Session,
    resolve: (descriptorId: string) => ToolDescriptor | undefined,
): readonly ControlPoint[] {
    if (session.kind === "idle") return [];
    if (session.kind === "draw" && !session.draft?.placed) return [];
    const descriptor = session.descriptorId === null ? undefined : resolve(session.descriptorId);
    if (session.kind === "draw" && descriptor?.onComplete) return [];
    const draft = sessionDraft(session);
    if (!draft) return [];
    return editModelOf(descriptor).controlPoints(draft);
}

/**
 * The construction path to draw for a session — the draft's flat positions — or
 * null. Only for a placed draft whose grammar declares `construction: "path"`,
 * and only while the draft has fewer points than the grammar's minimum.
 */
export function sessionConstructionPath(
    session: Session,
    resolve: (descriptorId: string) => ToolDescriptor | undefined,
): number[] | null {
    if (session.kind !== "draw" || !session.draft?.placed || session.descriptorId === null) {
        return null;
    }
    const grammar = grammarFor(resolve(session.descriptorId), session);
    if (grammar?.construction !== "path") return null;
    const { geometry } = session.draft.object;
    const min = grammar.bounds?.min ?? 1;
    return geometry.numPositions < min ? [...(geometry.getAllPos() as number[])] : null;
}

/** A product the session store holds: the tool making it, and its draft. */
export interface SessionProduct {
    readonly tool: ToolDescriptor;
    readonly draft: DraftObject;
}

/**
 * The product to draw for a session, or null: a placed draft, or the product of
 * an edit session, of a tool whose product the session store holds.
 */
export function sessionProduct(
    session: Session,
    resolveTool: (descriptorId: string) => ToolDescriptor | undefined,
): SessionProduct | null {
    if (session.kind === "idle" || session.descriptorId === null) return null;
    if (session.kind === "draw" && !session.draft?.placed) return null;
    const tool = resolveTool(session.descriptorId);
    if (!tool || tool.produces?.store !== SESSION_STORE_ID) return null;
    const draft = sessionDraft(session);
    return draft ? { tool, draft } : null;
}

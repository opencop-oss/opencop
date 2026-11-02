// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Point, Polygon } from "@opencop/elements";
import type { ToolDescriptor } from "@opencop/tools";
import { isInFlight, sessionEscape, type Session } from "./session";

const element = () => Point.create({ position: [0, 0] });

function draw(overrides: Partial<Extract<Session, { kind: "draw" }>> = {}): Session {
    return {
        kind: "draw",
        descriptorId: "t",
        epoch: 1,
        params: {},
        custody: true,
        locked: 0,
        draft: null,
        ...overrides,
    } as Session;
}

function edit(custody: boolean): Session {
    return {
        kind: "edit",
        descriptorId: "t",
        epoch: 1,
        product: element(),
        ref: { sourceId: "local", objectId: "x" },
        custody,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    } as Session;
}

/**
 * "In flight" is a predicate over what the operator has started, not a phase
 * name: the picker disables for it, Escape restarts it, activation refuses to
 * preempt it.
 */
describe("isInFlight", () => {
    it("is false for idle, and for an edit session with or without custody (EDIT has its own rule)", () => {
        expect(isInFlight({ kind: "idle" })).toBe(false);
        expect(isInFlight(edit(true))).toBe(false);
        // A finished measurement is an edit session too: activation refuses
        // it and the pickers disable for it under the editing rule, not this one.
        expect(isInFlight(edit(false))).toBe(false);
    });

    it("a draw is in flight once its draft is placed or a vertex is locked — not on the preview", () => {
        expect(isInFlight(draw())).toBe(false);
        expect(isInFlight(draw({ draft: { object: element(), placed: false } }))).toBe(false);
        expect(isInFlight(draw({ draft: { object: element(), placed: true } }))).toBe(true);
        expect(isInFlight(draw({ draft: { object: element(), placed: false }, locked: 1 }))).toBe(
            true,
        );
    });

    it("custody makes no difference: a measurement follows the same rule as a shape", () => {
        const measuring = (overrides: Partial<Extract<Session, { kind: "draw" }>>) =>
            draw({ custody: false, ...overrides });
        expect(isInFlight(measuring({}))).toBe(false);
        expect(isInFlight(measuring({ draft: { object: element(), placed: false } }))).toBe(false);
        expect(isInFlight(measuring({ draft: { object: element(), placed: true } }))).toBe(true);
        expect(
            isInFlight(measuring({ draft: { object: element(), placed: false }, locked: 1 })),
        ).toBe(true);
    });

    it("a trace is in flight from its first press — the press places the draft and locks one vertex", () => {
        // A trace has no preview phase: the polygon draft is created and placed
        // by the mousedown, and `locked` counts its captured vertices. So the
        // same rule that covers a placed draw covers a trace; nothing else holds
        // trace state.
        const trace = Polygon.create({ positions: [0, 0] });
        expect(
            isInFlight(draw({ custody: false, draft: { object: trace, placed: true }, locked: 1 })),
        ).toBe(true);
    });
});

/**
 * What Escape means to the session it reached — the lifecycle half of the
 * Escape ladder. The ladder (`../arbitration/derive/keyboard-ladder.ts`) only ranks the
 * session against the other parties; every "and then what" is here.
 */
describe("sessionEscape", () => {
    const noDescriptors = () => undefined;
    const placed = () => ({ object: element(), placed: true });
    /** Measure's one declaration: Escape over the finished measurement re-arms. */
    const measureLike: ToolDescriptor = {
        id: "t",
        label: "x",
        icon: () => null,
        onEscapeInEdit: "restart",
    };
    const editOf = (descriptorId: string | null, custody: boolean) => ({
        ...(edit(custody) as Extract<Session, { kind: "edit" }>),
        descriptorId,
    });

    it("an in-flight draw (a placed draft) restarts — custody or not", () => {
        expect(sessionEscape(draw({ draft: placed(), locked: 1 }) as never, noDescriptors)).toBe(
            "restart",
        );
        expect(
            sessionEscape(
                draw({ custody: false, draft: placed(), locked: 1 }) as never,
                noDescriptors,
            ),
        ).toBe("restart");
    });

    it("a trace mid-press restarts — a placed polygon draft with its captured vertices locked", () => {
        // A live trace is the same shape as a draw with a confirmed vertex, so
        // the same answer catches it; no tool declares what Escape does mid-draw.
        const trace = { object: Polygon.create({ positions: [0, 0] }), placed: true };
        expect(
            sessionEscape(
                draw({ custody: false, draft: trace, locked: 1 }) as never,
                noDescriptors,
            ),
        ).toBe("restart");
    });

    it("a waiting draw with nothing in flight exits", () => {
        expect(sessionEscape(draw() as never, noDescriptors)).toBe("exit");
        expect(sessionEscape(draw({ custody: false }) as never, noDescriptors)).toBe("exit");
    });

    it("a preview-only draft (no placement, no vertices) counts as waiting, not in flight", () => {
        // Mirrors the old behavior: Escape with only the mousemove preview
        // exits entirely, so the preview can't instantly resurrect itself —
        // the hazard measure had too, once for both custody flavours.
        const preview = { object: element(), placed: false };
        expect(sessionEscape(draw({ draft: preview }) as never, noDescriptors)).toBe("exit");
        expect(
            sessionEscape(draw({ custody: false, draft: preview }) as never, () => measureLike),
        ).toBe("exit");
    });

    it("an edit cancels — a custody edit reverts or discards, a custody-less product is dropped (a finished watchbox)", () => {
        expect(sessionEscape(editOf("t", true), noDescriptors)).toBe("cancel");
        // No declaration needed: cancel over a product the runtime holds no
        // custody of discards it and leaves the session, which closes the form.
        expect(sessionEscape(editOf("watchboxTool", false), noDescriptors)).toBe("cancel");
        // An existing element's edit has no descriptor of its own to consult.
        expect(sessionEscape(editOf(null, true), () => measureLike)).toBe("cancel");
    });

    it("a finished measurement honours measure's declared restart (drop it, re-arm the shape)", () => {
        expect(sessionEscape(editOf("t", false), () => measureLike)).toBe("restart");
    });

    it('`onEscapeInEdit: "cancel"` spells out the default', () => {
        const explicit: ToolDescriptor = { ...measureLike, onEscapeInEdit: "cancel" };
        expect(sessionEscape(editOf("t", false), () => explicit)).toBe("cancel");
    });
});

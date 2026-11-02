// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Polygon } from "@opencop/elements";
import { polygonGrammar, type ToolDescriptor } from "@opencop/tools";
import type { Session } from "../session";
import { drawHintState, sessionDrawHints } from "./draw-hints";
import { genericDragModel } from "../edit-models/generic-drag.model";

/** The session decides the hint phase; the descriptor decides the lines. */

function draw(overrides: Partial<Extract<Session, { kind: "draw" }>> = {}): Session {
    return {
        kind: "draw",
        descriptorId: "polygonTool",
        epoch: 1,
        params: {},
        custody: true,
        locked: 0,
        draft: null,
        ...overrides,
    } as Session;
}

const draft = (placed: boolean) => ({ object: Polygon.create({ positions: [0, 0] }), placed });

function edit(descriptorId: string | null, custody: boolean): Session {
    return {
        kind: "edit",
        descriptorId,
        epoch: 1,
        product: Polygon.create({ positions: [0, 0, 1, 1, 1, 0] }),
        ref: { sourceId: "local", objectId: "x" },
        custody,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    } as Session;
}

const polygonDescriptor: ToolDescriptor = {
    id: "polygonTool",
    label: "polygon",
    icon: () => null,
    draw: { grammar: polygonGrammar },
};

const resolve = (d: ToolDescriptor) => (id: string) => (id === d.id ? d : undefined);
const polygon = polygonGrammar;

describe("drawHintState", () => {
    it("is null while idle", () => {
        expect(drawHintState({ kind: "idle" }, undefined)).toBeNull();
    });

    it("is armed before the first click, even with a draft following the cursor", () => {
        expect(drawHintState(draw(), polygon)?.phase).toBe("armed");
        expect(drawHintState(draw({ draft: draft(false) }), polygon)?.phase).toBe("armed");
    });

    it("is placing once the draft is placed, with the locked count and the grammar's bounds", () => {
        expect(drawHintState(draw({ draft: draft(true), locked: 2 }), polygon)).toEqual({
            phase: "placing",
            placed: 2,
            movable: true,
            min: 3,
            max: undefined,
        });
    });

    it("is editing over a finished product, and says nothing about custody", () => {
        const state = drawHintState(edit("polygonTool", false), polygon);
        expect(state).toMatchObject({ phase: "editing" });
        expect(state).not.toHaveProperty("custody");
    });
});

describe("sessionDrawHints", () => {
    it("gives the grammar's defaults to a tool that declares no hints", () => {
        const hints = sessionDrawHints(draw(), resolve(polygonDescriptor));
        expect(hints.map((h) => h.key)).toEqual(["drawHints.sequential.start"]);
    });

    it("gives the tool's own hints when it declares them", () => {
        const own = {
            ...polygonDescriptor,
            draw: { grammar: polygonGrammar, hints: () => [{ key: "own" }] },
        };
        expect(sessionDrawHints(draw(), resolve(own)).map((h) => h.key)).toEqual(["own"]);
    });

    it("gives the host's edit defaults, with Save, to an element edit with no descriptor", () => {
        const hints = sessionDrawHints(edit(null, true), resolve(polygonDescriptor));
        expect(hints.map((h) => h.key)).toEqual([
            "drawHints.edit.reshapeOrMove",
            "drawHints.edit.save",
        ]);
    });

    it("shows Save only over an edit the element store holds", () => {
        const hints = sessionDrawHints(edit("polygonTool", false), resolve(polygonDescriptor));
        expect(hints.map((h) => h.key)).toEqual(["drawHints.edit.reshapeOrMove"]);
    });

    it("leaves the move line out for a tool whose edit model never moves its product", () => {
        const fixed: ToolDescriptor = {
            ...polygonDescriptor,
            draw: {
                grammar: polygonGrammar,
                editModel: { ...genericDragModel, translate: false },
            },
        };
        const hints = sessionDrawHints(edit("polygonTool", true), resolve(fixed));
        expect(hints.map((h) => h.key)).toEqual(["drawHints.edit.reshape", "drawHints.edit.save"]);
    });

    it("gives none for a grammar factory that throws — activation has already reported it", () => {
        const broken: ToolDescriptor = {
            ...polygonDescriptor,
            draw: {
                grammar: () => {
                    throw new Error("boom");
                },
            },
        };
        expect(sessionDrawHints(draw(), resolve(broken))).toEqual([]);
    });
});

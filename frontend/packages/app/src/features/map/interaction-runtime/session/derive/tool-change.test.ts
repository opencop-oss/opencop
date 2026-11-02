// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Polygon } from "@opencop/elements";
import type { Session } from "../session";
import { canChangeTool, type ToolChangeEnv } from "./tool-change";

/**
 * The session half of the activation gate, pinned as a table. One rule, two
 * callers — `canActivate` at click/keypress time and the toolbar's disabled
 * state at render time — so a divergence here is a divergence in both.
 */

const env = (overrides: Partial<ToolChangeEnv> = {}): ToolChangeEnv => ({
    isHistorical: false,
    ...overrides,
});

const idle: Session = { kind: "idle" };

function draw(overrides: Partial<Extract<Session, { kind: "draw" }>> = {}): Session {
    return {
        kind: "draw",
        descriptorId: "lineTool",
        epoch: 1,
        params: {},
        custody: true,
        locked: 0,
        draft: null,
        ...overrides,
    } as Session;
}

const placedDraft = () => ({ object: Polygon.create({ positions: [0, 0] }), placed: true });

describe("canChangeTool", () => {
    it("allows a tool change from idle", () => {
        expect(canChangeTool(idle, env())).toBe(true);
    });

    it("allows preempting a draw that is only waiting for its first click", () => {
        expect(canChangeTool(draw(), env())).toBe(true);
    });

    it("allows preempting a draw showing a mousemove preview", () => {
        const preview = { object: Polygon.create({ positions: [0, 0] }), placed: false };
        expect(canChangeTool(draw({ draft: preview }), env())).toBe(true);
    });

    it("refuses over a draw with a placed draft", () => {
        expect(canChangeTool(draw({ draft: placedDraft() }), env())).toBe(false);
    });

    it("refuses over a draw with locked vertices", () => {
        const captured = { object: Polygon.create({ positions: [0, 0] }), placed: false };
        expect(canChangeTool(draw({ draft: captured, locked: 2 }), env())).toBe(false);
    });

    it("refuses over an edit session, custody or not", () => {
        for (const custody of [true, false]) {
            const edit = {
                kind: "edit",
                descriptorId: "lineTool",
                epoch: 1,
                product: Polygon.create({ positions: [0, 0] }),
                ref: { key: "k", sourceId: "s", objectId: "o" },
                custody,
                params: {},
                isNew: true,
                sub: { hotspot: null, dragging: null },
            } as unknown as Session;
            expect(canChangeTool(edit, env())).toBe(false);
        }
    });

    it("refuses in historical mode even from idle", () => {
        expect(canChangeTool(idle, env({ isHistorical: true }))).toBe(false);
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Line, Point } from "@opencop/elements";
import {
    defaultDrawHints,
    resolveDrawHints,
    type DrawHint,
    type DrawHintState,
} from "./draw-hints";
import { createParametricGrammar, createSequentialGrammar } from "./grammars";
import { pointGrammar, polygonGrammar, traceGrammar } from "./shape-grammars";

const point = pointGrammar;
const polygon = polygonGrammar;
const trace = traceGrammar;

const keys = (hints: readonly DrawHint[]) => hints.map((h) => h.key);

function state(overrides: Partial<DrawHintState> = {}): DrawHintState {
    return { phase: "armed", placed: 0, movable: true, ...overrides };
}

describe("defaultDrawHints — every grammar has instructions", () => {
    it("point: click to place", () => {
        expect(keys(defaultDrawHints(point, state()))).toEqual(["drawHints.point.place"]);
    });

    it("sequential, armed: place the first point", () => {
        expect(keys(defaultDrawHints(polygon, state()))).toEqual(["drawHints.sequential.start"]);
    });

    it("sequential, below its minimum: how many more points, then undo", () => {
        const hints = defaultDrawHints(polygon, state({ phase: "placing", placed: 1, min: 3 }));
        expect(keys(hints)).toEqual(["drawHints.sequential.needMore", "drawHints.sequential.undo"]);
        // A polygon needs three; one is placed, so two more clicks reach it.
        expect(hints[0]!.values).toEqual({ count: 2 });
    });

    it("sequential: offers completing once the next click reaches the minimum", () => {
        // Two placed plus the cursor's point is three — a polygon's minimum.
        const hints = defaultDrawHints(polygon, state({ phase: "placing", placed: 2 }));
        expect(keys(hints)).toEqual([
            "drawHints.sequential.addOrFinish",
            "drawHints.sequential.undo",
        ]);
    });

    it("sequential with a small max: completes with Enter, not a double-click", () => {
        const capped = createSequentialGrammar({
            min: 2,
            max: 5,
            create: (cursor) => Line.create({ positions: cursor }),
        });
        const hints = defaultDrawHints(capped, state({ phase: "placing", placed: 1 }));
        expect(keys(hints)).toContain("drawHints.sequential.addOrFinishEnter");
        expect(keys(hints)).not.toContain("drawHints.sequential.addOrFinish");
    });

    it("parametric: the next phase's own hint, or a generic one", () => {
        const tool = createParametricGrammar({
            create: (cursor) => Point.create({ position: cursor }),
            phases: [
                { preview: () => undefined, hint: { key: "own.first" } },
                { preview: () => undefined },
            ],
        });
        expect(keys(defaultDrawHints(tool, state()))).toEqual(["own.first"]);
        expect(keys(defaultDrawHints(tool, state({ phase: "placing", placed: 1 })))).toEqual([
            "drawHints.parametric.next",
        ]);
    });

    it("trace: press and drag, then release", () => {
        expect(keys(defaultDrawHints(trace, state()))).toEqual(["drawHints.trace.start"]);
        expect(keys(defaultDrawHints(trace, state({ phase: "placing", placed: 1 })))).toEqual([
            "drawHints.trace.release",
        ]);
    });

    it("editing: reshape, and move unless the product is unmovable; Save or Cancel only when the host says the edit is savable", () => {
        const editing = state({ phase: "editing" });
        expect(keys(defaultDrawHints(undefined, editing, { savable: true }))).toEqual([
            "drawHints.edit.reshapeOrMove",
            "drawHints.edit.save",
        ]);
        expect(keys(defaultDrawHints(undefined, editing))).toEqual([
            "drawHints.edit.reshapeOrMove",
        ]);
        expect(
            keys(defaultDrawHints(undefined, state({ phase: "editing", movable: false }))),
        ).toEqual(["drawHints.edit.reshape"]);
    });

    it("hands a tool's hints the Save line among the defaults, so the tool can keep or drop it", () => {
        const seen: string[][] = [];
        resolveDrawHints(
            (_s, defaults) => {
                seen.push(keys(defaults));
                return defaults;
            },
            undefined,
            state({ phase: "editing" }),
            { savable: true },
        );
        expect(seen).toEqual([["drawHints.edit.reshapeOrMove", "drawHints.edit.save"]]);
    });

    it("no grammar while drawing: no hints", () => {
        expect(defaultDrawHints(undefined, state())).toEqual([]);
    });
});

describe("resolveDrawHints — a tool overrides, extends, or turns them off", () => {
    it("uses the defaults when the tool declares none", () => {
        expect(keys(resolveDrawHints(undefined, point, state()))).toEqual([
            "drawHints.point.place",
        ]);
    });

    it("hands the defaults to the tool's function to extend", () => {
        const hints = resolveDrawHints((_s, d) => [...d, { key: "extra" }], point, state());
        expect(keys(hints)).toEqual(["drawHints.point.place", "extra"]);
    });

    it("shows none for `false`", () => {
        expect(resolveDrawHints(false, point, state())).toEqual([]);
    });
});

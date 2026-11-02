// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { noop } from "lodash";
import { createElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import { ELEMENT_STORE_ID, SESSION_STORE_ID } from "@opencop/tools";
import type { ToolLocation } from "@opencop/tools";
import {
    __resetToolRegistry,
    getTool,
    registerTool,
    selectToolsForLocation,
    toolForElementType,
    type ToolDescriptor,
} from "./tool-registry";

function descriptor(id: string, overrides: Partial<ToolDescriptor> = {}): ToolDescriptor {
    return { id, label: id, icon: () => createElement("span"), ...overrides };
}

const ids = (list: readonly ToolDescriptor[]) => list.map((d) => d.id);

afterEach(() => __resetToolRegistry());

describe("selectToolsForLocation", () => {
    it("defaults an undeclared location to `topBarStart`", () => {
        const all = [descriptor("a"), descriptor("b", { location: "topBarCenter" })];

        expect(ids(selectToolsForLocation(all, "topBarStart"))).toEqual(["a"]);
        expect(ids(selectToolsForLocation(all, "topBarCenter"))).toEqual(["b"]);
    });

    it("orders by `order`, lowest first, keeping registration order on ties", () => {
        const all = [
            descriptor("c", { order: 30 }),
            descriptor("a", { order: 10 }),
            descriptor("b", { order: 20 }),
            descriptor("d1"),
            descriptor("d2"),
        ];

        // Explicit orders sort first; the two defaulted (order 100) keep their
        // relative registration order.
        expect(ids(selectToolsForLocation(all, "topBarStart"))).toEqual([
            "a",
            "b",
            "c",
            "d1",
            "d2",
        ]);
    });

    it("does not mutate the source array", () => {
        const all = [descriptor("b", { order: 20 }), descriptor("a", { order: 10 })];
        const snapshot = [...all];

        selectToolsForLocation(all, "topBarStart");

        expect(all).toEqual(snapshot);
    });

    it("partitions descriptors across locations without loss", () => {
        const locations: ToolLocation[] = ["topBarStart", "topBarCenter", "topBarEnd"];
        const all = [
            descriptor("s", { location: "topBarStart" }),
            descriptor("c", { location: "topBarCenter" }),
            descriptor("e", { location: "topBarEnd" }),
            descriptor("defaulted"),
        ];

        const placed = locations.flatMap((location) => ids(selectToolsForLocation(all, location)));

        expect(placed.sort()).toEqual(["c", "defaulted", "e", "s"]);
    });

    it("places an `external`-located descriptor in none of the rendered toolbar locations, while it stays lookup-able by id", () => {
        const all = [descriptor("noButtonTool", { location: "external" })];
        registerTool(all[0]);

        // Toolbar chrome only ever queries "topBarStart", "topBarCenter" and
        // "topBarEnd" — "external" itself is not a location any chrome renders.
        const renderedLocations: ToolLocation[] = ["topBarStart", "topBarCenter", "topBarEnd"];
        const placed = renderedLocations.flatMap((location) =>
            ids(selectToolsForLocation(all, location)),
        );

        expect(placed).toEqual([]);
        expect(getTool("noButtonTool")).toBe(all[0]);
    });

    describe("group filter", () => {
        const all = [
            descriptor("interact1", { group: "interaction", order: 20 }),
            descriptor("shape", { group: "shapes" }),
            descriptor("ungrouped"),
            descriptor("interact0", { group: "interaction", order: 10 }),
            descriptor("elsewhere", { group: "interaction", location: "topBarEnd" }),
        ];

        it("`only` keeps one group of the location, ordered", () => {
            expect(
                ids(selectToolsForLocation(all, "topBarStart", { only: "interaction" })),
            ).toEqual(["interact0", "interact1"]);
        });

        it("`except` keeps every other group of the location, undeclared groups included", () => {
            expect(
                ids(selectToolsForLocation(all, "topBarStart", { except: "interaction" })),
            ).toEqual(["shape", "ungrouped"]);
        });

        it("`only` and `except` partition the location without loss", () => {
            const placed = [
                ...ids(selectToolsForLocation(all, "topBarStart", { only: "interaction" })),
                ...ids(selectToolsForLocation(all, "topBarStart", { except: "interaction" })),
            ];

            expect(placed.sort()).toEqual(ids(selectToolsForLocation(all, "topBarStart")).sort());
        });

        it("filters within the location only — a same-group tool in another location stays there", () => {
            expect(ids(selectToolsForLocation(all, "topBarEnd", { only: "interaction" }))).toEqual([
                "elsewhere",
            ]);
        });
    });
});

describe("registry", () => {
    it("registers a descriptor and looks it up by id", () => {
        registerTool(
            descriptor("lineTool", { produces: { store: ELEMENT_STORE_ID, type: "line" } }),
        );

        expect(getTool("lineTool")?.id).toBe("lineTool");
        expect(getTool("missing")).toBeUndefined();
    });

    it("derives toolForElementType from the registered descriptors", () => {
        registerTool(
            descriptor("lineTool", { produces: { store: ELEMENT_STORE_ID, type: "line" } }),
        );
        registerTool(
            descriptor("circleTool", { produces: { store: ELEMENT_STORE_ID, type: "circle" } }),
        );

        expect(toolForElementType("circle")?.id).toBe("circleTool");
        expect(toolForElementType("polygon")).toBeUndefined();
    });

    it("removes exactly its own entry when the disposer runs", () => {
        const dispose = registerTool(descriptor("lineTool"));
        registerTool(descriptor("circleTool"));

        dispose();

        expect(getTool("lineTool")).toBeUndefined();
        expect(getTool("circleTool")?.id).toBe("circleTool");
    });
});

describe("a tool's declared product", () => {
    it("accepts a product in the element store, a subject store or a namespaced session type", () => {
        registerTool(
            descriptor("lineTool", { produces: { store: ELEMENT_STORE_ID, type: "line" } }),
        );
        registerTool(descriptor("regionTool", { produces: { store: "regions", type: "region" } }));
        registerTool(descriptor("rulerTool", { produces: { store: "session", type: "m:ruler" } }));

        expect(toolForElementType("line")?.id).toBe("lineTool");
        expect(getTool("regionTool")?.produces).toEqual({ store: "regions", type: "region" });
        expect(getTool("rulerTool")).toBeDefined();
    });

    it("resolves no tool by element type for a product outside the element store", () => {
        registerTool(descriptor("regionTool", { produces: { store: "regions", type: "polygon" } }));

        expect(toolForElementType("polygon")).toBeUndefined();
    });

    it.each([
        ["an empty element type", { produces: { store: ELEMENT_STORE_ID, type: "" } }],
        ["an un-namespaced session type", { produces: { store: "session", type: "ruler" } }],
        [
            "style on a product that is not an element",
            {
                produces: { store: "session", type: "m:ruler" },
                style: [
                    {
                        label: "Color",
                        input: { type: "swatch" as const, defaultValue: "#fff", onChange: noop },
                    },
                ],
            },
        ],
    ])("refuses %s", (_, fields) => {
        expect(() => registerTool(descriptor("badTool", fields))).toThrow(
            'refused: tool "badTool"',
        );
        expect(getTool("badTool")).toBeUndefined();
    });
});

/**
 * `toolForElementType` assumes the element-type → tool map is a partition. It is,
 * for the subjects installed today, but nothing enforces it — so the ambiguity is
 * surfaced rather than arbitrated. Declared ownership (one answer shared with
 * renderer resolution) is a follow-up; these pin what the registry does until then.
 */
describe("a tool's product mapper", () => {
    const draft = { key: "d", type: "test:ruler", geometry: {} } as never;
    const units = {} as never;

    it("is refused for a product the session store does not hold", () => {
        expect(() =>
            registerTool(
                descriptor("lineTool", {
                    produces: { store: ELEMENT_STORE_ID, type: "line" },
                    mapProduct: () => [],
                }),
            ),
        ).toThrow(/mapProduct is only for a product the session store holds/);
    });

    it("is contained: a throw draws nothing", () => {
        vi.spyOn(Logger.get("contributions"), "error").mockImplementation(noop);
        registerTool(
            descriptor("rulerTool", {
                produces: { store: SESSION_STORE_ID, type: "test:ruler" },
                mapProduct: () => {
                    throw new Error("boom");
                },
            }),
        );

        expect(getTool("rulerTool")!.mapProduct!(draft, units)).toEqual([]);
    });
});

describe("element-type claims are surfaced, not arbitrated", () => {
    let warnings: string[];

    beforeEach(() => {
        warnings = [];
        vi.spyOn(Logger.get("toolbar"), "warn").mockImplementation((...args: unknown[]) => {
            warnings.push(args.map(String).join(" "));
        });
    });
    afterEach(() => vi.restoreAllMocks());

    it("warns when a second descriptor claims an element type, naming both", () => {
        registerTool(
            descriptor("symbolTool", { produces: { store: ELEMENT_STORE_ID, type: "symbol" } }),
        );
        registerTool(
            descriptor("otherSymbolTool", {
                produces: { store: ELEMENT_STORE_ID, type: "symbol" },
            }),
        );

        const warning = warnings.find((w) => w.includes("already claimed"));
        expect(warning).toBeDefined();
        expect(warning).toContain("otherSymbolTool");
        expect(warning).toContain("symbolTool");
    });

    it("resolves the first claimant, so the second silently loses the editor", () => {
        registerTool(
            descriptor("symbolTool", { produces: { store: ELEMENT_STORE_ID, type: "symbol" } }),
        );
        registerTool(
            descriptor("otherSymbolTool", {
                produces: { store: ELEMENT_STORE_ID, type: "symbol" },
            }),
        );

        expect(toolForElementType("symbol")?.id).toBe("symbolTool");
    });

    it("does not warn for descriptors with no element type", () => {
        registerTool(descriptor("popoverA"));
        registerTool(descriptor("popoverB"));

        expect(warnings.filter((w) => w.includes("already claimed"))).toHaveLength(0);
    });

    it("does not warn when two descriptors claim different types", () => {
        registerTool(
            descriptor("lineTool", { produces: { store: ELEMENT_STORE_ID, type: "line" } }),
        );
        registerTool(
            descriptor("circleTool", { produces: { store: ELEMENT_STORE_ID, type: "circle" } }),
        );

        expect(warnings.filter((w) => w.includes("already claimed"))).toHaveLength(0);
    });
});

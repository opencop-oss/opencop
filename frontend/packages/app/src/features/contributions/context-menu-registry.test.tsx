// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { nativeOwner } from "~/features/contributions/contribution-guard";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import { render, screen } from "~/test";
import type { ContextMenuItemContribution } from "@opencop/contributions";
import { ELEMENT_STORE_ID } from "@opencop/tools";
import {
    getContextMenuItemCount,
    registerContextMenuItem,
    resolveContextMenuItems,
} from "./context-menu-registry";
import { __resetContributionFailureLog } from "./contribution-guard";

const disposers: Array<() => void> = [];
const register = (item: ContextMenuItemContribution) =>
    disposers.push(registerContextMenuItem(nativeOwner("opencop.test"), item));

const action = (id: string, target: ContextMenuItemContribution["target"], extra = {}) =>
    ({
        id,
        target,
        labelKey: "k",
        activate: { toolId: "t" },
        ...extra,
    }) as ContextMenuItemContribution;

const ids = (at: Parameters<typeof resolveContextMenuItems>[0]) =>
    resolveContextMenuItems(at).map((item) => item.id);

beforeEach(() => {
    __resetContributionFailureLog();
    vi.spyOn(Logger.get("plugins"), "error").mockImplementation(() => undefined);
});

afterEach(() => {
    for (const dispose of disposers.splice(0)) dispose();
    vi.restoreAllMocks();
    expect(getContextMenuItemCount()).toBe(0);
});

describe("context-menu items", () => {
    it("shows ground items on empty map and object items on the type they name", () => {
        register(action("ground", { kind: "ground" }));
        register(action("angle-late", { kind: "object", type: "angle" }, { order: 20 }));
        register(
            action(
                "angle-early",
                { kind: "object", store: ELEMENT_STORE_ID, type: "angle" },
                {
                    order: 10,
                },
            ),
        );

        expect(ids({ historical: false })).toEqual(["ground"]);
        expect(ids({ type: "angle", historical: false })).toEqual(["angle-early", "angle-late"]);
        expect(ids({ type: "line", historical: false })).toEqual([]);
    });

    it("hides an item that changes data in historical mode", () => {
        register(action("drop", { kind: "ground" }, { mutates: true }));
        register(action("copy", { kind: "ground" }));

        expect(ids({ historical: true })).toEqual(["copy"]);
        expect(ids({ historical: false })).toEqual(["drop", "copy"]);
    });

    it.each([
        [{ id: "", target: { kind: "ground" } }, "id missing"],
        [{ id: "x", target: { kind: "sky" } }, "target.kind unknown"],
        [{ id: "x", target: { kind: "ground" }, labelKey: "k" }, "activate.toolId missing"],
        [{ id: "x", target: { kind: "ground" }, Content: "div" }, "Content is not a component"],
        [{ id: "x", target: { kind: "object" } }, "target.type missing"],
        [
            { id: "x", target: { kind: "object", store: "watchboxes", type: "zone" } },
            'target.store "watchboxes" does not open the menu',
        ],
    ])("refuses %o", (item, error) => {
        expect(() => registerContextMenuItem(nativeOwner("opencop.test"), item as never)).toThrow(
            error,
        );
    });

    it("contains a body that throws: the menu's other items survive", () => {
        register({
            id: "broken",
            target: { kind: "ground" },
            Content: () => {
                throw new Error("body broke");
            },
        });
        const [item] = resolveContextMenuItems({ historical: false });
        vi.spyOn(console, "error").mockImplementation(() => undefined);
        const SIBLING = "sibling";

        render(
            <div>
                <span>{SIBLING}</span>
                {"Content" in item && <item.Content target={{ lngLat: [0, 0] }} close={vi.fn()} />}
            </div>,
        );

        expect(screen.getByText(SIBLING)).toBeInTheDocument();
    });
});

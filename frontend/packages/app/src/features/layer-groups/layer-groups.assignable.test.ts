// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { assignableLayerGroups, isAssignableLayerGroup } from "./layer-groups.util";

const privateGroup = { id: "g1", accessScope: "PRIVATE" as const };
const globalGroup = { id: "g2", accessScope: "GLOBAL" as const };

describe("isAssignableLayerGroup", () => {
    it("lets a GLOBAL layer into either scope of group", () => {
        expect(isAssignableLayerGroup(globalGroup, "GLOBAL")).toBe(true);
        expect(isAssignableLayerGroup(privateGroup, "GLOBAL")).toBe(true);
    });

    it("keeps a PRIVATE layer out of a GLOBAL group", () => {
        expect(isAssignableLayerGroup(globalGroup, "PRIVATE")).toBe(false);
    });

    // The server rejects only non-global-into-global; a PRIVATE group takes anything. The older
    // same-scope-only client filter was stricter than this.
    it("lets a PRIVATE layer into a PRIVATE group", () => {
        expect(isAssignableLayerGroup(privateGroup, "PRIVATE")).toBe(true);
    });
});

describe("assignableLayerGroups", () => {
    const groups = [privateGroup, globalGroup];

    it("keeps both groups when every layer is GLOBAL", () => {
        expect(assignableLayerGroups(groups, ["GLOBAL", "GLOBAL"])).toEqual(groups);
    });

    it("drops GLOBAL groups when any layer is PRIVATE", () => {
        expect(assignableLayerGroups(groups, ["GLOBAL", "PRIVATE"])).toEqual([privateGroup]);
    });

    it("returns nothing for an empty selection", () => {
        expect(assignableLayerGroups(groups, [])).toEqual([]);
    });
});

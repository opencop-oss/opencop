// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { groupIssues } from "./issue-list.util";

function error(
    code: string,
    overrides?: { ruleId?: string; recordIndex?: number; sectionKey?: string },
) {
    return { code, message: "msg", ...overrides };
}

describe("groupIssues", () => {
    it("collapses duplicates by code+ruleId+sectionKey, collecting record indices", () => {
        const groups = groupIssues([
            error("ERR_A", { ruleId: "r1", recordIndex: 0 }),
            error("ERR_A", { ruleId: "r1", recordIndex: 1 }),
            error("ERR_A", { ruleId: "r1", recordIndex: 5 }),
        ]);
        expect(groups).toHaveLength(1);
        expect(groups[0]).toMatchObject({ count: 3, recordIndices: [0, 1, 5] });
    });

    it("keeps groups separate when any key component differs", () => {
        const groups = groupIssues([
            error("ERR_A", { ruleId: "r1", recordIndex: 0 }),
            error("ERR_B", { ruleId: "r1", recordIndex: 0 }), // different code
            error("ERR_A", { ruleId: "r2", recordIndex: 0 }), // different ruleId
            error("ERR_A", { sectionKey: "geometry", recordIndex: 0 }), // different sectionKey
        ]);
        expect(groups).toHaveLength(4);
    });

    it("produces empty recordIndices when recordIndex is undefined", () => {
        const groups = groupIssues([error("ERR_A")]);
        expect(groups[0]).toMatchObject({ count: 1, recordIndices: [] });
    });

    it("treats missing ruleId as empty string for grouping", () => {
        const groups = groupIssues([
            error("ERR_A", { recordIndex: 0 }),
            error("ERR_A", { recordIndex: 1 }),
        ]);
        expect(groups).toHaveLength(1);
        expect(groups[0].count).toBe(2);
    });
});

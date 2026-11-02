// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { canAssignLayerAsNonAdmin } from "./layer-groups.util";

describe("canAssignLayerAsNonAdmin", () => {
    it("allows a PRIVATE layer the caller can edit or better", () => {
        expect(canAssignLayerAsNonAdmin({ accessScope: "PRIVATE", role: "OWNER" })).toBe(true);
        expect(canAssignLayerAsNonAdmin({ accessScope: "PRIVATE", role: "ADMIN" })).toBe(true);
        expect(canAssignLayerAsNonAdmin({ accessScope: "PRIVATE", role: "EDITOR" })).toBe(true);
    });

    it("rejects a PRIVATE layer the caller only holds MEMBER on", () => {
        expect(canAssignLayerAsNonAdmin({ accessScope: "PRIVATE", role: "MEMBER" })).toBe(false);
    });

    // Every user resolves to MEMBER on every GLOBAL layer via the server's access-scope fallback,
    // so offering these would let anyone redistribute them through a group they own.
    it("rejects a GLOBAL layer regardless of role", () => {
        expect(canAssignLayerAsNonAdmin({ accessScope: "GLOBAL", role: "MEMBER" })).toBe(false);
        expect(canAssignLayerAsNonAdmin({ accessScope: "GLOBAL", role: "EDITOR" })).toBe(false);
        expect(canAssignLayerAsNonAdmin({ accessScope: "GLOBAL", role: "OWNER" })).toBe(false);
    });
});

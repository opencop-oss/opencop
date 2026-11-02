// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { isLayerRoleAtLeast, weakestLayerRole } from "./layers.util";

describe("isLayerRoleAtLeast", () => {
    it("treats a role as at least itself", () => {
        expect(isLayerRoleAtLeast("EDITOR", "EDITOR")).toBe(true);
    });

    it("ranks OWNER above every other role", () => {
        expect(isLayerRoleAtLeast("OWNER", "ADMIN")).toBe(true);
        expect(isLayerRoleAtLeast("OWNER", "EDITOR")).toBe(true);
        expect(isLayerRoleAtLeast("OWNER", "MEMBER")).toBe(true);
    });

    it("ranks MEMBER below every other role", () => {
        expect(isLayerRoleAtLeast("MEMBER", "EDITOR")).toBe(false);
        expect(isLayerRoleAtLeast("MEMBER", "ADMIN")).toBe(false);
        expect(isLayerRoleAtLeast("MEMBER", "OWNER")).toBe(false);
    });

    it("puts EDITOR between ADMIN and MEMBER", () => {
        expect(isLayerRoleAtLeast("EDITOR", "MEMBER")).toBe(true);
        expect(isLayerRoleAtLeast("EDITOR", "ADMIN")).toBe(false);
    });
});

describe("weakestLayerRole", () => {
    it("returns the weakest role in the set", () => {
        expect(weakestLayerRole(["OWNER", "EDITOR", "ADMIN"])).toBe("EDITOR");
        expect(weakestLayerRole(["ADMIN", "MEMBER"])).toBe("MEMBER");
    });

    it("returns the only role when there is one", () => {
        expect(weakestLayerRole(["ADMIN"])).toBe("ADMIN");
    });

    // No selection means no ceiling to impose, so callers fall back to the full option list.
    it("returns undefined for an empty set", () => {
        expect(weakestLayerRole([])).toBeUndefined();
    });

    it("is order-independent", () => {
        expect(weakestLayerRole(["MEMBER", "OWNER"])).toBe(weakestLayerRole(["OWNER", "MEMBER"]));
    });
});

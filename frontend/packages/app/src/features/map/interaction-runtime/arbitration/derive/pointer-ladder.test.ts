// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { POINTER_ORDER, resolvePointerOwner } from "./pointer-ladder";

/**
 * The pointer order, pinned: the session over an open surface over ambient.
 * The ladder ranks slots; what an owner binds is its own business.
 */

describe("resolvePointerOwner — the order", () => {
    it("1. a live session owns the pointer over an open surface", () => {
        expect(resolvePointerOwner({ session: true, surface: true, ambient: true })).toBe(
            "session",
        );
    });

    it("2. an open surface owns it when no session is live", () => {
        expect(resolvePointerOwner({ session: false, surface: true, ambient: true })).toBe(
            "surface",
        );
    });

    it("3. ambient owns it when nothing else does", () => {
        expect(resolvePointerOwner({ session: false, surface: false, ambient: true })).toBe(
            "ambient",
        );
    });

    it("nobody owns it before the composition point registers the owners", () => {
        expect(resolvePointerOwner({ session: false, surface: false, ambient: false })).toBeNull();
    });

    it("the order is the three slots, session first", () => {
        expect(POINTER_ORDER).toEqual(["session", "surface", "ambient"]);
    });
});

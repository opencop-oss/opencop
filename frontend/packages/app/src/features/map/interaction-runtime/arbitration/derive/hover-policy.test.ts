// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { deriveHoverPolicy } from "./hover-policy";

/** Hover clearing is withheld while a surface owns the pointer, and only then. */

describe("deriveHoverPolicy", () => {
    it("withholds a hover clear while a surface owns the pointer", () => {
        expect(deriveHoverPolicy("surface")).toEqual({ suppressClear: true });
    });

    it("applies hover clears under every other owner, and before any owner is registered", () => {
        expect(deriveHoverPolicy("session")).toEqual({ suppressClear: false });
        expect(deriveHoverPolicy("ambient")).toEqual({ suppressClear: false });
        expect(deriveHoverPolicy(null)).toEqual({ suppressClear: false });
    });
});

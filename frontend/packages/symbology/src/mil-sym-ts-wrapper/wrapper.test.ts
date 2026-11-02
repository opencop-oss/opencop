// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, beforeEach } from "vitest";
import { isMultiPoint, renderIcon, _resetIconCacheForTests } from "./wrapper";
import { FRIEND_INFANTRY, GARBAGE } from "../test-fixtures";

describe("milstd wrapper", () => {
    beforeEach(() => {
        _resetIconCacheForTests();
    });

    describe("isMultiPoint", () => {
        it("returns false (not throws) for garbage input", () => {
            expect(isMultiPoint(GARBAGE)).toBe(false);
        });
    });

    describe("renderIcon", () => {
        it("returns a populated WrapperIconResult for a renderable SIDC", () => {
            const result = renderIcon(FRIEND_INFANTRY);
            expect(result).not.toBeNull();
            expect(result?.url).toMatch(/^data:/);
            expect(result?.width).toBeGreaterThan(0);
            expect(result?.height).toBeGreaterThan(0);
        });

        it("returns null (not an empty result) for an unrenderable SIDC", () => {
            expect(renderIcon(GARBAGE)).toBeNull();
        });

        it("caches the null result so unrenderable SIDCs don't re-hit the renderer", () => {
            expect(renderIcon(GARBAGE)).toBeNull();
            expect(renderIcon(GARBAGE)).toBeNull();
        });
    });
});

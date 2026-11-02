// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { access } from "./layer.util";

describe("access", () => {
    const objectInfo = { index: 2, data: [], target: [] };

    it("returns the static value for non-function accessors", () => {
        expect(access(42, { foo: 1 }, objectInfo)).toBe(42);
        expect(access([1, 2, 3], { foo: 1 }, objectInfo)).toEqual([1, 2, 3]);
    });

    it("invokes function accessors with the object and objectInfo", () => {
        const spy = vi.fn((d: { v: number }) => d.v * 2);
        const result = access(spy, { v: 5 }, objectInfo);
        expect(result).toBe(10);
        expect(spy).toHaveBeenCalledWith({ v: 5 }, objectInfo);
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi, type Mock } from "vitest";
import { forwardFillProps } from "./fill.prop";
import type { Accessor } from "@deck.gl/core";

type Wrapper = <T>(accessor: Accessor<unknown, T>) => Accessor<unknown, T>;
const makeWrap = (): Wrapper & Mock => {
    const spy = vi.fn((accessor: unknown) => accessor);
    return spy as unknown as Wrapper & Mock;
};

describe("forwardFillProps", () => {
    it("passes filled through (no wrap → raw accessor passes through)", () => {
        const result = forwardFillProps({
            filled: true,
            getFillColor: [10, 20, 30, 255] as [number, number, number, number],
        });

        expect(result).toEqual({
            filled: true,
            getFillColor: [10, 20, 30, 255],
        });
    });

    it("wraps getFillColor when wrap is provided", () => {
        const wrap = makeWrap();
        forwardFillProps(
            { filled: true, getFillColor: [10, 20, 30, 255] as [number, number, number, number] },
            wrap,
        );

        expect(wrap).toHaveBeenCalledTimes(1);
        expect(wrap).toHaveBeenCalledWith([10, 20, 30, 255]);
    });
});

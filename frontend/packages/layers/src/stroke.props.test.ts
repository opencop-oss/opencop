// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi, type Mock } from "vitest";
import { forwardStrokeProps, forwardStrokeTriggers } from "./stroke.props";
import type { Accessor } from "@deck.gl/core";

type Wrapper = <T>(accessor: Accessor<unknown, T>) => Accessor<unknown, T>;
const makeWrap = (): Wrapper & Mock => {
    const spy = vi.fn((accessor: unknown) => accessor);
    return spy as unknown as Wrapper & Mock;
};

describe("forwardStrokeProps", () => {
    const fullProps = {
        stroked: true,
        dashed: false,
        strokeWidthUnits: "meters" as const,
        strokeWidthScale: 2,
        strokeWidthMinPixels: 1,
        strokeWidthMaxPixels: 100,
        jointRounded: true,
        capRounded: true,
        miterLimit: 8,
        strokeBillboard: true,
        getStrokeColor: [1, 2, 3, 4] as [number, number, number, number],
        getStrokeWidth: 3,
        getDashArray: [4, 4] as [number, number],
    };

    it("renames stroke* props to deck.gl width* names; accessors pass through unwrapped", () => {
        const result = forwardStrokeProps(fullProps);

        expect(result).toEqual({
            widthUnits: "meters",
            widthScale: 2,
            widthMinPixels: 1,
            widthMaxPixels: 100,
            jointRounded: true,
            capRounded: true,
            miterLimit: 8,
            billboard: true,
            getColor: [1, 2, 3, 4],
            getWidth: 3,
            getDashArray: [4, 4],
        });
    });

    it("wraps each accessor when wrap is provided", () => {
        const wrap = makeWrap();
        const result = forwardStrokeProps(fullProps, wrap);

        expect(wrap).toHaveBeenCalledWith(fullProps.getStrokeColor);
        expect(wrap).toHaveBeenCalledWith(fullProps.getStrokeWidth);
        expect(wrap).toHaveBeenCalledWith(fullProps.getDashArray);
        expect(wrap).toHaveBeenCalledTimes(3);

        expect(result).toMatchObject({
            getColor: fullProps.getStrokeColor,
            getWidth: fullProps.getStrokeWidth,
            getDashArray: fullProps.getDashArray,
        });
    });
});

describe("forwardStrokeTriggers", () => {
    it("renames triggers from stroke* to width/color names", () => {
        const result = forwardStrokeTriggers({
            getStrokeWidth: true,
            getStrokeColor: true,
            getDashArray: false,
        });

        expect(result).toEqual({
            getWidth: true,
            getColor: true,
            getDashArray: false,
        });
    });
});

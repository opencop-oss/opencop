// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi, type Mock } from "vitest";
import { forwardIconProps, forwardIconTriggers } from "./icon.props";
import type { Accessor } from "@deck.gl/core";

type Wrapper = <T>(accessor: Accessor<unknown, T>) => Accessor<unknown, T>;
const makeWrap = (): Wrapper & Mock => {
    const spy = vi.fn((accessor: unknown) => accessor);
    return spy as unknown as Wrapper & Mock;
};

describe("forwardIconProps", () => {
    const fullProps = {
        icon: true,
        iconBillboard: false,
        iconSizeUnits: "meters" as const,
        iconSizeScale: 3,
        iconSizeMinPixels: 4,
        iconSizeMaxPixels: 200,
        iconSizeBasis: "width" as const,
        iconAlphaCutoff: 0.1,
        getIcon: vi.fn(() => "marker"),
        getIconColor: [1, 2, 3, 4] as [number, number, number, number],
        getIconSize: 5,
        getIconAngle: 90,
        getIconPixelOffset: [2, 3] as [number, number],
    };

    it("renames icon* props to deck.gl IconLayer names; accessors pass through unwrapped", () => {
        const result = forwardIconProps(fullProps);

        expect(result).toEqual({
            billboard: false,
            sizeUnits: "meters",
            sizeScale: 3,
            sizeMinPixels: 4,
            sizeMaxPixels: 200,
            sizeBasis: "width",
            alphaCutoff: 0.1,
            getIcon: fullProps.getIcon,
            getColor: [1, 2, 3, 4],
            getSize: 5,
            getAngle: 90,
            getPixelOffset: [2, 3],
        });
    });

    it("wraps every accessor when wrap is provided", () => {
        const wrap = makeWrap();
        const result = forwardIconProps(fullProps, wrap);

        expect(wrap).toHaveBeenCalledWith(fullProps.getIcon);
        expect(wrap).toHaveBeenCalledWith(fullProps.getIconColor);
        expect(wrap).toHaveBeenCalledWith(fullProps.getIconSize);
        expect(wrap).toHaveBeenCalledWith(fullProps.getIconAngle);
        expect(wrap).toHaveBeenCalledWith(fullProps.getIconPixelOffset);
        expect(wrap).toHaveBeenCalledTimes(5);

        expect(result).toMatchObject({
            getIcon: fullProps.getIcon,
            getColor: fullProps.getIconColor,
            getSize: fullProps.getIconSize,
            getAngle: fullProps.getIconAngle,
            getPixelOffset: fullProps.getIconPixelOffset,
        });
    });

    it("omits getIcon when input getIcon is undefined", () => {
        const wrap = makeWrap();
        const result = forwardIconProps({ icon: true }, wrap);
        expect(result.getIcon).toBeUndefined();
    });
});

describe("forwardIconTriggers", () => {
    it("renames triggers correctly", () => {
        const result = forwardIconTriggers({
            getIcon: true,
            getIconColor: true,
            getIconSize: false,
            getIconAngle: true,
            getIconPixelOffset: false,
        });

        expect(result).toEqual({
            getIcon: true,
            getColor: true,
            getSize: false,
            getAngle: true,
            getPixelOffset: false,
        });
    });
});

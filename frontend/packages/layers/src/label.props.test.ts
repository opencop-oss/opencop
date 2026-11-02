// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi, type Mock } from "vitest";
import { forwardLabelProps, forwardLabelTriggers } from "./label.props";
import type { Accessor } from "@deck.gl/core";

type Wrapper = <T>(accessor: Accessor<unknown, T>) => Accessor<unknown, T>;
const makeWrap = (): Wrapper & Mock => {
    const spy = vi.fn((accessor: unknown) => accessor);
    return spy as unknown as Wrapper & Mock;
};

describe("forwardLabelProps", () => {
    const fullProps = {
        getLabel: vi.fn(() => "hi"),
        getLabelColor: [1, 2, 3, 4] as [number, number, number, number],
        getLabelSize: 12,
        getLabelBackgroundColor: [5, 5, 5, 255] as [number, number, number, number],
        getLabelBorderColor: [6, 6, 6, 255] as [number, number, number, number],
        getLabelBorderWidth: 1,
        getLabelPixelOffset: [7, 8] as [number, number],
    };

    it("renames label accessors to deck.gl TextLayer accessor shape", () => {
        const result = forwardLabelProps(fullProps);

        expect(result).toEqual({
            getText: fullProps.getLabel,
            getColor: [1, 2, 3, 4],
            getSize: 12,
            getBackgroundColor: [5, 5, 5, 255],
            getBorderColor: [6, 6, 6, 255],
            getBorderWidth: 1,
            getPixelOffset: [7, 8],
        });
    });

    it("wraps every label accessor when wrap is provided", () => {
        const wrap = makeWrap();
        const result = forwardLabelProps(fullProps, wrap);

        expect(wrap).toHaveBeenCalledWith(fullProps.getLabel);
        expect(wrap).toHaveBeenCalledWith(fullProps.getLabelColor);
        expect(wrap).toHaveBeenCalledWith(fullProps.getLabelSize);
        expect(wrap).toHaveBeenCalledWith(fullProps.getLabelBackgroundColor);
        expect(wrap).toHaveBeenCalledWith(fullProps.getLabelBorderColor);
        expect(wrap).toHaveBeenCalledWith(fullProps.getLabelBorderWidth);
        expect(wrap).toHaveBeenCalledWith(fullProps.getLabelPixelOffset);
        expect(wrap).toHaveBeenCalledTimes(7);

        expect(result).toMatchObject({
            getText: fullProps.getLabel,
            getColor: fullProps.getLabelColor,
            getSize: fullProps.getLabelSize,
            getBackgroundColor: fullProps.getLabelBackgroundColor,
            getBorderColor: fullProps.getLabelBorderColor,
            getBorderWidth: fullProps.getLabelBorderWidth,
            getPixelOffset: fullProps.getLabelPixelOffset,
        });
    });
});

describe("forwardLabelTriggers", () => {
    it("renames triggers from label* to text/color names", () => {
        const result = forwardLabelTriggers({
            getLabel: false,
            getLabelColor: true,
            getLabelSize: false,
            getLabelBackgroundColor: true,
            getLabelBorderColor: false,
            getLabelBorderWidth: true,
            getLabelPixelOffset: true,
        });

        expect(result).toEqual({
            getText: false,
            getColor: true,
            getSize: false,
            getBackgroundColor: true,
            getBorderColor: false,
            getBorderWidth: true,
            getPixelOffset: true,
        });
    });
});

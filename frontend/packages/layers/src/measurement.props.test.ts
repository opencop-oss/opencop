// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi, type Mock } from "vitest";
import { forwardMeasurementProps, forwardMeasurementTriggers } from "./measurement.props";
import type { Accessor } from "@deck.gl/core";

type Wrapper = <T>(accessor: Accessor<unknown, T>) => Accessor<unknown, T>;
const makeWrap = (): Wrapper & Mock => {
    const spy = vi.fn((accessor: unknown) => accessor);
    return spy as unknown as Wrapper & Mock;
};

describe("forwardMeasurementProps", () => {
    const fullProps = {
        measurementSystem: "METRIC" as const,
        getMeasurementColor: [1, 2, 3, 4] as [number, number, number, number],
        getMeasurementSize: 14,
        getMeasurementBackgroundColor: [9, 9, 9, 255] as [number, number, number, number],
        getMeasurementBorderColor: [8, 8, 8, 255] as [number, number, number, number],
        getMeasurementBorderWidth: 1,
    };

    it("renames measurement accessors to TextLayer accessor shape; non-accessor props are dropped", () => {
        const result = forwardMeasurementProps(fullProps);

        expect(result).toEqual({
            getColor: [1, 2, 3, 4],
            getSize: 14,
            getBackgroundColor: [9, 9, 9, 255],
            getBorderColor: [8, 8, 8, 255],
            getBorderWidth: 1,
        });
    });

    it("wraps every measurement accessor when wrap is provided", () => {
        const wrap = makeWrap();
        const result = forwardMeasurementProps(fullProps, wrap);

        expect(wrap).toHaveBeenCalledWith(fullProps.getMeasurementColor);
        expect(wrap).toHaveBeenCalledWith(fullProps.getMeasurementSize);
        expect(wrap).toHaveBeenCalledWith(fullProps.getMeasurementBackgroundColor);
        expect(wrap).toHaveBeenCalledWith(fullProps.getMeasurementBorderColor);
        expect(wrap).toHaveBeenCalledWith(fullProps.getMeasurementBorderWidth);
        expect(wrap).toHaveBeenCalledTimes(5);

        expect(result).toMatchObject({
            getColor: fullProps.getMeasurementColor,
            getSize: fullProps.getMeasurementSize,
            getBackgroundColor: fullProps.getMeasurementBackgroundColor,
            getBorderColor: fullProps.getMeasurementBorderColor,
            getBorderWidth: fullProps.getMeasurementBorderWidth,
        });
    });
});

describe("forwardMeasurementTriggers", () => {
    it("renames triggers from measurement* names", () => {
        const result = forwardMeasurementTriggers({
            getMeasurementColor: true,
            getMeasurementSize: false,
            getMeasurementBackgroundColor: true,
            getMeasurementBorderColor: false,
            getMeasurementBorderWidth: true,
        });

        expect(result).toEqual({
            getColor: true,
            getSize: false,
            getBackgroundColor: true,
            getBorderColor: false,
            getBorderWidth: true,
        });
    });
});

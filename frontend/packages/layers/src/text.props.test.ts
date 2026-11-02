// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi, type Mock } from "vitest";
import {
    forwardMeasuredTextTriggers,
    forwardTextProps,
    measuredTextAccessors,
    type MeasuredTextDatum,
} from "./text.props";
import type { LabelProps } from "./label.props";
import type { MeasurementProps } from "./measurement.props";
import type { Accessor } from "@deck.gl/core";

type Wrapper = <T>(accessor: Accessor<unknown, T>) => Accessor<unknown, T>;
const identity = ((accessor: unknown) => accessor) as Wrapper;
const makeWrap = (): Wrapper & Mock => {
    const spy = vi.fn((accessor: unknown) => accessor);
    return spy as unknown as Wrapper & Mock;
};

const info = { index: 0, data: [], target: [] };
const call = (accessor: unknown, d: MeasuredTextDatum) =>
    (accessor as (d: MeasuredTextDatum, i: typeof info) => unknown)(d, info);

const styleProps: Required<MeasurementProps & LabelProps> = {
    // label family
    getLabel: () => "LBL",
    getLabelColor: [1, 1, 1, 1],
    getLabelSize: 10,
    getLabelBackgroundColor: [2, 2, 2, 2],
    getLabelBorderColor: [3, 3, 3, 3],
    getLabelBorderWidth: 1,
    getLabelPixelOffset: [5, 6],
    // measurement family
    getMeasurementColor: [4, 4, 4, 4],
    getMeasurementSize: 20,
    getMeasurementBackgroundColor: [7, 7, 7, 7],
    getMeasurementBorderColor: [8, 8, 8, 8],
    getMeasurementBorderWidth: 2,
    getMeasurementText: () => null,
    measurementSystem: "METRIC",
};

const label: MeasuredTextDatum = { kind: "label", position: [0, 0] };
const measurement: MeasuredTextDatum = {
    kind: "measurement",
    text: "MSR",
    position: [1, 1],
    angle: 45,
    anchor: "start",
};

describe("measuredTextAccessors", () => {
    const acc = measuredTextAccessors(styleProps, identity);

    it("reads geometry straight off the datum", () => {
        expect(call(acc.getPosition, label)).toEqual([0, 0]);
        expect(call(acc.getPosition, measurement)).toEqual([1, 1]);
    });

    it("resolves label text from getLabel, measurement text from the datum", () => {
        expect(call(acc.getText, label)).toBe("LBL");
        expect(call(acc.getText, measurement)).toBe("MSR");
    });

    it("suppresses measurement text when getMeasurementText returns empty, leaving labels alone", () => {
        const suppressed = measuredTextAccessors(
            { ...styleProps, getMeasurementText: () => "" },
            identity,
        );
        expect(call(suppressed.getText, measurement)).toBe("");
        expect(call(suppressed.getText, label)).toBe("LBL");
    });

    it("overrides measurement text when getMeasurementText returns a string", () => {
        const overridden = measuredTextAccessors(
            { ...styleProps, getMeasurementText: () => "OVR" },
            identity,
        );
        expect(call(overridden.getText, measurement)).toBe("OVR");
    });

    it("dispatches style accessors per kind", () => {
        expect(call(acc.getColor, label)).toEqual([1, 1, 1, 1]);
        expect(call(acc.getColor, measurement)).toEqual([4, 4, 4, 4]);

        expect(call(acc.getSize, label)).toBe(10);
        expect(call(acc.getSize, measurement)).toBe(20);

        expect(call(acc.getBackgroundColor, label)).toEqual([2, 2, 2, 2]);
        expect(call(acc.getBackgroundColor, measurement)).toEqual([7, 7, 7, 7]);

        expect(call(acc.getBorderColor, label)).toEqual([3, 3, 3, 3]);
        expect(call(acc.getBorderColor, measurement)).toEqual([8, 8, 8, 8]);

        expect(call(acc.getBorderWidth, label)).toBe(1);
        expect(call(acc.getBorderWidth, measurement)).toBe(2);
    });

    it("offsets labels from getLabelPixelOffset; measurements default to [0, 0]", () => {
        expect(call(acc.getPixelOffset, label)).toEqual([5, 6]);
        expect(call(acc.getPixelOffset, measurement)).toEqual([0, 0]);
    });

    it("derives angle, anchor, and billboard structurally", () => {
        expect(call(acc.getAngle, label)).toBe(0);
        expect(call(acc.getAngle, measurement)).toBe(45);

        expect(call(acc.getTextAnchor, label)).toBe("middle");
        expect(call(acc.getTextAnchor, measurement)).toBe("start");

        // Angled (measurement) text lies flat; angle-less (label) text billboards.
        expect(call(acc.getBillboard, label)).toBe(1);
        expect(call(acc.getBillboard, measurement)).toBe(0);
    });

    it("wraps each forwarded source accessor through the sub-layer wrapper", () => {
        const wrap = makeWrap();
        measuredTextAccessors(styleProps, wrap);

        expect(wrap).toHaveBeenCalledWith(styleProps.getLabel);
        expect(wrap).toHaveBeenCalledWith(styleProps.getLabelColor);
        expect(wrap).toHaveBeenCalledWith(styleProps.getMeasurementColor);
        expect(wrap).toHaveBeenCalledWith(styleProps.getLabelPixelOffset);
    });
});

describe("forwardMeasuredTextTriggers", () => {
    it("pairs each per-kind style trigger as [label, measurement]", () => {
        const result = forwardMeasuredTextTriggers({
            getLabelSize: true,
            getMeasurementSize: false,
            getLabelColor: true,
            getMeasurementColor: true,
            getLabelBackgroundColor: false,
            getMeasurementBackgroundColor: true,
            getLabelBorderWidth: false,
            getMeasurementBorderWidth: true,
            getLabelBorderColor: true,
            getMeasurementBorderColor: false,
            getLabelPixelOffset: true,
        });

        expect(result).toEqual({
            getSize: [true, false],
            getColor: [true, true],
            getBackgroundColor: [false, true],
            getBorderWidth: [false, true],
            getBorderColor: [true, false],
            getPixelOffset: true,
        });
    });
});

describe("forwardTextProps", () => {
    it("renames shared text uniforms to deck.gl TextLayer prop names", () => {
        const result = forwardTextProps({
            textSizeScale: 2,
            textSizeUnits: "meters",
            textOutlineWidth: 3,
            textBackground: true,
            fontFamily: "Arial",
        });

        expect(result).toMatchObject({
            sizeScale: 2,
            sizeUnits: "meters",
            outlineWidth: 3,
            background: true,
            fontFamily: "Arial",
        });
    });
});

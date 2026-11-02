// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { Color } from "./primitive";
import {
    LABEL_COLOR,
    MEASUREMENT_COLOR,
    TEXT_SIZE,
    angle,
    hexaToRgba,
    icon,
    interleave,
    label,
    measurement,
    path,
    point,
    polygon,
    text,
} from "./primitive.util";

const red: Color = [255, 0, 0, 255];
const positions: [number, number, number] = [1, 2, 0];
const line = [0, 0, 0, 1, 1, 0];

describe("primitive factories", () => {
    it("tag the geometry and style with their types, keeping every field", () => {
        expect(
            polygon({ id: "a", geometry: { positions: line }, style: { fillColor: red } }),
        ).toEqual({
            id: "a",
            geometry: { type: "polygon", positions: line },
            style: { type: "polygon", fillColor: red },
        });
        expect(
            path({
                id: "b",
                geometry: { positions: line },
                style: { strokeColor: red, strokeWidth: 2 },
            }),
        ).toEqual({
            id: "b",
            geometry: { type: "path", positions: line },
            style: { type: "path", strokeColor: red, strokeWidth: 2 },
        });
    });

    it("place point-like primitives on point geometry", () => {
        const geometry = { positions };
        const types = (style: string) => ({ geometry: { type: "point" }, style: { type: style } });

        const pointStyle = { fillColor: red, strokeColor: red, strokeWidth: 1, radius: 4 };
        expect(point({ id: "p", geometry, style: pointStyle })).toMatchObject(types("point"));

        const iconStyle = { id: "x", source: "x.png", width: 8, height: 8, billboard: true };
        expect(icon({ id: "i", geometry, style: iconStyle })).toMatchObject(types("icon"));

        const textStyle = { text: "hi", size: 12, color: red };
        expect(text({ id: "t", geometry, style: textStyle })).toMatchObject(types("text"));

        const angleStyle = {
            strokeColor: red,
            strokeWidth: 1,
            radius: 10,
            angles: [0, 90] as [number, number],
        };
        expect(angle({ id: "g", geometry, style: angleStyle })).toMatchObject(types("angle"));
    });
});

describe("label", () => {
    it("defaults to a white, billboarded, unrotated label at the standard size", () => {
        expect(label({ id: "l", geometry: { positions }, style: { text: "A" } })).toEqual({
            id: "l",
            geometry: { type: "point", positions },
            style: {
                type: "text",
                text: "A",
                color: LABEL_COLOR,
                size: TEXT_SIZE,
                angle: 0,
                billboard: true,
            },
        });
    });

    it("lets the caller override rotation, billboarding and placement", () => {
        const style = {
            text: "A",
            angle: 45,
            billboard: false,
            offset: [0, 10] as [number, number],
            anchor: "start" as const,
            baseline: "top" as const,
        };

        expect(label({ id: "l", geometry: { positions }, style }).style).toEqual({
            type: "text",
            color: LABEL_COLOR,
            size: TEXT_SIZE,
            ...style,
        });
    });
});

describe("measurement", () => {
    it("draws grey, flat, standard-size text at a point", () => {
        expect(measurement({ id: "m", geometry: { positions }, style: { text: "5m" } })).toEqual({
            id: "m",
            geometry: { type: "point", positions },
            style: {
                type: "text",
                text: "5m",
                color: MEASUREMENT_COLOR,
                size: TEXT_SIZE,
                billboard: false,
            },
        });
    });

    it("keeps the caller's placement", () => {
        const { style } = measurement({
            id: "m",
            geometry: { positions },
            style: { text: "5m", angle: 30, anchor: "end", baseline: "top", offset: [1, 2] },
        });

        expect(style).toMatchObject({ angle: 30, anchor: "end", baseline: "top", offset: [1, 2] });
    });
});

describe("interleave", () => {
    it("appends the element to each 2D position", () => {
        expect(interleave([1, 2, 3, 4])).toEqual([1, 2, 0, 3, 4, 0]);
        expect(interleave([1, 2, 3, 4], 5)).toEqual([1, 2, 5, 3, 4, 5]);
    });

    it("appends the element to each 3D position", () => {
        expect(interleave([1, 2, 3, 4, 5, 6], 9, 3)).toEqual([1, 2, 3, 9, 4, 5, 6, 9]);
    });

    it("returns nothing for no positions", () => {
        expect(interleave([])).toEqual([]);
    });
});

describe("hexaToRgba", () => {
    it("reads #RRGGBB as opaque by default", () => {
        expect(hexaToRgba("#ff8000")).toEqual([255, 128, 0, 255]);
        expect(hexaToRgba("#FF8000")).toEqual([255, 128, 0, 255]);
    });

    it("takes #RRGGBB alpha from the opacity", () => {
        expect(hexaToRgba("#ff8000", 0.2)).toEqual([255, 128, 0, 51]);
        expect(hexaToRgba("#ff8000", 0)).toEqual([255, 128, 0, 0]);
    });

    it("reads #RRGGBBAA alpha from the string, ignoring the opacity", () => {
        expect(hexaToRgba("#00ff0080")).toEqual([0, 255, 0, 128]);
        expect(hexaToRgba("#00ff0080", 0.2)).toEqual([0, 255, 0, 128]);
        expect(hexaToRgba("#00ff0000")).toEqual([0, 255, 0, 0]);
    });
});

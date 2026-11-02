// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { type Element } from "./Element";
import { elementFromFeature } from "./elementFromFeature";
import { ArrowStyle } from "./style/ArrowStyle";
import { IconStyle } from "./style/IconStyle";
import { PointStyle } from "./style/PointStyle";
import { ShapeStyle } from "./style/ShapeStyle";
import { StrokePattern, StrokeStyle } from "./style/StrokeStyle";
import { Angle } from "./types/Angle";
import { Arrow } from "./types/Arrow";
import { Circle } from "./types/Circle";
import { Ellipse } from "./types/Ellipse";
import { Graphic } from "./types/Graphic";
import { Line } from "./types/Line";
import { MediaIcon } from "./types/MediaIcon";
import { Point } from "./types/Point";
import { Polygon } from "./types/Polygon";
import { Rectangle } from "./types/Rectangle";
import { Symbol } from "./types/Symbol";
import { TextBox } from "./types/TextBox";

function meta(id: string) {
    return {
        id,
        objectId: `object-${id}`,
        source: `source-${id}`,
        displayName: `Display ${id}`,
        start: 1_000,
        time: 2_000,
        stale: 3_000,
        speed: 12,
        course: 45,
        heading: 60,
        hae: 250,
        attributes: {
            label: id,
            priority: 7,
        },
    };
}

function expectRoundTrip(element: Element): Element {
    const feature = element.toFeature();
    const reconstructed = elementFromFeature(feature);

    expect(reconstructed).toBeDefined();
    if (!reconstructed) throw new Error(`Expected ${element.type} to decode from feature`);

    expect(reconstructed.toFeature()).toEqual(feature);
    return reconstructed;
}

describe("elementFromFeature", () => {
    it("round-trips representative element types through GeoJSON features", () => {
        const elements = [
            Point.create({
                position: [1, 2],
                style: new PointStyle({ color: "#ff0000b3", radius: 9 }),
                meta: meta("point"),
            }),
            Circle.create({
                origin: [3, 4],
                radius: 500,
                style: new ShapeStyle({
                    fillColor: "#11223344",
                    strokeColor: "#556677ff",
                    strokePattern: StrokePattern.Dashed,
                    strokeWeight: 6,
                }),
                meta: meta("circle"),
            }),
            Ellipse.create({
                origin: [5, 6],
                major: 1200,
                minor: 700,
                bearing: 35,
                style: new ShapeStyle({
                    fillColor: "#44556677",
                    strokeColor: "#8899aaff",
                    strokePattern: StrokePattern.Dotted,
                    strokeWeight: 5,
                }),
                meta: meta("ellipse"),
            }),
            Line.create({
                positions: [0, 0, 1, 1, 2, 0],
                style: new StrokeStyle({
                    color: "#abcdef12",
                    pattern: StrokePattern.Dotted,
                    weight: 4,
                }),
                meta: meta("line"),
            }),
            Polygon.create({
                positions: [0, 0, 2, 0, 2, 2, 0, 2],
                style: new ShapeStyle({
                    fillColor: "#12345678",
                    strokeColor: "#90abcdef",
                    strokePattern: StrokePattern.Outlined,
                    strokeWeight: 3,
                }),
                meta: meta("polygon"),
            }),
            Rectangle.create({
                positions: [10, 10, 14, 10, 14, 13, 10, 13],
                style: new ShapeStyle({
                    fillColor: "#fedcba98",
                    strokeColor: "#765432ff",
                    strokePattern: StrokePattern.Solid,
                    strokeWeight: 2,
                }),
                meta: meta("rectangle"),
            }),
            Arrow.create({
                start: [1, 1],
                end: [2, 3],
                style: new ArrowStyle({
                    color: "#00ff88ff",
                    pattern: StrokePattern.Dashed,
                    weight: 5,
                    size: 21,
                }),
                meta: meta("arrow"),
            }),
            Angle.create({
                first: [0, 0],
                second: [1, 1],
                third: [2, 1],
                style: new StrokeStyle({
                    color: "#ffaa00ff",
                    pattern: StrokePattern.Dashed,
                    weight: 7,
                }),
                meta: meta("angle"),
            }),
            Graphic.create({
                sidc: "GFGPOLAGS-----X",
                controls: [0, 0, 1, 1, 2, 0],
                modifiers: {
                    uniqueDesignation: "Alpha",
                    staffComments: "Bravo",
                },
                meta: meta("graphic"),
            }),
            Symbol.create({
                position: [9, 8],
                sidc: "SFGPUCI----D",
                style: new IconStyle({ size: 25, angle: 90 }),
                meta: meta("symbol"),
            }),
        ];

        for (const element of elements) expectRoundTrip(element);
    });

    it("keeps a text box's ring as stored, whichever way its first edge runs", () => {
        const westward = [0.02, 0.01, 0, 0.01, 0, 0, 0.02, 0];
        const element = TextBox.create({ positions: westward, meta: meta("textbox") });

        const reconstructed = expectRoundTrip(element);

        expect(reconstructed.geometry.getAllPos()).toEqual(westward);
    });

    it("restores symbol style fields and the code, without resolving an icon", () => {
        const element = Symbol.create({
            position: [9, 8],
            sidc: "SFGPUCI----D",
            style: new IconStyle({ size: 25, angle: 90 }),
            meta: meta("symbol-style"),
        });

        const reconstructed = expectRoundTrip(element);

        expect(Symbol.is(reconstructed)).toBe(true);
        if (!Symbol.is(reconstructed)) throw new Error("Expected decoded element to be a symbol");

        expect(reconstructed.getStyle().getSize()).toBe(25);
        expect(reconstructed.getStyle().getAngle()).toBe(90);
        // The decoded element carries the code; the renderer turns it into pixels.
        expect(reconstructed.getCode()).toBe("SFGPUCI----D");
    });

    it("restores a media icon's href and writes no symbol code", () => {
        const href = "opencop:url:/v1/layers/abc/media/def#48x24";
        const element = MediaIcon.create({ position: [9, 8], href, meta: meta("media-icon") });

        const feature = element.toFeature();
        const wire = feature.properties.opencop.element as unknown as Record<string, unknown>;
        expect(wire["type"]).toBe("media-icon");
        expect(wire["href"]).toBe(href);
        expect("symbol" in wire).toBe(false);

        const reconstructed = expectRoundTrip(element);
        expect(MediaIcon.is(reconstructed)).toBe(true);
        if (!MediaIcon.is(reconstructed)) throw new Error("Expected a media icon");
        expect(MediaIcon.getHref(reconstructed)).toBe(href);
        expect(reconstructed.getCode()).toBeUndefined();
    });

    it("keeps a media icon's href on a clone", () => {
        const element = MediaIcon.create({ position: [0, 0], href: "https://example.com/a.png" });

        expect(MediaIcon.getHref(element.clone() as typeof element)).toBe(
            "https://example.com/a.png",
        );
    });

    it("preserves nullable motion fields during feature decode", () => {
        const element = Point.create({
            position: [4, 2],
            style: new PointStyle({ color: "#0044ffff", radius: 8 }),
            meta: {
                ...meta("point-nullable"),
                speed: null,
                course: null,
                heading: null,
            },
        });

        const reconstructed = expectRoundTrip(element);

        expect(reconstructed.speed).toBeNull();
        expect(reconstructed.course).toBeNull();
        expect(reconstructed.heading).toBeNull();
    });
});

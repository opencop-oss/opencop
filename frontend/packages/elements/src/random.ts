// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// --- Primitives ---
import type { Position, Position2D } from "./geometry/Geometry";
import { ArrowStyle } from "./style/ArrowStyle";
import { IconStyle } from "./style/IconStyle";
import { PointStyle } from "./style/PointStyle";
import { ShapeStyle } from "./style/ShapeStyle";
import { StrokePattern, StrokeStyle } from "./style/StrokeStyle";
import { Angle } from "./types/Angle";
import { Arrow } from "./types/Arrow";
import { Circle } from "./types/Circle";
import { Ellipse } from "./types/Ellipse";
import { Line } from "./types/Line";
import { Point } from "./types/Point";
import { Polygon } from "./types/Polygon";
import { Rectangle } from "./types/Rectangle";
import { Symbol } from "./types/Symbol";

function rand(min: number, max: number): number {
    return Math.random() * (max - min) + min;
}

function randomCoord(): Position2D {
    return [rand(-180, 180), rand(-85, 85)];
}

function randomCoords(number: number): number[] {
    const coords: number[] = [];
    for (let i = 0; i < number; i++) {
        coords.push(...randomCoord());
    }
    return coords;
}

function nearbyCoord(origin: Position, offsetDeg = 0.05): Position2D {
    return [origin[0] + rand(-offsetDeg, offsetDeg), origin[1] + rand(-offsetDeg, offsetDeg)];
}

function randomHex(): string {
    const r = Math.floor(rand(0, 256));
    const g = Math.floor(rand(0, 256));
    const b = Math.floor(rand(0, 256));
    const a = Math.floor(rand(128, 256));
    return (
        "#" +
        r.toString(16).padStart(2, "0") +
        g.toString(16).padStart(2, "0") +
        b.toString(16).padStart(2, "0") +
        a.toString(16).padStart(2, "0")
    );
}

function randomPattern(): StrokePattern {
    const patterns = [StrokePattern.Solid, StrokePattern.Dashed, StrokePattern.Dotted];
    return patterns[Math.floor(rand(0, patterns.length))];
}

// --- Styles ---

export function randomPointStyle(): PointStyle {
    return new PointStyle({ color: randomHex(), radius: rand(3, 20) });
}

export function randomStrokeStyle(): StrokeStyle {
    return new StrokeStyle({
        color: randomHex(),
        pattern: randomPattern(),
        weight: rand(1, 8),
    });
}

export function randomShapeStyle(): ShapeStyle {
    return new ShapeStyle({
        fillColor: randomHex(),
        strokeColor: randomHex(),
        strokePattern: randomPattern(),
        strokeWeight: rand(1, 6),
    });
}

export function randomArrowStyle(): ArrowStyle {
    return new ArrowStyle({
        color: randomHex(),
        pattern: randomPattern(),
        weight: rand(1, 8),
        size: rand(8, 30),
    });
}

export function randomSymbolStyle(): IconStyle {
    return new IconStyle({ angle: rand(0, 360), size: rand(20, 60) });
}

// --- Elements ---

export function randomPoint() {
    return Point.create({
        position: randomCoord(),
        style: randomPointStyle(),
    });
}

export function randomLine() {
    return Line.create({
        positions: randomCoords(rand(2, 10)),
        style: randomStrokeStyle(),
    });
}

export function randomCircle() {
    return Circle.create({
        origin: randomCoord(),
        radius: rand(10, 50_000),
        style: randomShapeStyle(),
    });
}

export function randomEllipse() {
    return Ellipse.create({
        origin: randomCoord(),
        major: rand(10, 50_000),
        minor: rand(10, 50_000),
        style: randomShapeStyle(),
    });
}

export function randomAngle() {
    const vertex = randomCoord();
    return Angle.create({
        first: nearbyCoord(vertex),
        second: vertex,
        third: nearbyCoord(vertex),
        style: randomStrokeStyle(),
    });
}

export function randomArrow() {
    const origin = randomCoord();
    return Arrow.create({
        start: origin,
        end: nearbyCoord(origin),
        style: randomArrowStyle(),
    });
}

export function randomPolygon() {
    const origin = randomCoord();
    const numVerts = Math.floor(rand(3, 9));
    const positions: Position2D[] = [];
    for (let i = 0; i < numVerts; i++) {
        positions.push(nearbyCoord(origin));
    }
    return Polygon.create({
        positions,
        style: randomShapeStyle(),
    });
}

export function randomRectangle() {
    const origin = randomCoord();
    return Rectangle.create({
        positions: [
            nearbyCoord(origin),
            nearbyCoord(origin),
            nearbyCoord(origin),
            nearbyCoord(origin),
        ],
        style: randomShapeStyle(),
    });
}

/**
 * A concrete SIDC for generated fixtures. Spelled out rather than pulled from
 * `@opencop/symbology` — this package does not interpret codes, and fixtures
 * need a real one so they render. Friendly land unit, no entity.
 */
const FIXTURE_SIDC = "10031000000000000000";

export function randomSymbol() {
    return Symbol.create({
        position: randomCoord(),
        sidc: FIXTURE_SIDC,
        style: randomSymbolStyle(),
    });
}

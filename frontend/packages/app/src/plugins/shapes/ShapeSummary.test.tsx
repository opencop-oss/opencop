// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import * as turf from "@turf/turf";
import { screen, within } from "@testing-library/react";
import {
    Angle,
    Arrow,
    Circle,
    Ellipse,
    Line,
    Polygon,
    Rectangle,
    type Position2D,
} from "@opencop/elements";
import {
    formatArea,
    formatDistance,
    summarizeCircle,
    summarizeLine,
    summarizePolygon,
} from "@opencop/utils";
import type { DisplayPreferences } from "@opencop/ui";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import { HOST_TRANSLATIONS } from "~/test/host-translations";
import { installShapes } from "./install-shapes";
import { ShapeSummary } from "./ShapeSummary";

/** Shapes installed in a test host, as the composition root installs it. */
const shapesHost = () =>
    createTestHost({
        id: "opencop.shapes",
        install: installShapes,
        translations: [HOST_TRANSLATIONS],
    });

// The summary reads the display preferences the host provides around the map.
function renderShape(
    element: Parameters<typeof ShapeSummary>[0]["element"],
    preferences: Partial<DisplayPreferences> = {},
) {
    return shapesHost().render(<ShapeSummary element={element} />, {
        capabilities: { displayPreferences: { measurementSystem: "METRIC", ...preferences } },
    });
}

describe("ShapeSummary", () => {
    it("shows radius, perimeter, and area for a circle", () => {
        const circle = Circle.create({ origin: [0, 0], radius: 1000 });
        renderShape(circle);

        const [area, perimeter] = summarizeCircle(circle.geometry, "METRIC");
        expect(screen.getByText(formatDistance(1000, "METRIC").display)).toBeInTheDocument();
        expect(screen.getByText(perimeter)).toBeInTheDocument();
        expect(screen.getByText(area)).toBeInTheDocument();
    });

    it("shows area and perimeter for a polygon", () => {
        const polygon = Polygon.create({
            positions: [
                [0, 0],
                [0, 0.01],
                [0.01, 0.01],
                [0.01, 0],
            ],
        });
        renderShape(polygon);

        const [area, perimeter] = summarizePolygon(polygon.geometry, "METRIC")!;
        expect(screen.getByText(area)).toBeInTheDocument();
        expect(screen.getByText(perimeter)).toBeInTheDocument();
        expect(screen.queryByText("RADIUS")).not.toBeInTheDocument();
    });

    // Rectangles share PolygonGeometry with polygons — this is the case that broke
    // when dispatch was on element.type ("rectangle" !== "polygon") instead of the
    // geometry class.
    it("shows area and perimeter for a rectangle", () => {
        const rectangle = Rectangle.create({
            positions: [
                [0, 0],
                [0, 0.01],
                [0.01, 0.01],
                [0.01, 0],
            ],
        });
        renderShape(rectangle);

        expect(screen.getByText("AREA")).toBeInTheDocument();
        expect(screen.getByText("PERIMETER")).toBeInTheDocument();
    });

    it("shows length for a line", () => {
        const start: Position2D = [0, 0];
        const end = turf.destination(start, 1.5, 90, { units: "kilometers" }).geometry
            .coordinates as Position2D;
        const line = Line.create({ positions: [start, end] });
        renderShape(line);

        const [length] = summarizeLine(line.geometry, "METRIC")!;
        expect(screen.getByText(length)).toBeInTheDocument();
    });

    // Arrows are also a 2-point Polyline, so they fall into the same generic
    // "length" case as a line — geometry-based dispatch doesn't special-case them.
    it("shows length for an arrow", () => {
        const start: Position2D = [0, 0];
        const end = turf.destination(start, 800, 45, { units: "meters" }).geometry
            .coordinates as Position2D;
        const arrow = Arrow.create({ start, end });
        renderShape(arrow);

        expect(screen.getByText("LENGTH")).toBeInTheDocument();
    });

    it("shows both segment lengths and the angle for an angle element", () => {
        const vertex: Position2D = [10, 45];
        const arm = (meters: number, bearing: number) =>
            turf.destination(vertex, meters, bearing, { units: "meters" }).geometry
                .coordinates as Position2D;

        const angle = Angle.create({ first: arm(500, 0), second: vertex, third: arm(300, 90) });
        renderShape(angle, { angleUnit: "degrees", angleOrientation: "cw" });

        // Arms placed due north and due east of the vertex, cw orientation: a clean 90°.
        expect(screen.getByText(formatDistance(500, "METRIC").display)).toBeInTheDocument();
        expect(screen.getByText(formatDistance(300, "METRIC").display)).toBeInTheDocument();
        expect(screen.getByText(/90(\.0+)?\s*deg/i)).toBeInTheDocument();
    });

    it("is registered for the shape types, not a graphic, whose control points are also a Polyline", () => {
        const { details } = shapesHost().contributions;
        const summaryFor = (type: string) =>
            details.resolveInfoSections(type).some(({ id }) => id === "opencop.shapes.summary");
        expect(summaryFor("line")).toBe(true);
        expect(summaryFor("polygon")).toBe(true);
        expect(summaryFor("graphic")).toBe(false);
    });

    it("renders nothing for an angle that isn't fully placed yet", () => {
        const vertex: Position2D = [10, 45];
        const angle = Angle.create({ first: [10, 45.01], second: vertex, third: [10.01, 45] });
        angle.geometry.pop(); // simulate the third point not placed yet

        renderShape(angle);
        expect(screen.queryByRole("table")).not.toBeInTheDocument();
    });

    it("shows area and perimeter for an ellipse, matching a circle when major equals minor", () => {
        const ellipse = Ellipse.create({ origin: [0, 0], major: 500, minor: 500 });
        renderShape(ellipse);

        expect(
            screen.getByText(formatArea(Math.PI * 500 * 500, "METRIC").display),
        ).toBeInTheDocument();
        // Perimeter is a traced polygon length, not the analytic circle formula, so
        // compare with a tolerance instead of exact string equality — an exact match
        // only holds by rounding luck at the current boundary resolution and would
        // break if that shared constant were ever tuned for an unrelated reason.
        expect(readMeters("PERIMETER")).toBeCloseTo(2 * Math.PI * 500, -2);
    });

    it("renders nothing for an ellipse with a degenerate (zero) axis", () => {
        const ellipse = Ellipse.create({ origin: [0, 0], major: 500, minor: 0 });
        renderShape(ellipse);
        expect(screen.queryByRole("table")).not.toBeInTheDocument();
        expect(screen.queryByText("Shape Details")).not.toBeInTheDocument();
    });

    it("computes area and perimeter for a non-circular, rotated ellipse", () => {
        const major = 800;
        const minor = 400;
        const ellipse = Ellipse.create({ origin: [0, 0], major, minor, bearing: 35 });
        renderShape(ellipse);

        expect(
            screen.getByText(formatArea(Math.PI * major * minor, "METRIC").display),
        ).toBeInTheDocument();

        // Independent oracle (Ramanujan's 2nd approximation), so this catches real
        // arithmetic mistakes without depending on the implementation's own boundary
        // trace or its resolution constant.
        const h = ((major - minor) / (major + minor)) ** 2;
        const approxPerimeter =
            Math.PI * (major + minor) * (1 + (3 * h) / (10 + Math.sqrt(4 - 3 * h)));
        expect(readMeters("PERIMETER")).toBeCloseTo(approxPerimeter, -2);
    });
});

/** Reads a DetailTable row's value by its label and parses it back to meters. */
function readMeters(label: string): number {
    const row = screen.getByText(label).closest("tr");
    if (!row) throw new Error(`no row found for label "${label}"`);
    const text = within(row).getByText(/^[\d.]+(km|m)$/).textContent ?? "";
    return text.endsWith("km") ? Number(text.slice(0, -2)) * 1000 : Number(text.slice(0, -1));
}

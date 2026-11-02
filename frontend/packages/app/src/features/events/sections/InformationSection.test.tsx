// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { API_BASE, HttpResponse, http, render, screen, server } from "~/test";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { noop } from "lodash";
import {
    CircleGeometry,
    Element,
    EllipseGeometry,
    PointGeometry,
    PointStyle,
    PolygonGeometry,
    PolylineGeometry,
} from "@opencop/elements";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { installNativeSubjects } from "~/features/contributions/native-install";
import { installShapes } from "~/plugins/shapes/install-shapes";
import type { EventDetails } from "@opencop/api";
import type { Geometry } from "geojson";
import { CoordFormatProvider } from "~/features/coordinates/CoordFormatContext";
import { HostDisplayPreferences } from "~/features/preferences/HostDisplayPreferences";
import { InformationSection } from "./InformationSection";

/**
 * Characterization: pins what the information tab shows. The per-type
 * coordinates moved behind the details-position surface, which the shapes
 * subject contributes, so it is installed. The labels are the rendered English
 * strings, so a regression shows up as the operator would see it.
 */

function makeDetails(type: string): EventDetails {
    return {
        displayName: "Alpha",
        id: "id-1",
        objectId: "object-12345678",
        source: "local",
        kind: "test-kind",
        version: "1",
        timestampz: { time: Date.UTC(2026, 9, 6, 12), stale: Date.UTC(2026, 9, 6, 13) },
        element: { type, speed: 0, course: 0, heading: 0 },
    } as unknown as EventDetails;
}

/** The live element matching a wire geometry. */
function elementOf(type: string, geometry: Geometry): Element {
    const flat = (coordinates: number[][]) => coordinates.flat();
    const shape =
        geometry.type === "Point"
            ? type === "circle"
                ? new CircleGeometry(geometry.coordinates as [number, number], 10)
                : type === "ellipse"
                  ? new EllipseGeometry(geometry.coordinates as [number, number], 10, 5)
                  : new PointGeometry(geometry.coordinates as [number, number])
            : geometry.type === "LineString"
              ? new PolylineGeometry(flat(geometry.coordinates))
              : new PolygonGeometry(
                    flat((geometry as { coordinates: number[][][] }).coordinates[0]),
                );
    return Element.create({
        type,
        geometry: shape,
        style: new PointStyle({ color: "#ffffffff", radius: 3 }),
    });
}

function renderSection(type: string, geometry: Geometry) {
    return render(
        <CoordFormatProvider>
            <HostDisplayPreferences>
                <InformationSection
                    details={makeDetails(type)}
                    geometry={geometry}
                    element={elementOf(type, geometry)}
                />
            </HostDisplayPreferences>
        </CoordFormatProvider>,
    );
}

let disposeShapes = noop;
beforeAll(() => {
    disposeShapes = installNativeSubjects(
        [{ id: "opencop.shapes", install: installShapes }],
        contributionContextFor,
    );
});
afterAll(() => disposeShapes());

const POINT: Geometry = { type: "Point", coordinates: [10, 20] };
const LINE: Geometry = {
    type: "LineString",
    coordinates: [
        [10, 20],
        [12, 22],
    ],
};
const POLYGON: Geometry = {
    type: "Polygon",
    coordinates: [
        [
            [0, 0],
            [1, 0],
            [1, 1],
            [0, 0],
        ],
    ],
};

describe("InformationSection — the coordinates row today", () => {
    // The identity section resolves the event's layer and source names.
    beforeEach(() => {
        server.use(
            http.get(`${API_BASE}/me/layers`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([])),
        );
    });

    it("labels an arrow's row 'Central location'", () => {
        renderSection("arrow", LINE);
        expect(screen.getByText(/central location/i)).toBeInTheDocument();
    });

    it.each(["circle", "ellipse"])("labels a %s's row 'Center point'", (type) => {
        renderSection(type, POINT);
        expect(screen.getByText(/center point/i)).toBeInTheDocument();
    });

    it.each(["point", "symbol"])("labels a %s's row 'Position'", (type) => {
        renderSection(type, POINT);
        expect(screen.getByText(/^position$/i)).toBeInTheDocument();
        expect(screen.queryByText(/center point/i)).not.toBeInTheDocument();
    });

    it("shows no coordinates row for a line that is not an arrow", () => {
        renderSection("line", LINE);
        expect(screen.queryByText(/^coordinates$/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/central location/i)).not.toBeInTheDocument();
    });

    it("shows no coordinates row for a polygon", () => {
        renderSection("polygon", POLYGON);
        expect(screen.queryByText(/^coordinates$/i)).not.toBeInTheDocument();
    });

    it("places a contributed section by its order: the shape summary after Coordinates", () => {
        const { container } = renderSection("circle", POINT);
        const text = container.textContent?.toLowerCase() ?? "";
        const order = ["identity", "coordinates", "shape details", "timestamps", "motion"].map(
            (heading) => text.indexOf(heading),
        );
        expect(order.every((index) => index >= 0)).toBe(true);
        expect([...order].sort((a, b) => a - b)).toEqual(order);
    });

    it("orders the sections Identity, Coordinates, Timestamps, Motion", () => {
        const { container } = renderSection("point", POINT);
        const text = container.textContent?.toLowerCase() ?? "";
        const order = ["identity", "coordinates", "timestamps", "motion"].map((heading) =>
            text.indexOf(heading),
        );
        expect(order.every((index) => index >= 0)).toBe(true);
        expect([...order].sort((a, b) => a - b)).toEqual(order);
    });
});

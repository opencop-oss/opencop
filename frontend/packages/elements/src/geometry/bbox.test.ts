// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import * as turf from "@turf/turf";
import { Circle } from "./Circle";
import { Ellipse } from "./Ellipse";
import { Point } from "./Point";
import { Polygon } from "./Polygon";
import { Polyline } from "./Polyline";

/** Extent as a consumer measures it — the path every camera-framing caller takes. */
function measuredExtent(geometry: {
    toGeoJSONGeometry: () => GeoJSON.Geometry;
}): [number, number, number, number] {
    const [minLng, minLat, maxLng, maxLat] = turf.bbox(geometry.toGeoJSONGeometry());
    return [minLng, minLat, maxLng, maxLat];
}

function spans([minLng, minLat, maxLng, maxLat]: [number, number, number, number]) {
    return {
        width: turf.distance([minLng, 0], [maxLng, 0], { units: "meters" }),
        height: turf.distance([0, minLat], [0, maxLat], { units: "meters" }),
    };
}

describe("geometries whose coordinates are their extent", () => {
    it("emit no bbox, leaving turf to measure the coordinates", () => {
        expect(new Polygon([0, 0, 0, 10, 10, 10, 10, 0]).toGeoJSONGeometry().bbox).toBeUndefined();
        expect(new Polyline([1, 2, 3, 4]).toGeoJSONGeometry().bbox).toBeUndefined();
        expect(new Point([5, 6]).toGeoJSONGeometry().bbox).toBeUndefined();
    });

    it("measure to their coordinate extent", () => {
        expect(measuredExtent(new Polygon([0, 0, 0, 10, 10, 10, 10, 0]))).toEqual([0, 0, 10, 10]);
        expect(measuredExtent(new Polyline([1, 2, 3, 4]))).toEqual([1, 2, 3, 4]);
    });

    it("leave a point zero-area so callers can still detect one", () => {
        expect(measuredExtent(new Point([5, 6]))).toEqual([5, 6, 5, 6]);
    });
});

describe("Circle bbox", () => {
    it("carries the radius so the point does not measure as zero-area", () => {
        const circle = new Circle([0, 0], 1000);
        const geometry = circle.toGeoJSONGeometry();

        expect(geometry.type).toBe("Point");
        expect(geometry.bbox).toHaveLength(4);
        expect(measuredExtent(circle)).not.toEqual([0, 0, 0, 0]);
    });

    it("is what turf.bbox returns, in preference to the coordinates", () => {
        const circle = new Circle([0, 0], 1000);

        // The contract the framing callers depend on: no recompute, no special case.
        expect(turf.bbox(circle.toGeoJSONGeometry())).toEqual(circle.toGeoJSONGeometry().bbox);
    });

    it("spans the diameter within the drawn polygon's chord error", () => {
        const radius = 1000;
        const { width, height } = spans(measuredExtent(new Circle([0, 0], radius)));

        // Exactly 2r, not merely close: turf.circle starts at bearing 0 and steps
        // by 360/steps, so while BBOX_STEPS stays a multiple of 4 the N/E/S/W
        // extremes land on real vertices and no chord error shows up here. That
        // makes this a diameter check only — the rotated case below is what
        // constrains the ring's resolution.
        expect(width).toBeGreaterThan(radius * 2 * 0.999);
        expect(width).toBeLessThanOrEqual(radius * 2);
        expect(height).toBeGreaterThan(radius * 2 * 0.999);
        expect(height).toBeLessThanOrEqual(radius * 2);
    });

    it("centers on the origin", () => {
        const [minLng, minLat, maxLng, maxLat] = measuredExtent(new Circle([-77.037, 38.897], 500));

        expect((minLng + maxLng) / 2).toBeCloseTo(-77.037, 5);
        expect((minLat + maxLat) / 2).toBeCloseTo(38.897, 5);
    });

    it("degrades to a zero-area box at radius 0 rather than throwing", () => {
        expect(measuredExtent(new Circle([3, 4], 0))).toEqual([3, 4, 3, 4]);
    });

    it("stays continuous across the antimeridian instead of spanning the globe", () => {
        const [minLng, minLat, maxLng, maxLat] = measuredExtent(new Circle([179.9, 10], 50_000));

        // turf emits longitudes past +180 rather than wrapping, so the box stays
        // as narrow as the same circle drawn anywhere else.
        expect(maxLng - minLng).toBeLessThan(1);
        expect(maxLng).toBeGreaterThan(180);
        expect(minLat).toBeLessThan(10);
        expect(maxLat).toBeGreaterThan(10);
    });

    it("widens to the north pole for a ring that encloses it", () => {
        const [minLng, minLat, maxLng, maxLat] = measuredExtent(new Circle([0, 89.8], 50_000));

        // Per-vertex extremes would report maxLat below the pole the ring covers
        // and a ~350 degree longitude band; the cap is the honest extent.
        expect([minLng, maxLng]).toEqual([-180, 180]);
        expect(maxLat).toBe(90);
        expect(minLat).toBeLessThan(89.8);
    });

    it("widens to the south pole for a ring that encloses it", () => {
        const [minLng, minLat, maxLng, maxLat] = measuredExtent(new Circle([0, -89.8], 50_000));

        expect([minLng, maxLng]).toEqual([-180, 180]);
        expect(minLat).toBe(-90);
        expect(maxLat).toBeGreaterThan(-89.8);
    });

    it("falls back to the whole envelope for a circle covering both poles", () => {
        // Not an operational shape — this pins the both-poles branch, which a
        // radius past a quarter of the circumference (~10,002km) reaches from
        // the equator in either direction.
        expect(measuredExtent(new Circle([0, 0], 15_000_000))).toEqual([-180, -90, 180, 90]);
    });

    it("keeps the far edge of a circle that covers one pole and crosses the equator", () => {
        const [minLng, minLat, maxLng, maxLat] = measuredExtent(new Circle([0, 45], 6_000_000));

        // 6,000km from 45°N covers the north pole (5,004km away) and reaches
        // ~9°S on the far side. Only the covered pole gets widened — flattening
        // minLat to -90 as well would frame ~81 degrees of empty latitude.
        expect([minLng, maxLng]).toEqual([-180, 180]);
        expect(maxLat).toBe(90);
        expect(minLat).toBeCloseTo(-8.96, 1);
    });

    it("leaves a large pole-clear circle unwidened", () => {
        const [minLng, minLat, maxLng, maxLat] = measuredExtent(new Circle([0, 0], 5_000_000));

        // 5,000km spans ~90 degrees — wide, but covers no pole, so it must keep
        // its measured extent rather than widening.
        expect(minLng).toBeGreaterThan(-90);
        expect(maxLng).toBeLessThan(90);
        expect(minLat).toBeGreaterThan(-90);
        expect(maxLat).toBeLessThan(90);
    });

    it("survives a round trip through the wire format", () => {
        const circle = new Circle([12, 34], 750);
        // The cast isn't bbox-specific: Geometry.toProperties() returns `object`,
        // which isn't assignable to fromProperties's Record<string, unknown>.
        const restored = Circle.fromProperties(
            [12, 34],
            circle.toProperties() as { radius: number },
        );

        expect(measuredExtent(restored)).toEqual(measuredExtent(circle));
    });
});

describe("Ellipse bbox", () => {
    it("carries the axes so the point does not measure as zero-area", () => {
        const ellipse = new Ellipse([0, 0], 2000, 500);

        expect(ellipse.toGeoJSONGeometry().type).toBe("Point");
        expect(measuredExtent(ellipse)).not.toEqual([0, 0, 0, 0]);
    });

    it("runs the major axis east-west at bearing 90", () => {
        const { width, height } = spans(measuredExtent(new Ellipse([0, 0], 2000, 500, 90)));

        expect(width).toBeGreaterThan(height);
        expect(width).toBeGreaterThan(2000 * 2 * 0.999);
        expect(height).toBeGreaterThan(500 * 2 * 0.999);
        expect(height).toBeLessThanOrEqual(500 * 2);
    });

    it("runs the major axis north-south at bearing 0", () => {
        const { width, height } = spans(measuredExtent(new Ellipse([0, 0], 2000, 500, 0)));

        expect(height).toBeGreaterThan(width);
        expect(height).toBeGreaterThan(2000 * 2 * 0.999);
        expect(width).toBeGreaterThan(500 * 2 * 0.999);
        expect(width).toBeLessThanOrEqual(500 * 2);
    });

    it("tilts the box between the two at an intermediate bearing", () => {
        const { width, height } = spans(measuredExtent(new Ellipse([0, 0], 2000, 500, 20)));

        // Bearing 45 is the one angle a wrong sign or offset would still pass,
        // so pin an asymmetric one: mostly north-south, but wider than at 0.
        expect(height).toBeGreaterThan(width);
        expect(width).toBeGreaterThan(500 * 2);
        expect(height).toBeLessThan(2000 * 2);
    });

    it("keeps a circular ellipse's box square under rotation", () => {
        const unrotated = spans(measuredExtent(new Ellipse([0, 0], 1000, 1000, 0)));
        const rotated = spans(measuredExtent(new Ellipse([0, 0], 1000, 1000, 37)));

        // Rotation pulls the extremes off the vertices, so the rotated span falls
        // short by the chord error — and that shortfall is the only assertion in
        // this file sensitive to BBOX_STEPS. The bound is deliberately tight to
        // 64: measured shortfall is 0.086% at 64 steps but 0.16% at 32, so a
        // coarser ring fails here rather than passing silently.
        const relative = (a: number, b: number) => Math.abs(a - b) / b;
        expect(relative(rotated.width, unrotated.width)).toBeLessThan(0.0012);
        expect(relative(rotated.height, unrotated.height)).toBeLessThan(0.0012);
        expect(relative(rotated.width, rotated.height)).toBeLessThan(0.0012);
    });

    it("leaves an eccentric east-west ellipse unwidened despite spanning over half the globe", () => {
        // The major axis runs east-west (bearing 90) and is long enough that the
        // ring spans ~198 degrees of longitude, while the minor axis keeps it
        // within half a degree of the equator. Longitude span alone would read
        // as a covered pole here; what decides it is the 50km the ellipse
        // actually reaches toward one.
        const [minLng, minLat, maxLng, maxLat] = measuredExtent(
            new Ellipse([0, 0], 11_000_000, 50_000, 90),
        );

        expect(maxLng - minLng).toBeGreaterThan(180);
        expect(minLng).toBeGreaterThan(-180);
        expect(maxLng).toBeLessThan(180);
        expect(minLat).toBeCloseTo(-0.45, 1);
        expect(maxLat).toBeCloseTo(0.45, 1);
    });

    it("widens for an ellipse whose major axis reaches over a pole", () => {
        // Same axes as above turned onto the meridian (bearing 0), so the major
        // axis now runs the 10,002km to each pole and past it.
        expect(measuredExtent(new Ellipse([0, 0], 11_000_000, 50_000, 0))).toEqual([
            -180, -90, 180, 90,
        ]);

        // One pole only: 6,000km north from 60°N clears the pole (3,338km away),
        // while the south pole stays 16,669km off.
        const [minLng, minLat, maxLng, maxLat] = measuredExtent(
            new Ellipse([0, 60], 6_000_000, 50_000, 0),
        );
        expect([minLng, maxLng]).toEqual([-180, 180]);
        expect(maxLat).toBe(90);
        expect(minLat).toBeGreaterThan(-90);
        expect(minLat).toBeLessThan(60);
    });

    it("degrades to a zero-area box when an axis is missing rather than throwing", () => {
        // fromProperties defaults absent axes to 0, so wire data that omits them
        // is constructible — and turf.ellipse rejects a zero axis outright.
        // Serialization runs on the render path via Element.toFeature(), so a
        // throw here surfaces as a crashed panel.
        expect(measuredExtent(new Ellipse([3, 4], 0, 500, 20))).toEqual([3, 4, 3, 4]);
        expect(measuredExtent(new Ellipse([3, 4], 2000, 0, 20))).toEqual([3, 4, 3, 4]);
        expect(measuredExtent(new Ellipse([3, 4], 0, 0, 0))).toEqual([3, 4, 3, 4]);
    });

    it("survives a round trip through the wire format", () => {
        const ellipse = new Ellipse([12, 34], 2000, 500, 20);
        const restored = Ellipse.fromProperties(
            [12, 34],
            ellipse.toProperties() as Record<string, unknown>,
        );

        expect(measuredExtent(restored)).toEqual(measuredExtent(ellipse));
    });
});

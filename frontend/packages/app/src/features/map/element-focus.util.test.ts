// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import {
    CircleGeometry,
    EllipseGeometry,
    PointGeometry,
    PolygonGeometry,
    type Element,
} from "@opencop/elements";
import { focusOnElement } from "./element-focus.util";

function makeElementWithGeometry(
    geometry: CircleGeometry | EllipseGeometry | PointGeometry | PolygonGeometry,
): Element {
    return { geometry } as unknown as Element;
}

describe("focusOnElement", () => {
    it("fits the camera to the bounding box of an extended geometry", () => {
        const element = makeElementWithGeometry(new PolygonGeometry([0, 0, 0, 10, 10, 10, 10, 0]));
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        focusOnElement(element, { flyTo, fitBounds });

        expect(fitBounds).toHaveBeenCalledWith(
            [
                [0, 0],
                [10, 10],
            ],
            expect.objectContaining({
                padding: 80,
                duration: 1200,
                maxZoom: 18,
            }),
        );
        expect(flyTo).not.toHaveBeenCalled();
    });

    it("includes a circle's radius when fitting its bounds", () => {
        const element = makeElementWithGeometry(new CircleGeometry([0, 0], 100_000));
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        focusOnElement(element, { flyTo, fitBounds });

        expect(fitBounds).toHaveBeenCalledOnce();
        const [bounds, options] = fitBounds.mock.calls[0];
        expect(bounds[0][0]).toBeCloseTo(-0.8993, 3);
        expect(bounds[0][1]).toBeCloseTo(-0.8993, 3);
        expect(bounds[1][0]).toBeCloseTo(0.8993, 3);
        expect(bounds[1][1]).toBeCloseTo(0.8993, 3);
        expect(options).toEqual(
            expect.objectContaining({ padding: 80, duration: 1200, maxZoom: 18 }),
        );
        expect(flyTo).not.toHaveBeenCalled();
    });

    it("includes an ellipse's axes when fitting its bounds", () => {
        const element = makeElementWithGeometry(new EllipseGeometry([0, 0], 100_000, 50_000, 90));
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        focusOnElement(element, { flyTo, fitBounds });

        expect(fitBounds).toHaveBeenCalledOnce();
        const [bounds, options] = fitBounds.mock.calls[0];
        expect(bounds[0][0]).toBeCloseTo(-0.8993, 3);
        expect(bounds[0][1]).toBeCloseTo(-0.4497, 3);
        expect(bounds[1][0]).toBeCloseTo(0.8993, 3);
        expect(bounds[1][1]).toBeCloseTo(0.4497, 3);
        expect(options).toEqual(
            expect.objectContaining({ padding: 80, duration: 1200, maxZoom: 18 }),
        );
        expect(flyTo).not.toHaveBeenCalled();
    });

    it("flies to a fixed zoom for a point geometry", () => {
        const element = makeElementWithGeometry(new PointGeometry([5, 6]));
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        focusOnElement(element, { flyTo, fitBounds });

        expect(flyTo).toHaveBeenCalledWith(
            expect.objectContaining({ center: [5, 6], zoom: 15, duration: 1200 }),
        );
        expect(fitBounds).not.toHaveBeenCalled();
    });

    it("propagates geometry serialization failures", () => {
        const throwingGeometry = {
            toGeoJSONGeometry: () => {
                throw new Error("degenerate geometry");
            },
            getAllPos: () => [5, 6],
        } as unknown as PointGeometry;
        const element = makeElementWithGeometry(throwingGeometry);
        const flyTo = vi.fn();
        const fitBounds = vi.fn();

        expect(() => focusOnElement(element, { flyTo, fitBounds })).toThrow("degenerate geometry");

        expect(flyTo).not.toHaveBeenCalled();
        expect(fitBounds).not.toHaveBeenCalled();
    });

    it("propagates fitBounds failures without falling back to flyTo", () => {
        const element = makeElementWithGeometry(new PolygonGeometry([0, 0, 0, 10, 10, 10, 10, 0]));
        const flyTo = vi.fn();
        const fitBounds = vi.fn(() => {
            throw new Error("fitBounds failed");
        });

        expect(() => focusOnElement(element, { flyTo, fitBounds })).toThrow("fitBounds failed");

        expect(fitBounds).toHaveBeenCalledOnce();
        expect(flyTo).not.toHaveBeenCalled();
    });
});

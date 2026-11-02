// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { Map, NavigationControl, type RequestTransformFunction } from "maplibre-gl-real";
import { defaultStyle } from "./map.default";

describe("production no-imagery fallback", () => {
    let map: Map | undefined;
    let container: HTMLDivElement | undefined;

    afterEach(() => {
        map?.remove();
        container?.remove();
        map = undefined;
        container = undefined;
    });

    it("mounts a network-free map with controls and a visible GeoJSON overlay", async () => {
        const externalRequests: string[] = [];
        const rejectExternalRequest: RequestTransformFunction = (requestUrl) => {
            const url = new URL(requestUrl, window.location.href);
            if (/^https?:$/.test(url.protocol) && url.origin !== window.location.origin) {
                externalRequests.push(url.href);
                throw new Error(`Blocked non-local map request to ${url.origin}`);
            }
            return { url: requestUrl };
        };

        container = document.createElement("div");
        container.style.width = "512px";
        container.style.height = "512px";
        document.body.append(container);

        map = new Map({
            container,
            style: defaultStyle,
            center: [0, 0],
            zoom: 3,
            attributionControl: false,
            transformRequest: rejectExternalRequest,
        });
        map.addControl(new NavigationControl());
        await map.once("load");

        const canvas = container.querySelector<HTMLCanvasElement>("canvas.maplibregl-canvas");
        expect(canvas).not.toBeNull();
        expect(canvas!.width).toBeGreaterThan(0);
        expect(canvas!.height).toBeGreaterThan(0);
        expect(map.getPaintProperty("opencop-background", "background-color")).toBe("#1f2530");
        expect(container.querySelectorAll(".maplibregl-ctrl button")).toHaveLength(3);

        map.addSource("fallback-contract-overlay", {
            type: "geojson",
            data: {
                type: "FeatureCollection",
                features: [
                    {
                        type: "Feature",
                        properties: {},
                        geometry: {
                            type: "Polygon",
                            coordinates: [
                                [
                                    [-10, -10],
                                    [10, -10],
                                    [10, 10],
                                    [-10, 10],
                                    [-10, -10],
                                ],
                            ],
                        },
                    },
                ],
            },
        });
        map.addLayer({
            id: "fallback-contract-overlay",
            type: "fill",
            source: "fallback-contract-overlay",
            paint: { "fill-color": "#ff00ff", "fill-opacity": 1 },
        });
        await map.once("idle");

        const renderedOverlay = map.queryRenderedFeatures(map.project([0, 0]), {
            layers: ["fallback-contract-overlay"],
        });
        expect(renderedOverlay).toHaveLength(1);
        expect(externalRequests).toEqual([]);
    }, 15_000);
});

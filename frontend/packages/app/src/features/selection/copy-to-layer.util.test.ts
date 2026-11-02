// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Element, IconStyle, neverStale, PointGeometry } from "@opencop/elements";
import type { MyLayerInfo } from "@opencop/api";
import {
    buildCopyFeature,
    getCopyTargetLayers,
    isOnLayer,
    isStaleForCopy,
} from "./copy-to-layer.util";

const NOW = 1_800_000_000_000;

function element(props: { source?: string; start?: number; stale?: number } = {}): Element {
    return new Element({
        type: "symbol",
        id: "version-1",
        objectId: "object-1",
        source: props.source ?? "layer-origin",
        displayName: "Alpha",
        start: props.start,
        stale: props.stale,
        geometry: new PointGeometry([10, 20]),
        style: new IconStyle({ size: 1, angle: 0 }),
        attributes: { callsign: "A1" },
    });
}

describe("isStaleForCopy", () => {
    it("is false for an element whose stale time is still in the future", () => {
        expect(isStaleForCopy(element({ stale: NOW + 1 }), NOW)).toBe(false);
    });

    it("is true once the stale time has passed or been reached", () => {
        expect(isStaleForCopy(element({ stale: NOW - 1 }), NOW)).toBe(true);
        expect(isStaleForCopy(element({ stale: NOW }), NOW)).toBe(true);
    });

    it("is false for an element that never goes stale", () => {
        expect(isStaleForCopy(element({ stale: neverStale }), NOW)).toBe(false);
        expect(isStaleForCopy(element(), NOW)).toBe(false);
    });
});

describe("isOnLayer", () => {
    it("is true only when the element's source is the layer", () => {
        expect(isOnLayer(element({ source: "layer-a" }), "layer-a")).toBe(true);
        expect(isOnLayer(element({ source: "layer-a" }), "layer-b")).toBe(false);
    });
});

describe("buildCopyFeature", () => {
    it("retargets the source to the destination layer", () => {
        const feature = buildCopyFeature(element({ source: "layer-origin" }), "layer-target", NOW);

        expect(feature.properties.opencop.source).toBe("layer-target");
    });

    it("keeps the geometry, display name and attributes of the original", () => {
        const feature = buildCopyFeature(element(), "layer-target", NOW);

        expect(feature.geometry).toEqual({ type: "Point", coordinates: [10, 20] });
        expect(feature.properties.opencop.displayName).toBe("Alpha");
        expect(feature.properties.opencop.attributes).toEqual({ callsign: "A1" });
    });

    it("resets the event time to now and keeps the original stale time", () => {
        const feature = buildCopyFeature(
            element({ start: NOW - 5000, stale: NOW + 9000 }),
            "t",
            NOW,
        );

        expect(feature.properties.opencop.timestampz).toEqual({
            start: NOW - 5000,
            time: NOW,
            stale: NOW + 9000,
        });
    });

    it("falls back to now for a missing start time", () => {
        const feature = buildCopyFeature(element({ stale: NOW + 9000 }), "t", NOW);

        expect(feature.properties.opencop.timestampz.start).toBe(NOW);
    });

    it("does not mutate the source element", () => {
        const original = element({ source: "layer-origin" });

        buildCopyFeature(original, "layer-target", NOW);

        expect(original.source).toBe("layer-origin");
    });
});

describe("getCopyTargetLayers", () => {
    const layer = (layerId: string, role: MyLayerInfo["role"]): MyLayerInfo => ({
        layerId,
        layerName: layerId,
        role,
        accessScope: "PRIVATE",
        defaultVisible: true,
    });

    it("keeps layers the user is editor or higher on, and drops the rest", () => {
        const layers = [
            layer("owner", "OWNER"),
            layer("admin", "ADMIN"),
            layer("editor", "EDITOR"),
            layer("member", "MEMBER"),
        ];

        expect(getCopyTargetLayers(layers).map((l) => l.layerId)).toEqual([
            "owner",
            "admin",
            "editor",
        ]);
    });
});

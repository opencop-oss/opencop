// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PolygonGeometry, PolylineGeometry } from "@opencop/elements";
import {
    formatArea,
    formatDistance,
    summarizeCircle,
    summarizeLine,
    summarizePolygon,
    type MeasurementSystem,
} from "@opencop/utils";
import { chunk } from "lodash";
import * as turf from "@turf/turf";
import {
    isCircleMeasurement,
    isLineMeasurement,
    isPolygonMeasurement,
    type Measurement,
} from "./measurement.model";

/**
 * Anything with a geometry of the given kind — a measurement or an element.
 * These functions read the geometry only.
 */
type WithGeometry<G> = { readonly geometry: G };

function toCoordPairs(el: WithGeometry<PolylineGeometry | PolygonGeometry>): [number, number][] {
    return chunk(el.geometry.getAllPos() as number[], 2) as [number, number][];
}

export type SegmentLabel = { position: [number, number]; text: string; angle: number };

export function buildSegmentLabels(
    el: WithGeometry<PolylineGeometry>,
    unit: MeasurementSystem,
): SegmentLabel[] {
    const pairs = toCoordPairs(el);
    const labels: SegmentLabel[] = [];
    for (let i = 0; i < pairs.length - 1; i++) {
        const start = pairs[i];
        const end = pairs[i + 1];
        const meters = turf.length(turf.lineString([start, end]), { units: "meters" });
        const mid = turf.midpoint(turf.point(start), turf.point(end)).geometry.coordinates as [
            number,
            number,
        ];
        let angle = -turf.rhumbBearing(start, end) + 90;
        if (Math.abs(angle) > 90) angle += 180;
        labels.push({ position: mid, text: formatDistance(meters, unit).display, angle });
    }
    return labels;
}

export type TotalLabel = { position: [number, number]; text: string };

export function buildTotalLabel(
    el: WithGeometry<PolylineGeometry>,
    unit: MeasurementSystem,
): TotalLabel[] {
    const pairs = toCoordPairs(el);
    if (pairs.length < 2) return [];
    const lngs = pairs.map(([lng]) => lng);
    const lats = pairs.map(([, lat]) => lat);
    const totalMeters = turf.length(turf.lineString(pairs), { units: "meters" });
    const centerLng = (Math.min(...lngs) + Math.max(...lngs)) / 2;
    return [
        {
            position: [centerLng, Math.min(...lats)],
            text: formatDistance(totalMeters, unit).display,
        },
    ];
}

export function polygonLabel(d: WithGeometry<PolygonGeometry>, unit: MeasurementSystem): string {
    return summarizePolygon(d.geometry, unit)?.join("\n") ?? "";
}

export function circleLabel(radius: number, unit: MeasurementSystem): string {
    return formatArea(Math.PI * radius * radius, unit).display;
}

export function summarizeMeasurement(el: Measurement, unit: MeasurementSystem): string[] | null {
    if (isCircleMeasurement(el)) return summarizeCircle(el.geometry, unit);
    if (isLineMeasurement(el)) return summarizeLine(el.geometry, unit);
    if (isPolygonMeasurement(el)) return summarizePolygon(el.geometry, unit);
    return null;
}

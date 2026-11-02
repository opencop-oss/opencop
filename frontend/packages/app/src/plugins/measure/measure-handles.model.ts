// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Measure edit model: an origin + four cardinal radius handles for
 * circles, per-vertex handles for lines/polygons. Interpreted by the same
 * edit session as every other model — a finished measurement is an edit
 * session the runtime holds no custody over.
 */
import * as turf from "@turf/turf";
import {
    isCircleMeasurement,
    isLineMeasurement,
    isPolygonMeasurement,
    type Measurement,
} from "./measurement.model";
import {
    hitControlPoint,
    positionPairs,
    type ControlPoint,
    type EditIntent,
    type EditModel,
} from "@opencop/tools";

function measureControlPoints(measurement: Measurement): ControlPoint[] {
    if (isCircleMeasurement(measurement)) {
        const origin = measurement.geometry.getOrigin() as [number, number];
        const radius = measurement.geometry.getRadius();
        const handles: ControlPoint[] = [{ kind: "origin", position: origin }];
        for (const bearing of [0, 90, 180, 270] as const) {
            const pt = turf.destination(turf.point(origin), radius, bearing, { units: "meters" });
            handles.push({
                kind: "radius",
                position: pt.geometry.coordinates as [number, number],
                index: bearing,
            });
        }
        return handles;
    }
    if (isLineMeasurement(measurement) || isPolygonMeasurement(measurement)) {
        return positionPairs(measurement.geometry.getAllPos()).map((position, index) => ({
            kind: "vertex",
            position,
            index,
        }));
    }
    return [];
}

export const measureHandlesModel: EditModel<Measurement> = {
    controlPoints: measureControlPoints,

    applyHandle(measurement, controlPoint, lngLat) {
        if (isCircleMeasurement(measurement)) {
            if (controlPoint.kind === "origin") {
                measurement.geometry.setOrigin([lngLat[0], lngLat[1]]);
            } else if (controlPoint.kind === "radius") {
                const origin = measurement.geometry.getOrigin() as [number, number];
                measurement.geometry.setRadius(
                    turf.distance(turf.point(origin), turf.point([lngLat[0], lngLat[1]]), {
                        units: "meters",
                    }),
                );
            }
            return;
        }
        if (
            (isLineMeasurement(measurement) || isPolygonMeasurement(measurement)) &&
            controlPoint.kind === "vertex"
        ) {
            measurement.geometry.setPos([lngLat[0], lngLat[1]], controlPoint.index ?? 0);
        }
    },

    hitTest(measurement, screen, _lngLat, project, tolerancePx): EditIntent {
        const cp = hitControlPoint(measureControlPoints(measurement), screen, project, tolerancePx);
        return cp ? { kind: "handle", controlPoint: cp } : { kind: "none" };
    },

    // A measurement is re-taken, never moved: its body is not a drag target.
    translate: false,

    invalidation: "all",
};

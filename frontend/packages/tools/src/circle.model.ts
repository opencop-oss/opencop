// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Circle edit model — origin translate + boundary resize. The boundary hit
 * test computes the ring point at the pointer's bearing, so detection is exact
 * everywhere on the ring, not just at fixed vertices.
 */
import * as turf from "@turf/turf";
import type { CircleElement } from "@opencop/elements";
import type { EditIntent, EditModel, ScreenProjector } from "./edit-model";
import { dist } from "./screen-math";

const MIN_RADIUS_METERS = 10;

function boundaryScreenPoint(
    element: CircleElement,
    bearing: number,
    project: ScreenProjector,
): [number, number] | undefined {
    const lngLat = turf.destination(
        element.geometry.getOrigin(),
        element.geometry.getRadius(),
        bearing,
        { units: "meters" },
    ).geometry.coordinates as [number, number];
    return project(lngLat);
}

function isOnBoundary(
    element: CircleElement,
    screen: readonly [number, number],
    lngLat: readonly [number, number],
    project: ScreenProjector,
    tolerancePx: number,
): boolean {
    const bearing = turf.bearing(element.geometry.getOrigin(), [lngLat[0], lngLat[1]]);
    const boundary = boundaryScreenPoint(element, bearing, project);
    return boundary !== undefined && dist(boundary, screen) <= tolerancePx;
}

function isInside(element: CircleElement, lngLat: readonly [number, number]): boolean {
    const distance = turf.distance(element.geometry.getOrigin(), [lngLat[0], lngLat[1]], {
        units: "meters",
    });
    return distance <= element.geometry.getRadius();
}

export const circleEditModel: EditModel<CircleElement> = {
    controlPoints(element) {
        return [
            {
                kind: "origin",
                position: element.geometry.getOrigin() as [number, number],
            },
        ];
    },

    applyHandle(element, _controlPoint, lngLat) {
        element.geometry.setOrigin([lngLat[0], lngLat[1]]);
    },

    hitTest(element, screen, lngLat, project, tolerancePx): EditIntent {
        if (isOnBoundary(element, screen, lngLat, project, tolerancePx)) {
            return { kind: "boundary" };
        }
        if (isInside(element, lngLat)) return { kind: "body" };
        return { kind: "none" };
    },

    translate(element, from, to) {
        const origin = element.geometry.getOrigin() as [number, number];
        element.geometry.setOrigin([origin[0] + (to[0] - from[0]), origin[1] + (to[1] - from[1])]);
    },

    applyBoundary(element, lngLat) {
        element.geometry.setRadius(
            Math.max(
                turf.distance(element.geometry.getOrigin() as number[], [lngLat[0], lngLat[1]], {
                    units: "meters",
                }),
                MIN_RADIUS_METERS,
            ),
        );
    },

    // Radius changes rebuild the whole ring, not just a vertex.
    invalidation: "all",
};

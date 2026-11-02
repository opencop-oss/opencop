// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { PathLayer, ScatterplotLayer } from "@deck.gl/layers";
import { TripsLayer } from "@deck.gl/geo-layers";
import { DataFilterExtension } from "@deck.gl/extensions";
import type { TripWaypoint, TripSegment } from "./history.util";

// Type for DataFilterExtension props to avoid ts-expect-error
interface DataFilterExtensionProps<DataType = unknown> {
    getFilterValue: (d: DataType) => number;
    filterRange: [number, number];
}

// Visual configuration constants
const VISUAL_CONFIG = {
    pathWidth: 8,
    borderWidth: 11,
    waypointRadius: 4,
    opacity: {
        gap: 26, // 0.1 alpha
        continuous: 77, // 0.3 alpha
        border: 77, // 0.3 alpha
    },
    trailPercentage: 0.2,
    minTrailLengthMs: 5_000,
} as const;

/**
 * Create a single PathLayer for all gap segments
 */
export function createGapPathLayer(
    segments: TripSegment[],
    color: [number, number, number],
): PathLayer {
    const gapSegments = segments.filter((s) => s.type === "gap");
    const paths = gapSegments.map((segment) => segment.waypoints.map((wp) => wp.coordinates));

    return new PathLayer({
        id: "trip-path-gaps",
        data: paths,
        getPath: (d) => d,
        getColor: [...color, VISUAL_CONFIG.opacity.gap],
        widthMinPixels: VISUAL_CONFIG.pathWidth,
        capRounded: true,
        jointRounded: true,
    });
}

/**
 * Create border and main PathLayers for all continuous segments
 * Returns [borderLayer, pathLayer]
 */
export function createContinuousPathLayers(
    segments: TripSegment[],
    color: [number, number, number],
): [PathLayer, PathLayer] {
    const continuousSegments = segments.filter((s) => s.type === "continuous");
    const paths = continuousSegments.map((segment) =>
        segment.waypoints.map((wp) => wp.coordinates),
    );

    const borderLayer = new PathLayer({
        id: "trip-path-borders",
        data: paths,
        getPath: (d) => d,
        getColor: [255, 255, 255, VISUAL_CONFIG.opacity.border],
        widthMinPixels: VISUAL_CONFIG.borderWidth,
        capRounded: true,
        jointRounded: true,
    });

    const pathLayer = new PathLayer({
        id: "trip-path-continuous",
        data: paths,
        getPath: (d) => d,
        getColor: [...color, VISUAL_CONFIG.opacity.continuous],
        widthMinPixels: VISUAL_CONFIG.pathWidth,
        capRounded: true,
        jointRounded: true,
    });

    return [borderLayer, pathLayer];
}

/**
 * Create a single animated TripsLayer for all continuous segments
 * All timestamps are normalized to the global trip startTime
 */
export function createAnimatedTripLayer(
    segments: TripSegment[],
    color: [number, number, number],
    currentTime: number,
    startTime: number,
    endTime: number,
): TripsLayer {
    const tripDuration = endTime - startTime;
    const trailLength = Math.max(
        VISUAL_CONFIG.minTrailLengthMs,
        tripDuration * VISUAL_CONFIG.trailPercentage,
    );

    const continuousSegments = segments.filter((s) => s.type === "continuous");

    // Prepare data for TripsLayer - all segments use global timeline (relative to startTime)
    const trips = continuousSegments.map((segment) => ({
        waypoints: segment.waypoints,
    }));

    return new TripsLayer({
        id: "trip-animated-continuous",
        data: trips,
        getPath: (d: { waypoints: TripWaypoint[] }) => d.waypoints.map((wp) => wp.coordinates),
        // All timestamps relative to the global trip startTime
        getTimestamps: (d: { waypoints: TripWaypoint[] }) =>
            d.waypoints.map((wp) => wp.timestamp - startTime),
        getColor: color,
        fadeTrail: true,
        trailLength,
        // currentTime is also relative to global startTime
        currentTime: currentTime - startTime,
        widthMinPixels: VISUAL_CONFIG.pathWidth,
        capRounded: true,
        jointRounded: true,
    });
}

/**
 * Create a ScatterplotLayer for waypoint markers with time-based filtering
 */
export function createWaypointLayer(
    waypoints: TripWaypoint[],
    color: [number, number, number],
    currentTime: number,
    startTime: number,
): ScatterplotLayer<TripWaypoint, DataFilterExtensionProps<TripWaypoint>> {
    return new ScatterplotLayer<TripWaypoint, DataFilterExtensionProps<TripWaypoint>>({
        id: "trip-waypoints",
        data: waypoints,
        getPosition: (d) => d.coordinates,
        getFillColor: color,
        getRadius: VISUAL_CONFIG.waypointRadius,
        radiusMinPixels: VISUAL_CONFIG.waypointRadius,
        radiusMaxPixels: VISUAL_CONFIG.waypointRadius,
        extensions: [new DataFilterExtension({ filterSize: 1 })],
        getFilterValue: (d) => d.timestamp - startTime,
        filterRange: [0, currentTime - startTime],
    });
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { diff } from "deep-diff";
import { distance } from "@turf/turf";
import type { HistoryFeature, HistoryFeatureCollection } from "@opencop/api";

export interface HistoryEntry {
    id: string;
    action: string;
    description: string;
    author: string;
    timestamp: string;
    changes?: Record<string, { old: unknown; new: unknown }>;
}

/**
 * Returns a record of changed properties with old/new values
 *
 * To track additional properties:
 * 1. For new nested objects: Add a new diff() call for that object path
 * 2. For top-level feature properties: Add explicit checks like displayName BELOW
 *
 */
export function computeChanges(
    prev: HistoryFeature | null,
    curr: HistoryFeature,
): Record<string, { old: unknown; new: unknown }> {
    const changes: Record<string, { old: unknown; new: unknown }> = {};

    if (!prev) return changes;

    // Diff all element properties automatically (fillColor, strokeColor, etc.)
    const elementDiffs = diff(prev.properties.opencop.element, curr.properties.opencop.element);

    elementDiffs?.forEach((change) => {
        // For nested property changes, increase path length check
        if (change.kind === "E" && change.path && change.path.length === 1) {
            const key = change.path[0] as string;
            changes[key] = { old: change.lhs, new: change.rhs };
        }
    });

    // Check geometry changes
    const geomDiffs = diff(prev.geometry.coordinates, curr.geometry.coordinates);

    if (geomDiffs && geomDiffs.length > 0) {
        changes["coordinates"] = {
            old: prev.geometry.coordinates,
            new: curr.geometry.coordinates,
        };
    }

    // Check displayName change
    if (prev.properties.opencop.displayName !== curr.properties.opencop.displayName) {
        changes["displayName"] = {
            old: prev.properties.opencop.displayName,
            new: curr.properties.opencop.displayName,
        };
    }

    return changes;
}

/**
 * Determines the primary action type based on what changed
 */
export function determineAction(
    prev: HistoryFeature | null,
    changes: Record<string, { old: unknown; new: unknown }>,
): string {
    if (!prev) return "created";

    const changeKeys = Object.keys(changes);

    // Movement changes (coordinates or center point)
    if (changeKeys.includes("coordinates") || changeKeys.includes("center")) {
        return "moved";
    }

    // Style changes (colors, strokes, etc.)
    if (
        changeKeys.some((k) =>
            ["fillColor", "strokeColor", "strokeStyle", "strokeWeight"].includes(k),
        )
    ) {
        return "styled";
    }

    return "edited";
}

/**
 * Displays in UI
 *
 */
export function generateDescription(
    action: string,
    displayName: string,
    changes: Record<string, { old: unknown; new: unknown }>,
): string {
    switch (action) {
        case "created":
            return `${displayName} created`;
        case "moved":
            return `${displayName} repositioned`;
        case "styled": {
            const styleChanges = Object.keys(changes).filter((k) =>
                ["fillColor", "strokeColor", "strokeStyle", "strokeWeight"].includes(k),
            );
            if (styleChanges.length === 1) {
                return `${displayName} ${formatKey(styleChanges[0]).toLowerCase()} updated`;
            }
            return `${displayName} styling updated`;
        }
        default:
            return `${displayName} modified`;
    }
}

/**
 * Transforms GeoJSON FeatureCollection into sorted history entries with computed diffs
 */
export function transformFeaturesToHistory(
    featureCollection: HistoryFeatureCollection,
): HistoryEntry[] {
    const features = [...featureCollection.features];

    // Sort features by timestamp ascending
    features.sort(
        (a, b) => a.properties.opencop.timestampz.time - b.properties.opencop.timestampz.time,
    );

    const entries: HistoryEntry[] = [];

    for (let i = 0; i < features.length; i++) {
        const curr = features[i];
        const prev = i > 0 ? features[i - 1] : null;

        const changes = computeChanges(prev, curr);
        const action = determineAction(prev, changes);
        const displayName =
            curr.properties.opencop.displayName || curr.properties.opencop.objectId.slice(0, 8);
        const contact = curr.properties.opencop.author;
        const author = contact?.username || "Unknown";

        entries.push({
            id: `${curr.properties.opencop.id}_${curr.properties.opencop.timestampz.time}`,
            action,
            description: generateDescription(action, displayName, changes),
            author,
            timestamp: new Date(curr.properties.opencop.timestampz.time).toISOString(),
            changes: Object.keys(changes).length > 0 ? changes : undefined,
        });
    }

    return entries;
}

export function formatKey(key: string): string {
    return key
        .replace(/([A-Z])/g, " $1")
        .replace(/^./, (str) => str.toUpperCase())
        .trim();
}

export function isColorValue(value: string): boolean {
    return /^#([0-9A-Fa-f]{3,8})$/.test(value) || /^rgba?\(/.test(value) || /^hsla?\(/.test(value);
}

export interface TripWaypoint {
    coordinates: [number, number]; // [lng, lat]
    timestamp: number; // ms (absolute)
    hasGapBefore?: boolean; // Indicates a data gap before this waypoint
    gapDuration?: number; // ms of gap
    gapDistance?: number; // meters of gap
}

export interface GapThresholds {
    timeMs: number; // Time threshold to consider a gap
    distanceMeters: number; // Distance threshold to consider a gap
}

export const DEFAULT_GAP_THRESHOLDS: GapThresholds = {
    timeMs: 5 * 60 * 1000, // 5 minutes
    distanceMeters: 1000, // 1 km
};

/**
 * Calculate adaptive gap thresholds based on time range duration
 * - Short ranges (< 1 hour): 30 seconds, 100m
 * - Medium ranges (1-6 hours): 2 minutes, 500m
 * - Long ranges (6-24 hours): 5 minutes, 1km
 * - Very long ranges (> 24 hours): 15 minutes, 2km
 */
export function calculateAdaptiveGapThresholds(fromDate: string, toDate: string): GapThresholds {
    const durationMs = new Date(toDate).getTime() - new Date(fromDate).getTime();
    const durationHours = durationMs / (1000 * 60 * 60);

    if (durationHours < 1) {
        // Less than 1 hour: 30 seconds, 100m
        return {
            timeMs: 30 * 1000,
            distanceMeters: 100,
        };
    }

    if (durationHours < 6) {
        // 1-6 hours: 2 minutes, 500m
        return {
            timeMs: 2 * 60 * 1000,
            distanceMeters: 500,
        };
    }

    if (durationHours < 24) {
        // 6-24 hours: 5 minutes, 1km
        return {
            timeMs: 5 * 60 * 1000,
            distanceMeters: 1000,
        };
    }

    // More than 24 hours: 15 minutes, 2km
    return {
        timeMs: 15 * 60 * 1000,
        distanceMeters: 2000,
    };
}

export interface TripSegment {
    type: "continuous" | "gap";
    waypoints: TripWaypoint[];
    startIndex: number;
    endIndex: number;
}

/**
 * Split trip waypoints into continuous and gap segments
 */
export function segmentTrip(waypoints: TripWaypoint[]): TripSegment[] {
    if (waypoints.length === 0) return [];

    const segments: TripSegment[] = [];
    let currentSegment: TripWaypoint[] = [waypoints[0]];
    let currentType: "continuous" | "gap" = "continuous";
    let startIndex = 0;

    for (let i = 1; i < waypoints.length; i++) {
        const wp = waypoints[i];

        if (wp.hasGapBefore) {
            // End current continuous segment
            if (currentSegment.length > 0) {
                segments.push({
                    type: currentType,
                    waypoints: currentSegment,
                    startIndex,
                    endIndex: i - 1,
                });
            }

            // Start new gap segment
            currentSegment = [waypoints[i - 1], wp]; // Include prev point for line continuity
            currentType = "gap";
            startIndex = i - 1;
        } else if (currentType === "gap") {
            // End gap segment, start continuous segment
            segments.push({
                type: currentType,
                waypoints: currentSegment,
                startIndex,
                endIndex: i - 1,
            });

            currentSegment = [waypoints[i - 1], wp]; // Include prev point for line continuity
            currentType = "continuous";
            startIndex = i - 1;
        } else {
            // Continue current segment
            currentSegment.push(wp);
        }
    }

    // Add final segment
    if (currentSegment.length > 0) {
        segments.push({
            type: currentType,
            waypoints: currentSegment,
            startIndex,
            endIndex: waypoints.length - 1,
        });
    }

    return segments;
}

/**
 * Detect gaps in waypoint data based on time and distance thresholds
 * Marks waypoints with hasGapBefore flag and calculates gap metadata
 */
function detectGaps(waypoints: TripWaypoint[], thresholds: GapThresholds): TripWaypoint[] {
    if (waypoints.length < 2) return waypoints;

    const result: TripWaypoint[] = [waypoints[0]]; // First waypoint has no gap

    for (let i = 1; i < waypoints.length; i++) {
        const prev = waypoints[i - 1];
        const curr = waypoints[i];

        const timeDelta = curr.timestamp - prev.timestamp;
        const distanceKm = distance(prev.coordinates, curr.coordinates, { units: "kilometers" });
        const distanceMeters = distanceKm * 1000;

        // Check if this exceeds gap thresholds
        const isGap = timeDelta > thresholds.timeMs || distanceMeters > thresholds.distanceMeters;

        result.push({
            ...curr,
            hasGapBefore: isGap,
            gapDuration: isGap ? timeDelta : undefined,
            gapDistance: isGap ? distanceMeters : undefined,
        });
    }

    return result;
}

/**
 * Extracts trip waypoints from history features for deck.gl TripsLayer visualization.
 * Only includes Point geometries with changed coordinates (filters out style-only changes).
 * Detects and marks data gaps based on time/distance thresholds.
 * Returns empty array if fewer than 2 unique positions exist.
 */
export function extractTripWaypoints(
    featureCollection: HistoryFeatureCollection,
    thresholds: GapThresholds = DEFAULT_GAP_THRESHOLDS,
): TripWaypoint[] {
    const features = [...featureCollection.features];

    // Sort by timestamp ascending
    features.sort(
        (a, b) => a.properties.opencop.timestampz.time - b.properties.opencop.timestampz.time,
    );

    const waypoints: TripWaypoint[] = [];
    let lastCoords: [number, number] | null = null;

    for (const feature of features) {
        // Only process Point geometries (v1 scope)
        if (feature.geometry.type !== "Point") continue;

        const coords = feature.geometry.coordinates as [number, number];
        const timestamp = feature.properties.opencop.timestampz.time;

        // Skip if coordinates haven't changed (style-only update)
        if (lastCoords && coords[0] === lastCoords[0] && coords[1] === lastCoords[1]) {
            continue;
        }

        waypoints.push({ coordinates: coords, timestamp });
        lastCoords = coords;
    }

    // Return empty if insufficient data for a trip
    if (waypoints.length < 2) return [];

    // Detect gaps in the data
    return detectGaps(waypoints, thresholds);
}

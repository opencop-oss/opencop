// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { useEffect, useMemo, useRef } from "react";
import { useMap } from "react-map-gl/maplibre";
import { useToken } from "@chakra-ui/react";
import { useTrip, type SavedViewState } from "./TripContext";
import type { TripWaypoint } from "./history.util";
import { segmentTrip } from "./history.util";
import {
    createGapPathLayer,
    createContinuousPathLayers,
    createAnimatedTripLayer,
    createWaypointLayer,
} from "./trip-layers.util";
import type { Layer } from "@deck.gl/core";
import { useRegisterLayers } from "~/features/map/DeckLayerContext";
import { DECK_PRIORITY_TRIP } from "~/features/map/deck-layer-priorities";
import { hexToRgba } from "@opencop/utils";

/** A theme hex colour as the `[r, g, b]` the trip layers append their own alpha to. */
function rgbOf(hex: string): [number, number, number] {
    const [r, g, b] = hexToRgba(hex);
    return [r, g, b];
}

/**
 * Compute bounding box from waypoints [minLng, minLat, maxLng, maxLat]
 * only needed for fitting the map to the trip when the simulator activates, not for rendering the layers
 */
function computeBounds(waypoints: TripWaypoint[]): [number, number, number, number] {
    let minLng = Infinity;
    let minLat = Infinity;
    let maxLng = -Infinity;
    let maxLat = -Infinity;

    for (const wp of waypoints) {
        const [lng, lat] = wp.coordinates;
        if (lng < minLng) minLng = lng;
        if (lat < minLat) minLat = lat;
        if (lng > maxLng) maxLng = lng;
        if (lat > maxLat) maxLat = lat;
    }

    return [minLng, minLat, maxLng, maxLat];
}

/**
 * Renders trip path and animation layers on the map.
 * Registers layers via `useRegisterLayers()` through the shared `DeckLayerProvider`.
 * Must be rendered inside `<DeckLayerProvider>`.
 */
export function TripOverlay(): ReactNode {
    const trip = useTrip();
    const { current: mapInstance } = useMap();
    const hasFitBoundsRef = useRef(false);
    const previousViewRef = useRef<SavedViewState | null>(null);

    // Get theme colors - cyan.400 for path, orange.400 for waypoint markers
    const [cyan400, orange400] = useToken("colors", ["cyan.400", "orange.400"]);
    const tripColor = rgbOf(cyan400 || "#67e8f9");
    const waypointColor = rgbOf(orange400 || "#fb923c");

    // Memoize segments — only recomputes when waypoints change
    const segments = useMemo(() => segmentTrip(trip.waypoints), [trip.waypoints]);
    const visibleSegments = useMemo(
        () => (trip.hideGaps ? segments.filter((s) => s.type === "continuous") : segments),
        [segments, trip.hideGaps],
    );

    // Zoom to trip bounds when requested
    useEffect(() => {
        const map = mapInstance?.getMap();
        if (
            trip.active &&
            trip.hasZoomed &&
            trip.waypoints.length >= 2 &&
            map &&
            !hasFitBoundsRef.current
        ) {
            // Save current view state before zooming to trip
            const savedView: SavedViewState = {
                center: map.getCenter(),
                zoom: map.getZoom(),
                bearing: map.getBearing(),
                pitch: map.getPitch(),
            };
            previousViewRef.current = savedView;
            trip.markAsZoomed(savedView);

            const bounds = computeBounds(trip.waypoints);

            // Account for InfoPanel (w="lg" = 512px) on the right side
            const PANEL_WIDTH = 512;
            const BASE_PADDING = 80;

            map.fitBounds(bounds, {
                padding: {
                    top: BASE_PADDING,
                    bottom: BASE_PADDING,
                    left: BASE_PADDING,
                    right: PANEL_WIDTH + BASE_PADDING, // 592px total
                },
                duration: 800,
            });
            hasFitBoundsRef.current = true;
        }
    }, [trip.active, trip.hasZoomed, trip.waypoints, trip, mapInstance]);

    // Restore view when requested
    useEffect(() => {
        const map = mapInstance?.getMap();
        if (
            trip.active &&
            !trip.hasZoomed &&
            hasFitBoundsRef.current &&
            map &&
            previousViewRef.current
        ) {
            map.easeTo({
                center: previousViewRef.current.center,
                zoom: previousViewRef.current.zoom,
                bearing: previousViewRef.current.bearing,
                pitch: previousViewRef.current.pitch,
                duration: 800,
            });
            hasFitBoundsRef.current = false;
        }
    }, [trip.active, trip.hasZoomed, mapInstance]);

    // Clean up when trip deactivates
    useEffect(() => {
        if (!trip.active) {
            hasFitBoundsRef.current = false;
            previousViewRef.current = null;
        }
    }, [trip.active]);

    // Build all trip layers reactively
    const layers = useMemo(() => {
        if (!trip.active || trip.waypoints.length < 2) return [];

        const result: Layer[] = [];

        // Static path layers
        const [borderLayer, pathLayer] = createContinuousPathLayers(visibleSegments, tripColor);
        result.push(borderLayer, pathLayer);
        if (!trip.hideGaps) {
            result.push(createGapPathLayer(visibleSegments, tripColor));
        }

        // Animated layers (when animation enabled)
        if (trip.showAnimation) {
            result.push(
                createAnimatedTripLayer(
                    visibleSegments,
                    tripColor,
                    trip.currentTime,
                    trip.startTime,
                    trip.endTime,
                ),
                createWaypointLayer(
                    trip.waypoints,
                    waypointColor,
                    trip.currentTime,
                    trip.startTime,
                ),
            );
        }

        return result;
    }, [
        trip.active,
        trip.waypoints,
        visibleSegments,
        tripColor,
        trip.hideGaps,
        trip.showAnimation,
        trip.currentTime,
        trip.startTime,
        trip.endTime,
        waypointColor,
    ]);

    useRegisterLayers("trip", layers, DECK_PRIORITY_TRIP);

    return null;
}

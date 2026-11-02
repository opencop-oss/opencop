// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as turf from "@turf/turf";
import type { Element } from "@opencop/elements";
import type { ViewActions } from "./core/slices/view/view.slice";

const FOCUS_POINT_ZOOM = 15;
const FOCUS_DURATION_MS = 1200;
const FOCUS_BASE_PADDING_PX = 80;
const FOCUS_BOUNDS_MAX_ZOOM = 18;

export type FocusCameraActions = Pick<ViewActions, "flyTo" | "fitBounds">;

/**
 * Move the camera to frame an element's geometry. Geometries with ground
 * extent use their bbox with `fitBounds`; zero-area geometries such as bare
 * points use `flyTo` at a fixed zoom. Parametric geometries include their
 * actual extent in their serialized GeoJSON bbox.
 *
 * Extracted from MapContextMenu's former inline `handleZoomToFeature` body —
 * MapContextMenu's "Zoom to Feature" and the events-list focus button share
 * this implementation. (A similar inline block still exists in
 * SourceElementList.tsx's handleEventClick; consolidating it is a follow-up.)
 *
 * Bounds use equal padding on every side so the element is centered in the
 * full map viewport, regardless of whether an information panel is open.
 */
export function focusOnElement(element: Element, { flyTo, fitBounds }: FocusCameraActions): void {
    const geojson = element.geometry.toGeoJSONGeometry();
    const [minLng, minLat, maxLng, maxLat] = turf.bbox(geojson);

    // A geometry with no ground extent can't be framed: fitBounds would resolve
    // its zero-area bbox to maxZoom instead of the intended point zoom.
    if (minLng === maxLng && minLat === maxLat) {
        flyTo({
            center: [minLng, minLat],
            zoom: FOCUS_POINT_ZOOM,
            duration: FOCUS_DURATION_MS,
        });
        return;
    }

    fitBounds(
        [
            [minLng, minLat],
            [maxLng, maxLat],
        ],
        {
            padding: FOCUS_BASE_PADDING_PX,
            duration: FOCUS_DURATION_MS,
            maxZoom: FOCUS_BOUNDS_MAX_ZOOM,
        },
    );
}

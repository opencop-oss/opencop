// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useEffect, useMemo, useRef } from "react";
import type { EventDetails } from "@opencop/api";
import { localEventSource, type Element, type PartialOCFeature } from "@opencop/elements";
import { usePickedElementRef, usePickedElement } from "~/features/map/use-picked-element.hook";
import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";
import { useQuery } from "@tanstack/react-query";
import { useThrottledValue } from "@tanstack/react-pacer";
import { eventQueries } from "./events.queries";

const DISPLAY_THROTTLE_MS = 1000;

export interface EventDetailsResult {
    details: EventDetails | undefined;
    geometry: PartialOCFeature["geometry"] | undefined;
    loading: boolean;
    error: Error | null;
    /** Stable getter — returns latest unthrottled geojson for mutations. */
    getLatestGeojson: () => PartialOCFeature | undefined;
    /** True when the selected element is present in the active store (live or historical). */
    hasElement: boolean;
    /** The live, mutable Element backing this selection. */
    element: Element | undefined;
}

export function useEventDetails(): EventDetailsResult {
    const selectedRef = usePickedElementRef();
    const isHistorical = useHistoricalMode() === "historical";
    // Resolve the selected element from the shared store (it holds live or
    // historical elements depending on mode) through the same hook the map
    // overlays use. `isHistorical` (mode) is still read to
    // gate the live REST detail fetch below: that endpoint returns the *latest*
    // version (and the selected key is the per-version id, not the stable
    // objectId), which would contradict the historical version shown.
    const element = usePickedElement();

    // --- Data sources ---
    // Event details come from two sources that update at different rates:
    //
    //   1. WebSocket (via the active element) — real-time geometry, colors,
    //      displayName, timestamps, and element-level properties. Updates can
    //      arrive multiple times per second. WS is the source of truth for
    //      everything it carries.
    //
    //   2. REST fetch (via remoteData) — metadata the WebSocket doesn't carry:
    //      version, contacts, kind, attributes, extensions, remarks.
    //      Fetched once on pick, then refetched every 10s.
    //
    // The merge in `geojson` below starts from the WS element and layers in
    // only the REST-exclusive metadata fields, so real-time WS updates are
    // never overwritten by stale REST data.
    //
    // Throttling: the active element fires on every WS batch (sub-second for
    // fast-moving events). The raw `geojson` memo updates immediately so
    // save/del always see the latest data (via getLatestGeojson), but
    // useThrottledValue (1s, leading+trailing) feeds the return value so the
    // detail panel updates at a human-readable pace.

    const objectId = selectedRef?.objectId ?? "";
    const sourceId = selectedRef?.sourceId ?? "";
    const hasLiveRemoteElement = !isHistorical && !!element && sourceId !== localEventSource;
    const result = useQuery({
        ...eventQueries.detail(sourceId, objectId),
        enabled: !!sourceId && !!objectId && hasLiveRemoteElement,
    });
    const { isLoading: loading, error, data } = result;

    // Only use fetched data for the currently selected remote event. For local
    // (drawn) events the query is disabled, but `keepPreviousData` can leak
    // stale data from a previously-viewed remote event, overwriting the local
    // element's properties. Same-event placeholder data is still useful during
    // background refetches, so key off the objectId instead of the placeholder
    // flag alone.
    const remoteData =
        hasLiveRemoteElement && data?.objectId === objectId && data?.source === sourceId
            ? data
            : undefined;

    // Merge the live element with REST metadata. The live element remains the
    // source of truth for geometry, display fields, and timestamps so live WS
    // updates keep flowing through the panel without being overwritten by REST
    // detail responses.
    const geojson = useMemo<PartialOCFeature | undefined>(() => {
        if (!element) return undefined;
        const processed = element.toFeature();
        if (!processed) return undefined;
        return {
            ...processed,
            properties: {
                opencop: {
                    ...processed.properties.opencop,
                    ...(remoteData && {
                        version: remoteData.version,
                        contacts: remoteData.contacts,
                        kind: remoteData.kind,
                        attributes: remoteData.attributes,
                        extensions: remoteData.extensions,
                        remarks: remoteData.remarks,
                    }),
                },
            },
        };
    }, [element, remoteData]);

    // --- Throttled display via @tanstack/react-pacer ---
    //
    // useThrottledValue rate-limits the merged geojson and element together so
    // the whole detail panel (including contributed sections, which read live off the
    // element) updates at one human-readable pace (1s) instead of each field
    // drifting at its own rate. Leading edge ensures immediate display on
    // first value; trailing edge ensures the final value is never lost.

    const snapshot = useMemo(() => ({ geojson, element }), [geojson, element]);
    const [display, throttler] = useThrottledValue(snapshot, {
        wait: DISPLAY_THROTTLE_MS,
        leading: true,
        trailing: true,
    });

    // When the selected event changes, flush any pending throttled value
    // immediately. The new geojson has already been computed in this render
    // and pushed into the throttler — flushing ensures it displays without
    // waiting for the trailing edge (which cancel() would kill entirely,
    // preventing the new event from ever displaying).
    useEffect(() => {
        throttler.flush();
    }, [selectedRef?.sourceId, selectedRef?.objectId, throttler]);

    // Ref-backed getter for the unthrottled geojson — stable reference that
    // always returns the latest value when called (used by save/del).
    //
    // The element ref is needed because geometry edits mutate the Element
    // in-place (e.g. setPos) without changing the object reference, so the
    // useMemo above won't re-run. Reading directly from the element at call
    // time ensures save/del always serialise the current geometry.
    const elementRef = useRef(element);
    const remoteDataRef = useRef(remoteData);
    useEffect(() => {
        elementRef.current = element;
    }, [element]);
    useEffect(() => {
        remoteDataRef.current = remoteData;
    }, [remoteData]);

    const getLatestGeojson = useCallback((): PartialOCFeature | undefined => {
        const el = elementRef.current;
        if (!el) return undefined;
        const processed = el.toFeature();
        if (!processed) return undefined;
        const rd = remoteDataRef.current;
        return {
            ...processed,
            properties: {
                opencop: {
                    ...processed.properties.opencop,
                    ...(rd && {
                        version: rd.version,
                        contacts: rd.contacts,
                        kind: rd.kind,
                        attributes: rd.attributes,
                        extensions: rd.extensions,
                        remarks: rd.remarks,
                    }),
                },
            },
        };
    }, []);

    const details = display.geojson?.properties.opencop;
    const geometry = display.geojson?.geometry;

    return {
        details,
        geometry,
        loading,
        error: error as Error | null,
        getLatestGeojson,
        hasElement: !!element,
        element: display.element,
    };
}

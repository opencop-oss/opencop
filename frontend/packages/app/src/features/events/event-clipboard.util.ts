// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Element, localEventSource, type Position2D } from "@opencop/elements";
import { MercatorCoordinate } from "maplibre-gl";
import type { EventClipboardPayload } from "./event-clipboard.store";
import { useMapStore } from "../map/core/map.store";

export function buildEventClipboardPayload(element: Element): EventClipboardPayload {
    return {
        type: element.type,
        displayName: element.getDisplayName(),
        start: element.start,
        stale: element.stale,
        speed: element.speed,
        course: element.course,
        heading: element.heading,
        hae: element.hae,
        code: element.getCode(),
        modifiers: { ...element.getModifiers() },
        geometry: element.geometry.clone(),
        style: element.getStyle().clone(),
    };
}

export function createPastedEvent(
    payload: EventClipboardPayload,
    targetAnchor?: Position2D,
): Element | null {
    const { longitude, latitude } = useMapStore.getState();

    const element = new Element({
        type: payload.type,
        source: localEventSource,
        displayName: payload.displayName,
        start: payload.start,
        time: Date.now(),
        stale: payload.stale,
        speed: payload.speed,
        course: payload.course,
        heading: payload.heading,
        hae: payload.hae,
        code: payload.code,
        modifiers: { ...payload.modifiers },
        geometry: payload.geometry.clone(),
        style: payload.style.clone(),
    });

    const sourceAnchor = getAnchorPosition(element);
    const resolvedTargetAnchor: Position2D = targetAnchor ?? [longitude, latitude];
    const sourceMerc = MercatorCoordinate.fromLngLat(sourceAnchor);
    const targetMerc = MercatorCoordinate.fromLngLat(resolvedTargetAnchor);

    element.geometry.translate(targetMerc.x - sourceMerc.x, targetMerc.y - sourceMerc.y);

    return element;
}

function getAnchorPosition(element: Element): Position2D {
    const pos = element.geometry.getPos(0);
    return [pos[0], pos[1]];
}

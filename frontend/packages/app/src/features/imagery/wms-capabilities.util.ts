// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";
import { wmsBaseUrl } from "@opencop/layers";

export interface WmsTimeDimension {
    timeMin: DateTime;
    timeMax: DateTime;
    timeDefault: DateTime | null;
    /** ISO 8601 duration from a range segment (e.g. "P1D"), null when only explicit dates were present. */
    step: string | null;
}

export function parseWmsTimeContent(
    content: string,
    defaultVal: string | null,
): WmsTimeDimension | null {
    const allDates: DateTime[] = [];
    let step: string | null = null;
    for (const segment of content.split(",")) {
        const parts = segment.trim().split("/");
        if (parts.length >= 2) {
            const start = DateTime.fromISO(parts[0].trim(), { zone: "utc" });
            const end = DateTime.fromISO(parts[1].trim(), { zone: "utc" });
            if (start.isValid) allDates.push(start);
            if (end.isValid) allDates.push(end);
            if (step === null && parts.length >= 3 && parts[2].startsWith("P")) {
                step = parts[2];
            }
        } else if (parts[0]) {
            const dt = DateTime.fromISO(parts[0].trim(), { zone: "utc" });
            if (dt.isValid) allDates.push(dt);
        }
    }
    if (!allDates.length) return null;

    const times = allDates.map((d) => d.toMillis());
    const timeDefault = defaultVal ? DateTime.fromISO(defaultVal, { zone: "utc" }) : null;
    return {
        timeMin: DateTime.fromMillis(Math.min(...times), { zone: "utc" }),
        timeMax: DateTime.fromMillis(Math.max(...times), { zone: "utc" }),
        timeDefault: timeDefault?.isValid ? timeDefault : null,
        step,
    };
}

function parseWmtsCapabilitiesDoc(doc: Document, layerName: string): WmsTimeDimension | null {
    let targetLayer: Element | null = null;
    for (const layer of doc.querySelectorAll("Layer")) {
        const matched = Array.from(layer.children).some(
            (el) => el.localName === "Identifier" && el.textContent?.trim() === layerName,
        );
        if (matched) {
            targetLayer = layer;
            break;
        }
    }
    if (!targetLayer) return null;

    const timeDim = Array.from(targetLayer.querySelectorAll("Dimension")).find((dim) =>
        Array.from(dim.children).some(
            (el) =>
                el.localName === "Identifier" && el.textContent?.trim().toLowerCase() === "time",
        ),
    );
    if (!timeDim) return null;

    const defaultVal =
        Array.from(timeDim.children)
            .find((el) => el.localName === "Default")
            ?.textContent?.trim() ?? null;
    const content =
        Array.from(timeDim.children)
            .find((el) => el.localName === "Value")
            ?.textContent?.trim() ?? "";

    return parseWmsTimeContent(content, defaultVal);
}

export async function fetchWmsTimeDimension(
    wmsUrl: string,
    layerName: string,
    signal?: AbortSignal,
): Promise<WmsTimeDimension | null> {
    const capU = new URL(wmsBaseUrl(wmsUrl));
    capU.searchParams.set("SERVICE", "WMS");
    capU.searchParams.set("REQUEST", "GetCapabilities");
    const capUrl = capU.toString();
    const response = await fetch(capUrl, { signal });
    if (!response.ok) return null;

    const xml = await response.text();
    const doc = new DOMParser().parseFromString(xml, "text/xml");

    let targetLayer: Element | null = null;
    for (const layer of doc.querySelectorAll("Layer")) {
        const name = layer.querySelector(":scope > Name");
        if (name?.textContent?.trim() === layerName) {
            targetLayer = layer;
            break;
        }
    }
    if (!targetLayer) return null;

    const timeDim =
        Array.from(targetLayer.querySelectorAll("Dimension")).find(
            (d) => d.getAttribute("name")?.toLowerCase() === "time",
        ) ??
        Array.from(targetLayer.querySelectorAll("Extent")).find(
            (d) => d.getAttribute("name")?.toLowerCase() === "time",
        );
    if (!timeDim) return null;

    const defaultVal = timeDim.getAttribute("default") ?? null;
    const content = timeDim.textContent?.trim() ?? "";
    return parseWmsTimeContent(content, defaultVal);
}

export async function fetchKvpWmtsTimeDimension(
    tileUrl: string,
    layerName: string,
    signal?: AbortSignal,
): Promise<WmsTimeDimension | null> {
    const capU = new URL(wmsBaseUrl(tileUrl));
    capU.searchParams.set("SERVICE", "WMTS");
    capU.searchParams.set("REQUEST", "GetCapabilities");
    capU.searchParams.set("VERSION", "1.0.0");
    const capUrl = capU.toString();

    const response = await fetch(capUrl, { signal });
    if (!response.ok) return null;

    const xml = await response.text();
    const doc = new DOMParser().parseFromString(xml, "text/xml");
    return parseWmtsCapabilitiesDoc(doc, layerName);
}

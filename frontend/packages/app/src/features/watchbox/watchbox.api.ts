// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ApiError, apiFetch, buildPageParams, type FetchOptions, type Page } from "@opencop/api";
import type { Filter, PredicateOperator } from "@opencop/ui";
import {
    WATCHBOX_ERROR_CODES,
    type Watchbox,
    type WatchboxDirection,
    type WatchboxError,
    type WatchboxPolygon,
} from "./watchbox.model";
import type { OCMPClient } from "@opencop/websocket";
import Logger from "js-logger";
import { isString } from "lodash";

const logger = Logger.get("watchbox");
const WATCHBOX = "/watchboxes";
const watchboxDestination = "/topic/watchbox";

export type WatchboxErrorResponse = {
    code: WatchboxError;
    message: string;
    /** Request fields the error applies to, e.g. `"polygon"` or `"filter.predicates[0].path"`. */
    fields: string[];
    /** Machine-readable causes, e.g. tracking-reset reasons on a patch conflict. */
    reasons: string[];
    /** Watchbox-local named string lists; unknown keys are retained defensively. */
    context: Partial<Record<string, string[]>>;
};

const WATCHBOX_ERROR_CODE_SET: ReadonlySet<string> = new Set(WATCHBOX_ERROR_CODES);

function isWatchboxErrorCode(value: unknown): value is WatchboxError {
    return typeof value === "string" && WATCHBOX_ERROR_CODE_SET.has(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === "object" && !Array.isArray(value);
}

function parseWatchboxContext(value: unknown): Partial<Record<string, string[]>> {
    if (!isRecord(value)) return {};

    const context: Partial<Record<string, string[]>> = {};
    for (const [key, members] of Object.entries(value)) {
        if (!Array.isArray(members)) continue;
        context[key] = members.filter(isString);
    }
    return context;
}

export function parseWatchboxError(error: unknown): WatchboxErrorResponse | undefined {
    if (!(error instanceof ApiError)) return undefined;
    const body = error.rawBody;
    if (!isRecord(body)) return undefined;
    const { code, message, fields, reasons, context } = body;
    if (!isWatchboxErrorCode(code)) return undefined;
    return {
        code,
        message: typeof message === "string" ? message : "",
        fields: Array.isArray(fields) ? fields.filter((f) => typeof f === "string") : [],
        reasons: Array.isArray(reasons) ? reasons.filter((r) => typeof r === "string") : [],
        context: parseWatchboxContext(context),
    };
}

export type WatchboxSourceStatusResponse = Watchbox["sourceStatuses"][number];

export type WatchboxResponse = Watchbox;

export function getWatchboxes(options?: FetchOptions): Promise<Watchbox[]> {
    return apiFetch(WATCHBOX, options);
}

export function getWatchbox(id: string, options?: FetchOptions): Promise<Watchbox> {
    return apiFetch<WatchboxResponse>(`${WATCHBOX}/${id}`, options);
}

export type CreateWatchboxRequest = {
    name: string;
    description?: string | null;
    polygon: WatchboxPolygon;
    direction: WatchboxDirection;
    layerIds: string[];
    filter?: Filter | null;
    /** Watchboxes are created enabled; only ever sent to create one disabled. */
    enabled?: false;
};

export async function createWatchbox(request: CreateWatchboxRequest): Promise<Watchbox> {
    return apiFetch<WatchboxResponse>(WATCHBOX, {
        method: "POST",
        body: JSON.stringify(request),
    });
}

/** Absent fields are left unchanged; the backend treats null the same as absent. */
export type PatchWatchboxRequest = {
    id: string;
    name?: string;
    description?: string | null;
    polygon?: WatchboxPolygon;
    direction?: WatchboxDirection;
    layerIds?: string[];
    filter?: Filter | null;
    enabled?: boolean;
    /**
     * Required (as true) when the patch shrinks coverage or re-enables the
     * watchbox; without it such patches fail with
     * WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED listing the offending
     * fields and reset reasons.
     */
    confirmTrackingReset?: boolean;
};

export function patchWatchbox(request: PatchWatchboxRequest): Promise<Watchbox> {
    const { id, ...body } = request;
    return apiFetch<WatchboxResponse>(`${WATCHBOX}/${id}`, {
        method: "PATCH",
        body: JSON.stringify(body),
    });
}

export function deleteWatchbox(id: string): Promise<void> {
    return apiFetch(`${WATCHBOX}/${id}`, {
        method: "DELETE",
    });
}

export type WatchboxFilterTestRequest = {
    filter: Filter | null;
    feature: GeoJSON.Feature;
};

export type WatchboxFilterPredicateResult = {
    index: number;
    path: string;
    operation: PredicateOperator;
    negate: boolean;
    matched: boolean;
};

export type WatchboxFilterMatch = {
    matches: boolean;
    predicateResults: WatchboxFilterPredicateResult[];
};

export function testWatchboxFilter(
    request: WatchboxFilterTestRequest,
): Promise<WatchboxFilterMatch> {
    return apiFetch(`${WATCHBOX}/filter/test`, {
        method: "POST",
        body: JSON.stringify(request),
    });
}

export type WatchboxAlertEventKind =
    | "ENTER"
    | "LEAVE"
    | "FILTER_MATCH_GAINED"
    | "FILTER_MATCH_LOST";

export type WatchboxAlertEvent = {
    watchboxId: string;
    configEpoch: number;
    sourceId: string;
    objectId: string;
    eventId: string;
    effectiveEventTime: string;
    kind: WatchboxAlertEventKind;
    /** The id served by `GET /watchbox-detections/{id}`. Null for rows recorded before this field existed. */
    detectionId: string | null;
    /** At most 255 characters, capped at capture. Null for rows recorded before this field existed. */
    objectDisplayName: string | null;
};

/** One durable, retained row behind a live `WatchboxAlertEvent` — same identity fields, plus the
 *  server-assigned `id` and receipt time. Retained for the backend's retention window (72 hours by
 *  default, measured on `effectiveEventTime`), unlike the live WS stream which retains nothing. */
export type WatchboxDetection = {
    id: string;
    watchboxId: string;
    configEpoch: number;
    sourceId: string;
    objectId: string;
    eventId: string;
    effectiveEventTime: string;
    kind: WatchboxAlertEventKind;
    objectDisplayName: string | null;
    detectedAt: string;
};

/** Also the cap `recordDetection` (./watchbox-detections.cache.ts) enforces on a
 *  cache-pushed page, so a client-built row never grows the list past what a
 *  fetch at this same page size would return. */
export const DETECTIONS_PAGE_SIZE = 50;

/** Newest-first; the backend rejects a `sort` param, so callers must not re-sort. */
export function getWatchboxDetections(
    watchboxId: string,
    { size = DETECTIONS_PAGE_SIZE, ...options }: FetchOptions & { size?: number } = {},
): Promise<Page<WatchboxDetection>> {
    const params = buildPageParams({ watchboxId, size });
    return apiFetch<Page<WatchboxDetection>>(`/watchbox-detections?${params}`, options);
}

/**
 * Remembers which alert deliveries have already been handled.
 *
 * Live alert delivery is at-least-once by design.
 */
export interface SeenAlerts {
    /** True the first time a delivery is seen; false for a republication of one already seen. */
    admit(alert: WatchboxAlertEvent): boolean;
}

/**
 * Past the server's 10-minute default delivery window, since a republished row whose event time
 * has aged past that window is dropped rather than published — so two copies of one alert cannot
 * arrive more than a window apart.
 */
const SEEN_ALERT_TTL_MS = 15 * 60_000;
/** Memory guard only; correctness is the TTL's job. Bounds an alert flood inside one TTL. */
const SEEN_ALERT_MAX_ENTRIES = 5_000;

export function createSeenAlerts({
    ttlMs = SEEN_ALERT_TTL_MS,
    maxEntries = SEEN_ALERT_MAX_ENTRIES,
} = {}): SeenAlerts {
    const seenAt = new Map<string, number>();

    return {
        admit(alert) {
            const now = Date.now();

            // A repeat never re-inserts, so insertion order is age order and this can stop at the
            // first live entry rather than walking the whole map on every alert.
            for (const [expiredKey, at] of seenAt) {
                if (now - at < ttlMs) break;
                seenAt.delete(expiredKey);
            }

            const key = `${alert.watchboxId}:${alert.eventId}:${alert.kind}`;
            if (seenAt.has(key)) return false;

            seenAt.set(key, now);
            while (seenAt.size > maxEntries) {
                const oldest = seenAt.keys().next();
                if (oldest.done) break;
                seenAt.delete(oldest.value);
            }
            return true;
        },
    };
}

const defaultSeenAlerts = createSeenAlerts();

export type WatchForAlertsDeps = {
    client: OCMPClient;
    notify?: (alert: WatchboxAlertEvent) => void;
    /** Defaults to the module-wide store; see {@link defaultSeenAlerts}. */
    seen?: SeenAlerts;
};
export function watchForAlerts(
    watchboxId: string,
    { client, notify, seen = defaultSeenAlerts }: WatchForAlertsDeps,
) {
    const destination = `${watchboxDestination}/${watchboxId}`;
    const handleAlert = (payload: Readonly<Uint8Array<ArrayBufferLike>>): void => {
        try {
            const alert: WatchboxAlertEvent = JSON.parse(new TextDecoder().decode(payload));
            // Ahead of `notify` so every consumer inherits the policy, including the kinds the
            // toast currently discards.
            if (!seen.admit(alert)) return;
            notify?.(alert);
        } catch {
            logger.warn(`Failed to decode alert from watchbox ${watchboxId}.`);
        }
    };

    client.subscribe(destination, handleAlert);
    return () => client.unsubscribe(destination, handleAlert);
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as z from "zod";
import { predicateDraftSchema, type Filter } from "@opencop/ui";
import { parsePolygon } from "./polygon.model";

export const WATCHBOX_DIRECTIONS = ["ENTER", "LEAVE", "BOTH"] as const;
export type WatchboxDirection = (typeof WATCHBOX_DIRECTIONS)[number];
export type WatchboxAlertingStatus = "ACTIVE" | "DEGRADED" | "FAILED" | "DISABLED";
export type WatchboxStatusReason =
    | "WATCHBOX_DISABLED"
    | "WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED"
    | "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN"
    | "WATCHBOX_OBJECT_ID_CHURN_DETECTED"
    | "WATCHBOX_DETECTION_NOT_RUNNING";
/** Mirrors the backend's WatchboxResetReason; the wire value is the enum name. */
export const WATCHBOX_RESET_REASONS = ["POLYGON_CHANGED", "LAYER_REMOVED", "REENABLED"] as const;
export type WatchboxResetReason = (typeof WATCHBOX_RESET_REASONS)[number];

/** The most layers one watchbox may reference; the server rejects more. */
export const WATCHBOX_MAX_LAYERS = 100;

export const WATCHBOX_ERROR_CODES = [
    "WATCHBOX_INVALID_REQUEST",
    "WATCHBOX_INVALID_POLYGON",
    "WATCHBOX_INVALID_FILTER",
    "WATCHBOX_INVALID_NAME",
    "WATCHBOX_LAYER_REQUIRED",
    "WATCHBOX_LAYER_LIMIT_EXCEEDED",
    "WATCHBOX_FILTER_LIMIT_EXCEEDED",
    "WATCHBOX_MANAGEMENT_ROLE_REQUIRED",
    "WATCHBOX_NO_ELIGIBLE_SOURCES",
    "WATCHBOX_SOURCE_ELIGIBILITY_FAILED",
    "WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED",
    "WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR",
    "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN",
    "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED",
    "WATCHBOX_FEATURE_DISABLED",
    "WATCHBOX_NOT_FOUND",
] as const;
export type WatchboxError = (typeof WATCHBOX_ERROR_CODES)[number];

export type WatchboxSourceStatus = {
    sourceId: string;
    sourceName: string;
    status: WatchboxAlertingStatus;
    reason: WatchboxStatusReason;
    sinceAt: string;
    observedCount: number | null;
    cap: number | null;
    updatedAt: string;
};

export type WatchboxPolygon = GeoJSON.Polygon;

export type WatchboxCapabilities = {
    canDelete: boolean;
    canPatch: boolean;
};

export type Watchbox = {
    id: string;
    name: string;
    description: string | null;
    enabled: boolean;
    direction: WatchboxDirection;
    polygon: WatchboxPolygon;
    /** Never null: the server normalises an absent filter to an empty one. */
    filter: Filter;
    layerIds: string[];
    configEpoch: number;
    alertingStatus: WatchboxAlertingStatus;
    alertingReasons: WatchboxStatusReason[];
    sourceStatuses: WatchboxSourceStatus[];
    /**
     * Defensive: `WatchboxResponse.capabilities` is a non-nullable record component
     * and every current assembler populates it. Optional only so a rolling deploy
     * serving from an older core replica degrades to no delete action.
     */
    capabilities?: WatchboxCapabilities;
    createdAt: string;
    updatedAt: string;
};

export const watchboxSchema = z.object({
    name: z.string().trim().min(1).max(64),
    description: z.string().max(256),
    direction: z.enum(WATCHBOX_DIRECTIONS),
    /** Editable only when editing; a watchbox is always created enabled. */
    enabled: z.boolean(),
    layerIds: z.array(z.string()).min(1).max(WATCHBOX_MAX_LAYERS),
    polygon: z
        .custom<WatchboxPolygon>((polygon) => polygon !== undefined)
        .superRefine((polygon, ctx) => {
            const res = parsePolygon(polygon);
            if (!res.ok) {
                res.errors.forEach((error) =>
                    ctx.addIssue({
                        code: "custom",
                        message: error,
                    }),
                );
            }
        }),
    filter: z.object({
        combinator: z.enum(["ANY", "ALL"]),
        predicates: z.array(predicateDraftSchema),
    }),
});

/** The edit-mode form: the area can't change there, so it isn't re-validated. */
export const watchboxEditSchema = watchboxSchema.extend({
    polygon: z.custom<WatchboxPolygon>(() => true),
});

export type WatchboxFormValues = z.infer<typeof watchboxSchema>;

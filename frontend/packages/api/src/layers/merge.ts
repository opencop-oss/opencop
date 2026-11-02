// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import { ApiError } from "../error";

export type LayerLifecycleState =
    | "ACTIVE"
    | "PENDING_MERGE"
    | "MERGE_SOURCE_LOCKED"
    | "MERGE_FAILED"
    | "ARCHIVED";

export interface MergeValidateRequest {
    layerIds: string[];
}

export interface MergeValidatePairwiseCount {
    firstLayerId: string;
    secondLayerId: string;
    count: number;
}

export interface MergeValidateResponse {
    layerIds: string[];
    activeFeatureCounts: Record<string, number>;
    collisionSummary: {
        totalCollisions: number;
        pairwiseCounts: MergeValidatePairwiseCount[];
    };
}

export interface MergeRequest {
    name: string;
    layerIds: string[];
}

export interface MergeTargetLayer {
    id: string;
    name: string;
    lifecycleState: LayerLifecycleState;
    accessScope: string;
    createdAt: string;
    archivedAt: string | null;
    defaultVisible: boolean;
}

export interface MergeResponse {
    targetLayer: MergeTargetLayer;
    sourceLayerIds: string[];
}

const EMPTY_LAYER_VALIDATE_MESSAGE = "All merge source layers must contain active features";

function getRawErrorMessage(body: unknown): string | undefined {
    if (typeof body === "string") return body;
    if (body != null && typeof body === "object") {
        const message = (body as { message?: unknown }).message;
        if (typeof message === "string") return message;
    }
    return undefined;
}

export function isEmptyLayerMergeValidationError(err: unknown): boolean {
    if (!(err instanceof ApiError) || err.status !== 409) {
        return false;
    }

    return getRawErrorMessage(err.rawBody)?.trim() === EMPTY_LAYER_VALIDATE_MESSAGE;
}

export async function validateMerge(
    request: MergeValidateRequest,
    options?: FetchOptions,
): Promise<MergeValidateResponse> {
    return apiFetch<MergeValidateResponse>("/layers/merge/validate", {
        method: "POST",
        body: JSON.stringify(request),
        signal: options?.signal,
    });
}

export async function mergeLayers(
    request: MergeRequest,
    options?: FetchOptions,
): Promise<MergeResponse> {
    return apiFetch<MergeResponse>("/layers/merge", {
        method: "POST",
        body: JSON.stringify(request),
        signal: options?.signal,
    });
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import { ApiError } from "../error";
import { buildPageParams, type Page } from "../common";
import type { RuntimeFeature, RuntimeFeatureAudience } from "../app-metadata/app-metadata";

const ADMIN_BASE = "/admin/runtime-features";

export type RuntimeFeatureHistoryOperation = "CREATE" | "PATCH";

/** Event-time actor facts. Nullable profile fields are omitted when Core did not have them. */
export interface RuntimeFeatureHistoryActor {
    userId: string;
    fullName?: string;
    email?: string;
    globalAdmin: boolean;
}

/** A winning frontend-only definition registration or accepted audience patch, including a no-op. */
export interface RuntimeFeatureHistoryItem {
    id: number;
    featureName: string;
    consumerClass: RuntimeFeature["consumerClass"];
    operation: RuntimeFeatureHistoryOperation;
    beforeAudience: RuntimeFeatureAudience | null;
    afterAudience: RuntimeFeatureAudience;
    revisionBefore: number | null;
    revisionAfter: number;
    occurredAt: string;
    actor: RuntimeFeatureHistoryActor;
}

export interface RuntimeFeatureHistoryPageParams {
    /** Exact feature-name filter. The contextual Web history reader always supplies it. */
    featureName: string;
    page?: number;
    size?: number;
}

export interface RuntimeFeatureAudiencePatch {
    audience: RuntimeFeatureAudience;
    /** Omit for last-write-wins; supply the revision you read to guard against a concurrent edit. */
    expectedRevision?: number;
}

/** A declared name the catalog would not accept, with the reason. */
export interface RuntimeFeatureRejection {
    name: string;
    /** Server error code, e.g. `RUNTIME_FEATURE_RESERVED_NAME`. */
    code: string;
    message: string;
}

export interface RuntimeFeatureReconciliation {
    created: RuntimeFeature[];
    /** Already registered as frontend-only; returned untouched. Convergence, not an error. */
    alreadyRegistered: RuntimeFeature[];
    rejected: RuntimeFeatureRejection[];
    /** True only when nothing was rejected. */
    complete: boolean;
}

/**
 * Registers every declared name the catalog is missing, in one request.
 *
 * The server attempts each name independently and reports per-name outcomes, so one bad manifest
 * entry cannot block the rest and the names that did register stay registered. That makes this safe
 * to re-run: converged names are no-ops. It never enables anything — created definitions start at
 * audience `NONE`.
 *
 * A rejected name is reported in the 200 body, not as an error status; only a malformed request
 * (absent/empty/oversized name list) throws.
 */
export async function reconcileRuntimeFeatures(
    names: readonly string[],
    options?: FetchOptions,
): Promise<RuntimeFeatureReconciliation> {
    return apiFetch<RuntimeFeatureReconciliation>(`${ADMIN_BASE}/reconcile`, {
        method: "POST",
        body: JSON.stringify({ names }),
        signal: options?.signal,
    });
}

const CONFLICT_CODES = new Set(["RUNTIME_FEATURE_REVISION_CONFLICT", "RUNTIME_FEATURE_BUSY"]);

/**
 * True when a patch failed because the revision sent with it is no longer current — either another
 * writer got there first (`RUNTIME_FEATURE_REVISION_CONFLICT`) or one is mid-write
 * (`RUNTIME_FEATURE_BUSY`).
 *
 * Both mean the same thing to a caller that sent `expectedRevision`: resending the same request is
 * futile, because the revision it carries can only be rejected again. Reread the record first, then
 * let the operator decide whether their change still applies.
 */
export function isRuntimeFeatureConflict(error: unknown): boolean {
    return (
        error instanceof ApiError && error.body.code != null && CONFLICT_CODES.has(error.body.code)
    );
}

/** Replaces a feature's audience. This is the only path that changes whether a feature is on. */
export async function patchRuntimeFeatureAudience(
    name: string,
    patch: RuntimeFeatureAudiencePatch,
    options?: FetchOptions,
): Promise<RuntimeFeature> {
    return apiFetch<RuntimeFeature>(`${ADMIN_BASE}/${encodeURIComponent(name)}`, {
        method: "PATCH",
        body: JSON.stringify(patch),
        signal: options?.signal,
    });
}

export async function getRuntimeFeatureHistory(
    params: RuntimeFeatureHistoryPageParams,
    options?: FetchOptions,
): Promise<Page<RuntimeFeatureHistoryItem>> {
    const query = buildPageParams({ ...params }).toString();
    return apiFetch<Page<RuntimeFeatureHistoryItem>>(
        `${ADMIN_BASE}/history${query ? `?${query}` : ""}`,
        { signal: options?.signal },
    );
}

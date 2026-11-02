// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export type ApiErrorBody = {
    code?: string;
    message?: string;
    /** Finer-grained reason within `code`, when the failure has one. */
    detailCode?: string;
    /** The request field the failure concerns, when it concerns one. */
    field?: string;
};

const CSRF_REJECTED_CODE = "CSRF_REJECTED";
const FORBIDDEN_MESSAGE = "You don't have permission to do that.";

function selectApiErrorMessage(status: number, body: ApiErrorBody): string {
    const statusFallback = `Request failed with status ${status}`;
    if (status === 403) {
        return body.code === CSRF_REJECTED_CODE ? statusFallback : FORBIDDEN_MESSAGE;
    }
    return typeof body.message === "string" ? body.message : statusFallback;
}

export class ApiError extends Error {
    public readonly status: number;
    public readonly body: ApiErrorBody;
    public readonly rawBody: unknown;

    constructor(status: number, body: unknown) {
        const apiBody = (body != null && typeof body === "object" ? body : {}) as ApiErrorBody;

        super(selectApiErrorMessage(status, apiBody));

        this.name = "ApiError";
        this.status = status;
        this.body = apiBody;
        this.rawBody = body;
    }
}

/**
 * Statuses that must never be retried: the session is gone (401), the caller
 * isn't allowed (403), or the resource doesn't exist (404). Retrying any of
 * these just burns round-trips on an answer that won't change.
 *
 * Lives with `ApiError` so the app-wide retry default and any query that
 * overrides `retry` for its own reasons share one definition of the exclusion.
 */
export function isNonRetryableAuthError(error: unknown): boolean {
    return (
        error instanceof ApiError &&
        (error.status === 401 || error.status === 403 || error.status === 404)
    );
}

export function getErrorMessage(err: unknown, fallback = "An error occurred"): string {
    if (err instanceof ApiError) return err.message;
    if (err instanceof Error) {
        return err.message;
    }
    return fallback;
}

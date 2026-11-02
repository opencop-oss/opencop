// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { ApiError } from "./error.ts";

const logger = Logger.get("api");
const OPENCOP_CORE_API_URL = import.meta.env.VITE_OPENCOP_CORE_API_URL;

if (!OPENCOP_CORE_API_URL) {
    throw new Error("VITE_OPENCOP_CORE_API_URL environment variable is required but not set.");
}

const DEFAULT_API_VERSION = "/v1";
const API_BASE = OPENCOP_CORE_API_URL + DEFAULT_API_VERSION;
const CSRF_ACQUISITION_TIMEOUT_MS = 10_000;

/** Core API version segment a request targets; `/v1` unless the endpoint exists only in a later one. */
export type ApiVersion = "/v1" | "/v2";

type ApiVersionOptions = {
    apiVersion?: ApiVersion;
};

export type IgnoreSuccessfulResponseBodyOptions = RequestInit &
    ApiVersionOptions & {
        /** Skip reading a successful response body. Non-success responses are always read for ApiError. */
        ignoreSuccessfulResponseBody: true;
    };

export type JsonResponseOptions = RequestInit &
    ApiVersionOptions & {
        ignoreSuccessfulResponseBody?: false;
    };

export type ApiFetchOptions = IgnoreSuccessfulResponseBodyOptions | JsonResponseOptions;

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

type CsrfToken = {
    value: string;
    generation: number;
};

type CsrfTokenAcquisition = {
    generation: number;
    promise: Promise<CsrfToken>;
    controller: AbortController;
    activeCallers: number;
    settled: boolean;
};

let csrfGeneration = 0;
let csrfToken: CsrfToken | undefined;
let csrfTokenAcquisition: CsrfTokenAcquisition | undefined;
/** Current generation whose recovery can still be claimed by a late stale rejection. */
let csrfPendingRecoveryGeneration: number | undefined;

function isSafeMethod(method: string): boolean {
    return SAFE_METHODS.has(method.toUpperCase());
}

function parseCsrfToken(body: unknown): string {
    if (typeof body !== "object" || body === null || !("token" in body)) {
        throw new TypeError("Invalid CSRF token response");
    }

    const { token } = body;
    if (typeof token !== "string" || token.trim() === "") {
        throw new TypeError("Invalid CSRF token response");
    }

    return token;
}

/**
 * Converts a non-success response to the shared error contract. The exact CSRF lifecycle rejection
 * is informational because `apiFetch` may recover it once; every other response remains an error.
 * Response-body read failures are intentionally swallowed because the HTTP status remains usable.
 */
async function toApiError(response: Response, method: string, path: string): Promise<ApiError> {
    let body: unknown = null;
    try {
        const text = await response.text();
        try {
            body = JSON.parse(text);
        } catch {
            body = text;
        }
    } catch {
        // Ignore response-body read errors; status remains authoritative.
    }
    const error = new ApiError(response.status, body);
    const logMethod = isCsrfRejected(error) ? "info" : "error";
    const logMessage = `${method} ${path} failed with status ${response.status}`;
    if (response.status === 401 || response.status === 403) {
        // Authentication and authorization bodies can contain protected validation details.
        // Keep the parsed body on ApiError for application handling, but never send it to a sink.
        logger[logMethod](logMessage);
    } else {
        logger[logMethod](logMessage, body);
    }
    return error;
}

/**
 * Cancels work whose CSRF generation was superseded before dispatch or replay. This intentionally
 * shares `AbortError` semantics with caller cancellation, but must not be suppressed generically:
 * the unsafe business request may not have been sent.
 */
function csrfInvalidatedError(): DOMException {
    return new DOMException("CSRF token acquisition was invalidated", "AbortError");
}

function isCsrfRejected(error: unknown): error is ApiError {
    return error instanceof ApiError && error.status === 403 && error.body.code === "CSRF_REJECTED";
}

function isReplayableBody(body: BodyInit | null | undefined): boolean {
    return typeof ReadableStream === "undefined" || !(body instanceof ReadableStream);
}

/** Copies caller-owned mutable bodies before async preparation so replay preserves entry-time data. */
function snapshotRequestBody(body: BodyInit | null | undefined): BodyInit | null | undefined {
    if (body instanceof FormData) {
        const snapshot = new FormData();
        for (const [name, value] of body.entries()) {
            snapshot.append(name, value);
        }
        return snapshot;
    }
    if (body instanceof URLSearchParams) {
        return new URLSearchParams(body);
    }
    if (body instanceof ArrayBuffer) {
        return body.slice(0);
    }
    if (ArrayBuffer.isView(body)) {
        return new Uint8Array(body.buffer, body.byteOffset, body.byteLength).slice();
    }
    return body;
}

/**
 * Retires `generation` only while it is current: advances the generation and clears its settled
 * token and pending recovery ownership. Returns false after a newer generation supersedes it.
 */
function invalidateCsrfTokenGeneration(generation: number): boolean {
    if (generation !== csrfGeneration) return false;

    csrfGeneration++;
    csrfToken = undefined;
    csrfPendingRecoveryGeneration = undefined;
    return true;
}

/**
 * True when the current generation has a settled token, an in-flight acquisition, or preserved
 * recovery ownership, allowing an older replayable rejection to join or start one bounded recovery.
 */
function hasCurrentCsrfRecoveryState(): boolean {
    return (
        csrfToken?.generation === csrfGeneration ||
        csrfTokenAcquisition?.generation === csrfGeneration ||
        csrfPendingRecoveryGeneration === csrfGeneration
    );
}

/** Waits with caller-local cancellation and aborts bootstrap when its last caller leaves. */
function waitForCsrfToken(
    acquisition: CsrfTokenAcquisition,
    signal?: AbortSignal | null,
): Promise<CsrfToken> {
    acquisition.activeCallers++;
    const abortSignal = signal ?? undefined;

    return new Promise((resolve, reject) => {
        let released = false;
        const release = () => {
            if (released) return;
            released = true;
            acquisition.activeCallers--;
            if (acquisition.activeCallers === 0 && !acquisition.settled) {
                // Detach before abort so an immediate retry cannot join the doomed acquisition.
                if (csrfTokenAcquisition === acquisition) {
                    csrfPendingRecoveryGeneration = acquisition.generation;
                    csrfTokenAcquisition = undefined;
                }
                acquisition.controller.abort();
            }
        };
        const onAbort = () => {
            abortSignal?.removeEventListener("abort", onAbort);
            release();
            reject(abortSignal?.reason ?? csrfInvalidatedError());
        };
        abortSignal?.addEventListener("abort", onAbort, { once: true });
        acquisition.promise.then(
            (token) => {
                abortSignal?.removeEventListener("abort", onAbort);
                release();
                resolve(token);
            },
            (error: unknown) => {
                abortSignal?.removeEventListener("abort", onAbort);
                release();
                reject(error);
            },
        );
    });
}

function startCsrfTokenAcquisition(): CsrfTokenAcquisition {
    const acquisitionGeneration = csrfGeneration;
    if (csrfPendingRecoveryGeneration === acquisitionGeneration) {
        csrfPendingRecoveryGeneration = undefined;
    }
    const controller = new AbortController();
    const signal = withRequestTimeout(controller.signal, CSRF_ACQUISITION_TIMEOUT_MS);
    const request = (async () => {
        const path = "/csrf";
        const method = "GET";
        logger.debug(`${method} ${path}`);
        const response = await fetch(API_BASE + path, {
            credentials: "include",
            signal,
        });
        if (!response.ok) throw await toApiError(response, method, path);

        const token = parseCsrfToken(await response.json());

        if (csrfGeneration !== acquisitionGeneration) {
            throw csrfInvalidatedError();
        }

        csrfToken = {
            value: token,
            generation: acquisitionGeneration,
        };
        return csrfToken;
    })();
    const acquisition: CsrfTokenAcquisition = {
        generation: acquisitionGeneration,
        promise: request,
        controller,
        activeCallers: 0,
        settled: false,
    };
    acquisition.promise = request.finally(() => {
        acquisition.settled = true;
        if (csrfTokenAcquisition === acquisition) {
            csrfTokenAcquisition = undefined;
        }
    });
    csrfTokenAcquisition = acquisition;
    return acquisition;
}

async function acquireCsrfToken(signal?: AbortSignal | null): Promise<CsrfToken> {
    if (signal?.aborted) throw signal.reason ?? csrfInvalidatedError();
    if (csrfToken?.generation === csrfGeneration) {
        return csrfToken;
    }

    const acquisition =
        csrfTokenAcquisition?.generation === csrfGeneration
            ? csrfTokenAcquisition
            : startCsrfTokenAcquisition();
    return waitForCsrfToken(acquisition, signal);
}

/**
 * Advances the CSRF generation after the BFF session has definitively ended.
 * Settled state is cleared, and callers awaiting an older acquisition reject with `AbortError`.
 */
export function clearCsrfToken(): void {
    csrfGeneration++;
    csrfToken = undefined;
    csrfPendingRecoveryGeneration = undefined;
    csrfTokenAcquisition?.controller.abort(csrfInvalidatedError());
    csrfTokenAcquisition = undefined;
}

/**
 * Single fetch function for all API calls.
 * Handles credentials, error throwing, JSON parsing, and module-local CSRF state. Safe methods
 * (`GET`, `HEAD`, `OPTIONS`, `TRACE`) bypass CSRF preparation. Other methods lazily share one
 * concurrent `/csrf` acquisition per generation, bounded to ten seconds, and override
 * caller-provided `X-XSRF-TOKEN`.
 * An exact `403/CSRF_REJECTED` invalidates only the attempted generation and replays a replayable
 * request once with a shared replacement token. Streamed bodies and all other failures are terminal.
 * Clearing the generation during preparation or recovery rejects pending acquisition with `AbortError`.
 * Base URL is `OPENCOP_CORE_API_URL + apiVersion` (`/v1` by default) — callers pass just the path
 * (e.g., "/layers").
 *
 * Content-Type defaults to "application/json" when a body is present.
 * FormData bodies are excluded so the browser can set the multipart boundary.
 */
export function apiFetch(path: string, options: IgnoreSuccessfulResponseBodyOptions): Promise<void>;
export function apiFetch<T>(path: string, options?: JsonResponseOptions): Promise<T>;
export async function apiFetch<T>(path: string, options?: ApiFetchOptions): Promise<T | void> {
    const {
        ignoreSuccessfulResponseBody = false,
        apiVersion = DEFAULT_API_VERSION,
        ...requestOptions
    } = options ?? {};
    const url = OPENCOP_CORE_API_URL + apiVersion + path;
    const method = requestOptions.method ?? "GET";
    const safeMethod = isSafeMethod(method);
    const requestBody = safeMethod ? requestOptions.body : snapshotRequestBody(requestOptions.body);
    const needsJsonContentType = requestBody != null && !(requestBody instanceof FormData);
    const headers = new Headers(requestOptions.headers);
    if (needsJsonContentType && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }
    const sendRequest = async (): Promise<T | void> => {
        logger.debug(`${method} ${path}`);

        const res = await fetch(url, {
            credentials: "include",
            ...requestOptions,
            body: requestBody,
            headers,
        });

        if (!res.ok) {
            throw await toApiError(res, method, path);
        }

        if (ignoreSuccessfulResponseBody) return;

        const text = await res.text();
        if (!text) return undefined as T;
        return JSON.parse(text) as T;
    };

    if (safeMethod) return sendRequest();

    const token = await acquireCsrfToken(requestOptions.signal);
    if (token.generation !== csrfGeneration) {
        // Cancel before dispatch rather than sending a proof from a superseded lifecycle.
        throw csrfInvalidatedError();
    }
    headers.set("X-XSRF-TOKEN", token.value);

    try {
        return await sendRequest();
    } catch (error) {
        if (!isCsrfRejected(error)) throw error;

        const invalidatedCurrentGeneration = invalidateCsrfTokenGeneration(token.generation);
        if (!isReplayableBody(requestBody)) {
            if (invalidatedCurrentGeneration) {
                csrfPendingRecoveryGeneration = csrfGeneration;
            }
            throw error;
        }
        if (!invalidatedCurrentGeneration && !hasCurrentCsrfRecoveryState()) throw error;
        const replacementToken = await acquireCsrfToken(requestOptions.signal);
        if (replacementToken.generation !== csrfGeneration) {
            // Cancel before replay rather than sending a proof from a superseded lifecycle.
            throw csrfInvalidatedError();
        }
        headers.set("X-XSRF-TOKEN", replacementToken.value);
        try {
            return await sendRequest();
        } catch (replayError) {
            if (isCsrfRejected(replayError)) {
                invalidateCsrfTokenGeneration(replacementToken.generation);
            }
            throw replayError;
        }
    }
}

export { OPENCOP_CORE_API_URL };

/** Accepted by GET fetch functions so React Query can forward its AbortSignal. */
export type FetchOptions = { signal?: AbortSignal };

/**
 * Compose a caller's abort signal with a per-request timeout.
 *
 * Cancellation semantics are preserved either way: aborting through `signal`
 * (an unmount, `cancelQueries`) is still treated as a cancellation by the
 * caller that owns it, while the timeout firing rejects the request as an
 * ordinary error the caller can surface or retry.
 */
export function withRequestTimeout(signal: AbortSignal | undefined, ms: number): AbortSignal {
    const timeout = AbortSignal.timeout(ms);
    return signal ? AbortSignal.any([signal, timeout]) : timeout;
}

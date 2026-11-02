// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import type { PluginManager } from "@opencop/contributions/host";
import { STATIC_PLUGINS } from "./static-plugins";
import { enablementsStateRegistry } from "./plugin-records";

const logger = Logger.get("plugin-host");

/** One entry in the enablements document. `entryUrl` is used once runtime load lands. */
export interface EnablementEntry {
    id: string;
    entryUrl?: string;
    /**
     * Optional FE feature-flag id gating this entry. When set, the plugin loads
     * only if the host resolves the flag as enabled (see the `isFlagEnabled`
     * predicate on {@link runPluginLoader}); an entry with no `flag` always loads.
     * Per-entry, so one plugin can be gated while others ship unconditionally. No
     * entry declares a flag today.
     */
    flag?: string;
}

/** The enablements document — the FE's single backend contract (static file now, Core API later). */
export interface EnablementsDocument {
    plugins: EnablementEntry[];
}

/** Why an enablements fetch did not produce a usable document. */
export type EnablementsFailureReason =
    /** The request never completed — offline, DNS, TLS, CORS. */
    | "network"
    /** The server answered, but not with the document (5xx, 403, a proxy error page). */
    | "http"
    /** The document was served but is not valid JSON, or not the shape we require. */
    | "schema";

/**
 * The outcome of one enablements fetch.
 *
 * `absent` is the **only** silent case: a 404 is the documented "this install has
 * no plugins" state and is indistinguishable from a vanilla build. Every other
 * non-success is a `failed` an operator must be able to see, because it is a
 * misconfiguration or an outage, not a decision.
 *
 * Collapsing the two is what made a transient CDN blip look exactly like a
 * plugin-free install — which, now that a whole feature ships as a plugin, means
 * the feature silently disappears for the session with an empty admin table as
 * the only clue.
 */
export type EnablementsResult =
    | { readonly status: "absent" }
    | { readonly status: "loaded"; readonly document: EnablementsDocument }
    | {
          readonly status: "failed";
          readonly reason: EnablementsFailureReason;
          readonly detail: string;
      };

/**
 * What the last {@link runPluginLoader} run produced, for the admin surface.
 * `pending` is the pre-first-run state; `retrying` means a transient failure is
 * being re-attempted and the operator need not act yet.
 */
export type EnablementsState =
    | { readonly status: "pending" }
    | { readonly status: "retrying"; readonly attempt: number; readonly detail: string }
    | { readonly status: "absent" }
    | { readonly status: "loaded"; readonly pluginCount: number }
    | {
          readonly status: "failed";
          readonly reason: EnablementsFailureReason;
          readonly detail: string;
      };

/** Transient failures worth another attempt; a 4xx or a bad schema will not fix itself. */
const RETRYABLE: ReadonlySet<EnablementsFailureReason> = new Set(["network", "http"]);

/**
 * How hard to try before handing the failure to the operator. Exponential from
 * `baseDelayMs`, so the default spends ~1.5s total — long enough to ride out a
 * blip on app start, short enough that a real outage surfaces promptly.
 */
export interface RetryPolicy {
    readonly maxAttempts: number;
    readonly baseDelayMs: number;
}

export const DEFAULT_RETRY_POLICY: RetryPolicy = { maxAttempts: 3, baseDelayMs: 500 };

/**
 * `plugins` must be an array of entries with a string `id`. Validated rather than
 * cast, because `runPluginLoader` iterates it: a document whose `plugins` is an
 * object (a hand-edited file, a Core API shape change) previously threw an
 * unhandled rejection out of an unawaited promise instead of reporting a
 * misconfiguration.
 */
function validateDocument(value: unknown): EnablementsDocument | string {
    if (typeof value !== "object" || value === null) return "document is not an object";
    const { plugins } = value as { plugins?: unknown };
    if (!Array.isArray(plugins)) return "`plugins` is missing or not an array";
    for (const [index, entry] of plugins.entries()) {
        if (typeof entry !== "object" || entry === null)
            return `plugins[${index}] is not an object`;
        const { id, entryUrl, flag } = entry as Record<string, unknown>;
        if (typeof id !== "string" || id.length === 0)
            return `plugins[${index}].id is missing or empty`;
        if (entryUrl !== undefined && typeof entryUrl !== "string")
            return `plugins[${index}].entryUrl is not a string`;
        if (flag !== undefined && typeof flag !== "string")
            return `plugins[${index}].flag is not a string`;
    }
    return { plugins: plugins as EnablementEntry[] };
}

/**
 * Fetch and validate the enablements document from its fixed path.
 *
 * Distinguishes the four outcomes rather than returning `null` for all of them —
 * see {@link EnablementsResult} for why absence and failure must not share a
 * representation.
 */
export async function loadEnablements(): Promise<EnablementsResult> {
    let res: Response;
    try {
        res = await fetch("/enablements.json", { cache: "no-store" });
    } catch (err) {
        return { status: "failed", reason: "network", detail: String(err) };
    }

    // A missing document is the expected "no plugins" case — stay silent.
    if (res.status === 404) return { status: "absent" };
    if (!res.ok) {
        return {
            status: "failed",
            reason: "http",
            detail: `${res.status} ${res.statusText}`.trim(),
        };
    }

    let parsed: unknown;
    try {
        parsed = await res.json();
    } catch (err) {
        return { status: "failed", reason: "schema", detail: `invalid JSON: ${String(err)}` };
    }

    const validated = validateDocument(parsed);
    if (typeof validated === "string")
        return { status: "failed", reason: "schema", detail: validated };

    return { status: "loaded", document: validated };
}

/** Publish the loader's outcome for {@link usePluginEnablementsState}. */
function publish(state: EnablementsState): void {
    enablementsStateRegistry.replace([state]);
}

const wait = (ms: number): Promise<void> =>
    new Promise((resolve) => {
        setTimeout(resolve, ms);
    });

/**
 * Fetch enablements, retrying a transient failure with a short backoff. A `schema`
 * failure is returned immediately — re-fetching a malformed file yields the same
 * malformed file.
 */
async function fetchWithRetry(
    policy: RetryPolicy,
    isCancelled?: () => boolean,
): Promise<EnablementsResult> {
    let last: EnablementsResult = { status: "failed", reason: "network", detail: "not attempted" };
    for (let attempt = 1; attempt <= policy.maxAttempts; attempt++) {
        last = await loadEnablements();
        if (last.status !== "failed" || !RETRYABLE.has(last.reason)) return last;
        if (attempt === policy.maxAttempts || isCancelled?.()) return last;
        publish({ status: "retrying", attempt, detail: last.detail });
        logger.warn(
            `enablements fetch failed (${last.reason}: ${last.detail}); retrying (${attempt}/${policy.maxAttempts - 1})`,
        );
        await wait(policy.baseDelayMs * 2 ** (attempt - 1));
        // Re-check after the wait, not only before it: a teardown landing mid-backoff
        // would otherwise still spend one more request before the next iteration
        // noticed. Nothing stale can reach the manager either way — `runPluginLoader`
        // checks again before it reconciles — so this saves the request, not a bug.
        if (isCancelled?.()) return last;
    }
    return last;
}

/**
 * Read enablements and **reconcile** the manager's loaded set against them. In
 * this phase enabled ids resolve to statically-imported modules
 * ({@link STATIC_PLUGINS}).
 *
 * `isFlagEnabled` gates entries that carry a `flag`: an entry whose flag resolves
 * false is skipped, while an entry with no `flag` always loads — so one plugin can
 * be toggled without touching the others. Absent the predicate, any flagged entry
 * is treated as disabled (fail-closed).
 *
 * **Reconcile, don't reset.** On a re-run (e.g. a flag toggled), this unloads
 * only plugins that are no longer eligible and loads only newly-eligible ones,
 * leaving already-loaded-and-still-eligible plugins in place. That is what keeps
 * toggling one flag from tearing down (and briefly blanking the map for) an
 * unrelated plugin — the caller must therefore NOT unload everything before each
 * run; it just calls this again.
 *
 * **A failed fetch changes nothing.** Reconciling against a document we could not
 * read would unload every running plugin, so a failure publishes the state and
 * returns; already-loaded plugins keep working. Call again to retry.
 *
 * `isCancelled` lets a caller drop a load whose effect was torn down mid-flight
 * (the fetch has no natural await point the caller can abort). `retry` paces the
 * transient-failure attempts; see {@link DEFAULT_RETRY_POLICY}.
 *
 * TODO(native-federation): replace the static lookup with
 * `await import(entry.entryUrl)` and drop {@link STATIC_PLUGINS}.
 */
export async function runPluginLoader(
    manager: PluginManager,
    isFlagEnabled?: (flag: string) => boolean,
    isCancelled?: () => boolean,
    retry: RetryPolicy = DEFAULT_RETRY_POLICY,
): Promise<EnablementsState> {
    const result = await fetchWithRetry(retry, isCancelled);
    if (isCancelled?.()) return { status: "pending" };

    if (result.status === "failed") {
        logger.error(`enablements unavailable (${result.reason}): ${result.detail}`);
        const state = {
            status: "failed",
            reason: result.reason,
            detail: result.detail,
        } as const;
        publish(state);
        return state;
    }

    const entries = result.status === "absent" ? [] : result.document.plugins;

    // The ids that should be loaded given the current enablements + flag state.
    const eligible = new Set<string>();
    for (const entry of entries) {
        if (entry.flag && !isFlagEnabled?.(entry.flag)) continue;
        eligible.add(entry.id);
    }

    // Unload only what became ineligible (e.g. a flag turned off). Still-eligible
    // plugins are left running so an unrelated toggle doesn't disturb them.
    for (const record of manager.list()) {
        if (!eligible.has(record.manifest.id)) manager.unload(record.manifest.id);
    }

    // Anything already loaded and active is left as-is; reloading it would need-
    // lessly tear down and rebuild its contributions.
    const active = new Set(
        manager
            .list()
            .filter((r) => r.status === "active")
            .map((r) => r.manifest.id),
    );

    for (const entry of entries) {
        if (isCancelled?.()) return { status: "pending" };
        if (!eligible.has(entry.id) || active.has(entry.id)) continue;
        const module = STATIC_PLUGINS[entry.id];
        if (!module) {
            logger.warn(`enabled plugin "${entry.id}" has no registered module`);
            continue;
        }
        manager.load(module);
    }

    const state =
        result.status === "absent"
            ? ({ status: "absent" } as const)
            : ({ status: "loaded", pluginCount: entries.length } as const);
    publish(state);
    return state;
}

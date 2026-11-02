// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ComponentType, ReactElement } from "react";
import {
    containComponentIn,
    createContributionBoundary,
    type ContributionBoundaryProps,
} from "./boundary.tsx";
import { describeOrigin, type ContributionOrigin } from "./origin.ts";

/**
 * How many times in a row one origin may throw before the host stops calling it
 * and goes straight to its fallback.
 *
 * **Consecutive** is the load-bearing word: it is what separates "one malformed
 * element among good ones" from "this contribution is broken". Intermittent bad
 * data can never accumulate a streak, because every success resets it — so a
 * matcher that chokes on one weird code keeps working for every other element.
 * A genuinely broken contribution throws on every call and trips within a single
 * routing batch.
 *
 * Ten, not three: a source can legitimately push a short run of malformed
 * elements, and a false trip costs more than ten swallowed throws. Ten, not
 * fifty: `resolveSpec` runs per element and this codebase routinely carries tens
 * of thousands, so the throws are not free.
 */
export const CONSECUTIVE_FAILURE_LIMIT = 10;

interface OriginState {
    readonly origin: ContributionOrigin;
    failures: number;
    logged: boolean;
    tripped: boolean;
    lastError: unknown;
}

/** Where the guard writes. */
export interface ContainmentLog {
    error(message: string, error: unknown): void;
    warn(message: string): void;
}

export interface ContainmentOptions {
    readonly log: ContainmentLog;
    /** Called for every contained failure — not only the first per origin, which is all the log gets. */
    readonly onFailure?: (origin: ContributionOrigin, error: unknown) => void;
}

/**
 * One containment: the failure state every contribution's guard shares, and the calls
 * that read and write it. One per host, so two share nothing. Every member is a plain function, safe to destructure.
 */
export interface Containment {
    /**
     * Log a contained contribution failure, attributed to its origin, at most once
     * per origin until that origin succeeds again.
     *
     * Exported for the call sites that must do their own containment — where a throw
     * is not a value to substitute but a control-flow decision the host has to make
     * (abort a draw session, say). They keep the reporting identical to
     * {@link runContribution}'s so every contained failure reads the same in a log,
     * and they feed the same streak.
     */
    reportContributionFailure(origin: ContributionOrigin, error: unknown): void;
    /**
     * Run one contributed callback, containing any throw.
     *
     * The host promises that a plugin cannot take the map down; this is where that
     * promise is kept, and it has to wrap **every** executable contribution rather
     * than the one that happened to get a `try` first. A matcher, a thumbnail URL and
     * an edit overlay are all plugin code running inside a host loop, and any of them
     * throwing on some particular element aborts the loop around it.
     *
     * `fallback` decides what containment means for that call — decline this element,
     * draw no thumbnail, fall back to the renderer this one displaced. It is not
     * itself guarded: it is host code.
     *
     * **A repeatedly-failing origin is dropped rather than re-asked.** After
     * {@link CONSECUTIVE_FAILURE_LIMIT} consecutive throws the call is skipped
     * entirely and the fallback is used directly. This is not a behaviour change —
     * the fallback is what the caller already gets on a throw — it stops the host
     * paying for a call it knows will fail, and gives "this contribution is out" a
     * stable state to report. Tripping is sticky until {@link rearmContributions}
     * clears it, which the renderer reconciler does whenever a contribution actually
     * changes, so a reloaded plugin recovers without a page reload.
     */
    runContribution<T>(
        origin: ContributionOrigin,
        call: () => T,
        fallback: (error: unknown) => T,
    ): T;
    /**
     * {@link runContribution} for a member wrapped once and called many times: the
     * origin's key is built here, once, rather than on every call — which is most of
     * the guard's cost on the success path.
     */
    guard<A extends unknown[], R>(
        origin: ContributionOrigin,
        call: (...args: A) => R,
        fallback: (error: unknown, ...args: A) => R,
    ): (...args: A) => R;
    /**
     * Clear one origin's failure state, for a call site that contained its own throw
     * and has now seen the same origin succeed.
     *
     * {@link runContribution} does this itself on its success path; the sites that
     * catch for themselves have no such path, so without this their once-per-origin
     * log latch never reopens and a *second, different* defect at the same origin is
     * swallowed for the rest of the session. Narrower than
     * {@link rearmContributions} on purpose: one origin, not every phase of a
     * contribution, because a working `behavior` says nothing about a broken `icon`.
     */
    clearContributionFailure(origin: ContributionOrigin): void;
    /**
     * Clear every origin belonging to one contribution — its re-arm.
     *
     * Called when a contribution is genuinely replaced (a plugin loads, unloads, or
     * a different renderer wins its identity), never on an ordinary rebuild, so a
     * fixed contribution starts being called again while a still-broken one stays
     * cheap. Matches on the origin object rather than parsing the key, so a
     * contribution id containing the key separator cannot break the re-arm.
     */
    rearmContributions(contribution: string): void;
    /** Forget every failure: a test seam. */
    reset(): void;
    /** The error boundary for a rendered contribution, reporting into this containment. */
    ContributionBoundary(props: ContributionBoundaryProps): ReactElement;
    /** A contributed component wrapped once in {@link ContributionBoundary}, attributed to `origin`. */
    containComponent<P extends object>(
        origin: ContributionOrigin,
        Component: ComponentType<P>,
    ): ComponentType<P>;
}

export function createContainment({ log, onFailure }: ContainmentOptions): Containment {
    /**
     * Per-origin failure state: the once-only log flag, the consecutive-failure
     * streak, and whether the origin is tripped. An entry is **deleted** on success,
     * so recovery resets everything at once.
     */
    const originStates = new Map<string, OriginState>();

    function stateFor(origin: ContributionOrigin, key: string): OriginState {
        let state = originStates.get(key);
        if (!state) {
            state = { origin, failures: 0, logged: false, tripped: false, lastError: undefined };
            originStates.set(key, state);
        }
        return state;
    }

    function report(origin: ContributionOrigin, key: string, error: unknown): OriginState {
        const state = stateFor(origin, key);
        state.failures += 1;
        state.lastError = error;
        onFailure?.(origin, error);
        if (!state.logged) {
            state.logged = true;
            log.error(`contribution "${key}" threw; the host contained it`, error);
        }
        return state;
    }

    /** Report a throw from a guarded call, and trip the origin once it has failed too often in a row. */
    function failed(origin: ContributionOrigin, key: string, error: unknown): void {
        const state = report(origin, key, error);
        if (!state.tripped && state.failures >= CONSECUTIVE_FAILURE_LIMIT) {
            state.tripped = true;
            log.error(
                `contribution "${key}" threw ${state.failures} times in a row; the host has stopped calling it ` +
                    `and will use its fallback until the contribution is re-registered`,
                // The error that was current when the breaker opened, which may not
                // be the one logged on the first failure — the failure mode can
                // change across the streak, and only this line reports the latest.
                state.lastError,
            );
        }
    }

    const reportContributionFailure = (origin: ContributionOrigin, error: unknown) =>
        void report(origin, describeOrigin(origin), error);
    const ContributionBoundary = createContributionBoundary(reportContributionFailure);

    return {
        reportContributionFailure,
        runContribution(origin, call, fallback) {
            const key = describeOrigin(origin);
            const tripped = originStates.get(key);
            if (tripped?.tripped) return fallback(tripped.lastError);
            try {
                const value = call();
                originStates.delete(key); // recovered, or never failed
                return value;
            } catch (error) {
                failed(origin, key, error);
                return fallback(error);
            }
        },
        guard(origin, call, fallback) {
            const key = describeOrigin(origin);
            return (...args) => {
                const tripped = originStates.get(key);
                if (tripped?.tripped) return fallback(tripped.lastError, ...args);
                try {
                    const value = call(...args);
                    originStates.delete(key);
                    return value;
                } catch (error) {
                    failed(origin, key, error);
                    return fallback(error, ...args);
                }
            };
        },
        clearContributionFailure: (origin) => void originStates.delete(describeOrigin(origin)),
        rearmContributions(contribution) {
            for (const [key, state] of originStates) {
                if (state.origin.contribution === contribution) originStates.delete(key);
            }
        },
        reset() {
            originStates.clear();
        },
        ContributionBoundary,
        containComponent: (origin, Component) =>
            containComponentIn(ContributionBoundary, origin, Component),
    };
}

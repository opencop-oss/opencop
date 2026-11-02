// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Layer 2 of the "no unmocked network" guarantee: a tally of requests MSW could
 * not match. `setup.ts` populates it from the `onUnhandledRequest` callback and
 * asserts it empty in a global `afterEach` — so a leak fails the test *in
 * teardown* even when the app swallows the rejection (React Query `retry:false`,
 * try/catch, fire-and-forget `.catch()`).
 *
 * Exported so the harness self-test can prove the mechanism without tripping the
 * teardown assertion (it drains the tally before `afterEach` runs).
 *
 * Caveat: the tally is a single module-level array cleared per test. A request
 * that leaks *and* whose MSW `onUnhandledRequest` callback only fires after the
 * owning test's synchronous `afterEach` has run (a genuinely un-awaited
 * fire-and-forget) can be misattributed to the next test — so a surprising
 * "unmocked network request" failure is worth checking against the *previous*
 * test too, not just the one that failed.
 */
export const unhandledRequests: string[] = [];

/** Empties the tally and returns what it held. */
export function drainUnhandledRequests(): string[] {
    return unhandledRequests.splice(0);
}

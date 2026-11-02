// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { http, HttpResponse } from "msw";
import { API_BASE, sessionHandlers } from "./session";

/**
 * App metadata is bootstrap: the built-in subject installer subscribes to the
 * backend feature list (the watchbox tool registers on it), so any test that
 * reaches the composition root issues this request. Answer with no features —
 * a test about a specific flag overrides it with `server.use(...)`.
 */
const appMetadataHandlers = [
    http.get(`${API_BASE}/app-metadata`, () => HttpResponse.json({ features: [] })),
];

/**
 * Baseline handlers registered on the shared MSW server. Keep this list to
 * *truly global* bootstrap endpoints only (auth/session, app metadata):
 * `server.resetHandlers()` reverts to exactly this set, so anything here
 * survives `afterEach`. Per-test handlers belong in the test via
 * `server.use(http.get(...))`.
 */
export const defaultHandlers = [...sessionHandlers, ...appMetadataHandlers];

export { API_BASE } from "./session";

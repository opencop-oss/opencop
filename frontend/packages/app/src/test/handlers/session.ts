// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { http, HttpResponse } from "msw";
import { createTestUser } from "../fixtures/user";
import { createTestPreferences } from "../fixtures/preferences";

/** API base under Vitest env: `VITE_OPENCOP_CORE_API_URL` (`http://localhost/api`) + `/v1`. */
export const API_BASE = "http://localhost/api/v1";

/**
 * Baseline auth/session handlers. These are the *safety net* for the
 * authenticated-by-default harness: `render` pre-seeds the auth query cache
 * (so no `/me` fetch normally fires), but a test that clears or invalidates
 * that cache still resolves against these instead of tripping
 * `onUnhandledRequest: "error"`.
 *
 * Authored as reusable functions so a future Storybook/Playwright layer could
 * share them (non-goal to wire that now).
 */
export const sessionHandlers = [
    http.get(`${API_BASE}/csrf`, () => HttpResponse.json({ token: "test-csrf-token" })),
    http.get(`${API_BASE}/me`, () => HttpResponse.json(createTestUser())),
    http.get(`${API_BASE}/me/preferences`, () => HttpResponse.json(createTestPreferences())),
    http.post(`${API_BASE}/bff/logout`, () => new HttpResponse(null, { status: 204 })),
];

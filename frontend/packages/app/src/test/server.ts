// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { setupServer } from "msw/node";
import { defaultHandlers } from "./handlers";

/**
 * Shared MSW server for component/hook tests. Its lifecycle
 * (`listen`/`resetHandlers`/`close`) and the no-network guarantees live in
 * `setup.ts`. Tests add per-case handlers with `server.use(...)`; those are
 * dropped by the `afterEach(resetHandlers)` back to {@link defaultHandlers}.
 */
export const server = setupServer(...defaultHandlers);

export { http, HttpResponse } from "msw";

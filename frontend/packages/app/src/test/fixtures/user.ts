// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { AuthUser } from "@opencop/api";

/**
 * Builds the {@link AuthUser} shape the real `/api/v1/me` endpoint returns.
 *
 * The harness seeds this into the auth query cache so components render
 * authenticated by default. Pass `overrides` (deep-merged one level) to vary
 * identity/authorities per test — e.g. `createTestUser({ authorities: ["ROLE_admin"] })`.
 */
export function createTestUser(overrides: Partial<AuthUser> = {}): AuthUser {
    return {
        authenticated: true,
        ...overrides,
        user: {
            id: "00000000-0000-0000-0000-000000000001",
            subject: "test-subject",
            username: "test.user",
            displayName: "Test User",
            email: "test.user@example.com",
            ...overrides.user,
        },
        idp: {
            issuer: "http://localhost:8070/realms/opencop",
            ...overrides.idp,
        },
        authorities: overrides.authorities ?? [],
        claims: overrides.claims ?? {},
    };
}

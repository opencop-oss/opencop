// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { UserPreferences } from "@opencop/api";

/**
 * Default {@link UserPreferences}. `useUserTimezone`, measurement-unit, and
 * zoom-behavior hooks read this on mount, so many components fetch it — hence a
 * baseline handler ships it by default. Override per test with `server.use(...)`.
 */
export function createTestPreferences(overrides: Partial<UserPreferences> = {}): UserPreferences {
    return {
        username: "test.user",
        locale: "en",
        measurementSystem: "METRIC",
        temperatureUnit: "CELSIUS",
        timezone: null,
        zoomBehavior: "CENTER",
        avatarMediaId: null,
        avatar: null,
        initialView: null,
        defaultTile: null,
        ...overrides,
    };
}

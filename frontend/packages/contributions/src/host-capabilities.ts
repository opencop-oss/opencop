// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ToolSession } from "@opencop/tools";
import type { DisplayPreferences, FilterFields } from "@opencop/ui";

/**
 * Every host capability a contribution's UI may read: what the host provides through a
 * context declared in a shared package, wherever that UI renders. The host provides each
 * at its own scope (filter fields for the app, display preferences in the map view, a
 * tool session per editor); a test host provides all of them at once, from a test value
 * for each — and a capability added here without one does not compile.
 *
 * The theme (and the color mode it carries) is not listed: it is the host's design system,
 * mounted around everything, not a value a contribution is handed.
 */
export interface HostCapabilities {
    readonly filterFields: FilterFields;
    readonly displayPreferences: DisplayPreferences;
    readonly toolSession: ToolSession;
}

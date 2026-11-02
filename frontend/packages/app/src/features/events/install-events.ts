// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { attachEventsEditingPolicy } from "./events-editing.policy";

/**
 * The events feature's install-time act. Events is a host feature, not a
 * subject — it registers no tool and no renderer — but it does react to the
 * interaction session (the editing overlay follows a custody edit), and that
 * reaction is the feature's to attach where it composes, exactly as
 * `installWatchbox` attaches its panel policy. The runtime attaches no feature
 * policy.
 *
 * Returns the detach; the composition root does not call it today (native
 * installers live for the app's lifetime), but the shape is the one an
 * HMR-safe root will need.
 */
export function installEvents(): () => void {
    return attachEventsEditingPolicy();
}

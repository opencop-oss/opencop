// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState } from "react";
import { usePluginEnablementsState } from "./plugin-records";

/** How long the map waits for the plugin loader before it mounts without the plugins. */
export const PLUGIN_LOAD_WAIT_MS = 3000;

/**
 * Whether the plugin loader has settled — loaded, found no enablements, or
 * failed — so a consumer that reads the contribution registries once, such as
 * the map's layer pipeline, sees every plugin's contributions from the start.
 * Native plugins need no wait: they install while the app renders, before any
 * route.
 *
 * A loader still pending or retrying after {@link PLUGIN_LOAD_WAIT_MS} does not
 * hold the consumer: it reads `true`, and a plugin that loads later arrives the
 * way a plugin enabled at run time does.
 */
export function usePluginLoadSettled(): boolean {
    const { status } = usePluginEnablementsState();
    const settled = status === "loaded" || status === "absent" || status === "failed";
    const [waitedOut, setWaitedOut] = useState(false);

    useEffect(() => {
        if (settled) return;
        const timer = setTimeout(() => setWaitedOut(true), PLUGIN_LOAD_WAIT_MS);
        return () => clearTimeout(timer);
    }, [settled]);

    return settled || waitedOut;
}

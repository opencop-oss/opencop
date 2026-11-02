// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { PluginModule } from "@opencop/contributions/plugin";
import symbologyPlugin from "@opencop/symbology-plugin";
import textBoxPlugin from "@opencop/textbox-plugin";

/**
 * TEMPORARY — resolves an enablement id to a statically-imported plugin module.
 * The one sanctioned host → plugin static import; delete this file (and its lint
 * exceptions) at the Native Federation flip, where the loader switches to
 * `import(entry.entryUrl)` instead.
 */
export const STATIC_PLUGINS: Record<string, PluginModule> = {
    "opencop.symbology": symbologyPlugin,
    "opencop.textbox": textBoxPlugin,
};

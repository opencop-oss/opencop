// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ToolDescriptor } from "@opencop/tools";

/** Joins a plugin's id to the local id of one of its tools. */
export const PLUGIN_TOOL_ID_SEPARATOR = ":";

/** The toolbar group a plugin's tools land in, so a divider sets them apart from the host's. */
export const PLUGIN_TOOL_GROUP = "plugin";

/** The id the host registers one plugin's tool under: the plugin's local id, namespaced. */
export function pluginToolId(pluginId: string, localId: string): string {
    return `${pluginId}${PLUGIN_TOOL_ID_SEPARATOR}${localId}`;
}

/**
 * A plugin's tool as the host registers it: its id namespaced, its label key pinned from
 * the local id first — the namespaced id holds i18next's namespace separator, so a key
 * derived from it downstream would parse as a namespace — and the plugin group. The one
 * spelling of that decoration.
 */
export function hostedPluginTool(pluginId: string, tool: ToolDescriptor): ToolDescriptor {
    return {
        ...tool,
        labelKey: tool.labelKey ?? `toolbar.${tool.id}`,
        id: pluginToolId(pluginId, tool.id),
        group: PLUGIN_TOOL_GROUP,
    };
}

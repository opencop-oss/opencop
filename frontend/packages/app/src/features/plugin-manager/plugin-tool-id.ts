// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The app's side of the tool-id **identity boundary** between a plugin and the host.
 *
 * The host namespaces a plugin's tool id on registration so two plugins (or a
 * plugin and a native tool) cannot collide. Every id that crosses back the other
 * way — an editor's own `activate()`, the attribution on a contained failure —
 * has to be translated against the same template. The template is written once,
 * as `pluginToolId` in `@opencop/contributions/validation`; two hand-written
 * spellings of it is exactly how a plugin ends up asking the registry for an id that
 * was never registered.
 *
 * Its own module (rather than living in `plugin-services.ts`) so the toolbar can
 * read it without importing the plugin-runtime adapter, which pulls in the
 * registries the adapter writes to.
 */

import { PLUGIN_TOOL_ID_SEPARATOR } from "@opencop/contributions/validation";
import { pluginOwner, type ContributionOwner } from "~/features/contributions/contribution-guard";

const SEPARATOR = PLUGIN_TOOL_ID_SEPARATOR;

/**
 * The plugin that owns a registered tool id, or `undefined` for a native tool
 * (the tool registry keeps no owner for natives yet).
 *
 * Used to attribute a contained failure in a tool's rendered contribution: a
 * descriptor carries no owner field, because `ToolDescriptor` is a leaf contract
 * and must not learn what a plugin is.
 */
export function ownerOfToolId(toolId: string): ContributionOwner | undefined {
    const at = toolId.indexOf(SEPARATOR);
    return at > 0 ? pluginOwner(toolId.slice(0, at)) : undefined;
}

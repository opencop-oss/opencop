// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useSyncExternalStore } from "react";
import Logger from "js-logger";
import type { Element } from "@opencop/elements";
import type { ContextMenuItemContribution } from "@opencop/contributions";
import { createContextMenuRegistry, type ContextMenuOpenedOn } from "@opencop/contributions/host";
import { hostContainment } from "~/features/contributions/contribution-guard";

const logger = Logger.get("plugins");

/**
 * The app's context-menu registry — the one instance of `createContextMenuRegistry`, on
 * the host's containment — and the hooks the menu reads it through.
 */
export const contextMenuRegistry = createContextMenuRegistry({ containment: hostContainment });

export type { ContextMenuOpenedOn };

/** Contribute a context-menu item, owned by `owner`. */
export const registerContextMenuItem = contextMenuRegistry.register;

/** The contributed items for what the menu was opened on, by order (undeclared last). */
export const resolveContextMenuItems = contextMenuRegistry.resolve;

const reportedMissingTools = new Set<string>();

/**
 * Report, once per item, an action item whose tool is not registered. The menu
 * hides the item; a missing tool is a composition mistake, not a run-time state.
 */
export function reportMissingTool(item: ContextMenuItemContribution, toolId: string): void {
    if (!import.meta.env.DEV || reportedMissingTools.has(item.id)) return;
    reportedMissingTools.add(item.id);
    logger.warn(
        `[context-menu]: item "${item.id}" activates tool "${toolId}", which is not registered; hidden.`,
    );
}

/**
 * The contributed items for an element, or for empty map when `element` is
 * absent, in this mode; re-resolved when the items change.
 */
export function useContextMenuItems(
    element: Element | undefined,
    historical: boolean,
): readonly ContextMenuItemContribution[] {
    const all = useSyncExternalStore(contextMenuRegistry.subscribe, contextMenuRegistry.getEntries);
    const type = element?.type;
    return useMemo(
        () => resolveContextMenuItems({ type, historical }, all),
        [all, type, historical],
    );
}

/** Every registration, by identity, for tests that check what a disposal removed. */
export function getContextMenuEntries(): readonly object[] {
    return contextMenuRegistry.getEntries();
}

/** How many items are registered, for tests that assert disposal empties the registry. */
export function getContextMenuItemCount(): number {
    return contextMenuRegistry.getEntries().length;
}

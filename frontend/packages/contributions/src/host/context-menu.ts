// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ContextMenuItemContribution } from "../context-menu.ts";
import {
    containContextMenuItem,
    type Containment,
    type ContributionOwner,
} from "../containment/index.ts";
import { validateContextMenuItem } from "../validation/index.ts";
import { refuseProblems } from "./refusal.ts";
import { Registry } from "./registry.ts";

/** A registration, with its owner and its `Content` contained. */
export interface ContextMenuEntry extends ContributionOwner {
    readonly item: ContextMenuItemContribution;
}

/** What the menu was opened on, as the registry filters items by it. */
export interface ContextMenuOpenedOn {
    /** The type of the element under the click, or `undefined` on ground. */
    readonly type?: string;
    readonly historical: boolean;
}

export interface ContextMenuRegistry {
    /** Contribute an item, owned by `owner`. Refuses a malformed item or a taken id. */
    register(owner: ContributionOwner, item: ContextMenuItemContribution): () => void;
    /** The items for what the menu was opened on, by order (undeclared last). */
    resolve(
        at: ContextMenuOpenedOn,
        all?: readonly ContextMenuEntry[],
    ): readonly ContextMenuItemContribution[];
    /** Every registration, with its owner. A fresh array on each change. */
    readonly getEntries: () => readonly ContextMenuEntry[];
    readonly subscribe: (listener: () => void) => () => void;
}

function applies(item: ContextMenuItemContribution, at: ContextMenuOpenedOn): boolean {
    if (item.mutates && at.historical) return false;
    if (item.target.kind === "ground") return at.type === undefined;
    return item.target.type === at.type;
}

/**
 * The registry of the map context menu's contributed items. The menu lists its own items
 * first, then the contributed ones that apply to what it was opened on, by `order`. A
 * `Content` body is contained once, at registration. One instance per host.
 */
export function createContextMenuRegistry({
    containment,
}: {
    readonly containment: Containment;
}): ContextMenuRegistry {
    const registry = new Registry<ContextMenuEntry>();
    return {
        register(owner, item) {
            refuseProblems("context-menu", validateContextMenuItem(item));
            if (registry.getAll().some((entry) => entry.item.id === item.id))
                throw new Error(`[context-menu]: refused: id "${item.id}" is already registered.`);
            const entry: ContextMenuEntry = {
                ...owner,
                item: containContextMenuItem(containment, owner, item),
            };
            registry.add(entry);
            return () => registry.remove((candidate) => candidate === entry);
        },
        resolve: (at, all = registry.getAll()) =>
            all
                .map((entry) => entry.item)
                .filter((item) => applies(item, at))
                .sort((a, b) => (a.order ?? Infinity) - (b.order ?? Infinity) || 0),
        getEntries: registry.getAll,
        subscribe: registry.subscribe,
    };
}

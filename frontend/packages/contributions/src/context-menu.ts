// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ComponentType } from "react";
import type { Element } from "@opencop/elements";

/**
 * Where a right-click must land for a context-menu item to show: on empty map
 * ("ground"), or on a map object of one `type` (`MapObject.type`) in one store.
 * `store` defaults to the element store, and only element-store objects open
 * the menu today, so a target naming another store is refused.
 */
export type ContextMenuTarget =
    | { readonly kind: "ground" }
    | { readonly kind: "object"; readonly store?: string; readonly type: string };

/** What the menu was opened on, as it was when the menu opened. */
export interface ContextMenuSnapshot {
    /** Where the right-click landed, `[lng, lat]`. */
    readonly lngLat: readonly [number, number];
    /** The element under it; absent on ground. */
    readonly element?: Element;
}

/** What a contributed menu body receives. */
export interface ContextMenuContentProps {
    readonly target: ContextMenuSnapshot;
    /** Close the menu. */
    close(): void;
}

interface ContextMenuItemBase {
    /** Unique among context-menu items. */
    readonly id: string;
    /** Place among the contributed items, which follow the host's own; lower first. */
    readonly order?: number;
    readonly target: ContextMenuTarget;
    /** The item changes data, so it is hidden in historical mode. */
    readonly mutates?: boolean;
}

/** An item that starts a tool: the host activates it, at the right-click when `placeAt`. */
export interface ContextMenuActionItem extends ContextMenuItemBase {
    /** Translation key of the item's label. */
    readonly labelKey: string;
    readonly activate: { readonly toolId: string; readonly placeAt?: boolean };
}

/**
 * An item that draws its own menu body — a readout, a group of options. It
 * renders inside the menu, so it uses the menu's own item components. Contained:
 * one that throws shows nothing.
 */
export interface ContextMenuContentItem extends ContextMenuItemBase {
    readonly Content: ComponentType<ContextMenuContentProps>;
}

/**
 * An entry in the map's right-click menu. The host's own items come first; the
 * contributed ones follow by `order`. The target is fixed when the menu opens.
 */
export type ContextMenuItemContribution = ContextMenuActionItem | ContextMenuContentItem;

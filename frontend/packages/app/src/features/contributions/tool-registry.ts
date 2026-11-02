// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useSyncExternalStore } from "react";
import Logger from "js-logger";
import { createToolRegistry } from "@opencop/contributions/host";
import { hostContainment } from "~/features/contributions/contribution-guard";
import { type ToolLocation, type ToolDescriptor, producedElementType } from "@opencop/tools";

const logger = Logger.get("toolbar");

/**
 * The unified toolbar-tool registry.
 *
 * ONE registry, app-owned, holding **both** native tools and plugin tools —
 * they are the same kind of entry, so the toolbar can't tell them apart. The
 * descriptor type ({@link ToolDescriptor}) lives in `@opencop/tools`: it is
 * the single contract a subject registers, native or plugin. Native features
 * register at startup ({@link import("./tool-registrations")}); plugin tools are
 * injected by the loader through the host `registerTool` service, which lands
 * the *same* descriptor here — there is no narrow→rich bridge.
 *
 * A descriptor carries one of two capabilities: `draw` (a draw kit, whose
 * grammar a session runs — a built-in one, or the tool's own) or `content` (a
 * popover body). Its interaction fields — `shortcut`, `cursor`,
 * the kit's `editModel` and `hints`, `onComplete`, `onEscapeInEdit` — are all
 * optional and defaulted, so a tool that declares none still behaves the way
 * every shape tool does today.
 */
export type { ToolDescriptor, ToolParams } from "@opencop/tools";

const DEFAULT_ORDER = 100;
const DEFAULT_LOCATION: ToolLocation = "topBarStart";

/** Which groups of a location a piece of chrome draws: one group, or every group but one. */
export type GroupFilter = { readonly only: string } | { readonly except: string };

/** The app's tool registry: the one instance of `createToolRegistry`, on the host's containment. */
export const toolRegistry = createToolRegistry({
    containment: hostContainment,
    log: import.meta.env.DEV ? logger : undefined,
});

/**
 * Add a descriptor; returns a disposer that removes exactly this entry. Native tools add
 * once at startup; plugin tools add on load and remove on unload.
 */
export const registerTool = toolRegistry.register;

/** Non-reactive lookup by id (e.g. the interaction runtime resolving the active tool). */
export const getTool = toolRegistry.get;

/** Non-reactive read of all registered descriptors (the keyboard router's shortcut scan). */
export function getAllTools(): readonly ToolDescriptor[] {
    return toolRegistry.getAll();
}

/**
 * Notify on every registry change (add / remove / replace). Non-reactive
 * subscribers — the interaction runtime, which exits a session whose
 * descriptor was just removed — read the new state through the getters.
 */
export const subscribeTools = toolRegistry.subscribe;

/**
 * Non-reactive lookup by the element type a tool produces. It resolves plugin
 * tools too: a plugin's tool is registered under a namespaced `id`, so the id
 * cannot be reconstructed from the element's type, but `produces` can be read.
 *
 * **First registration wins.**
 */
export function toolForElementType(elementType: string): ToolDescriptor | undefined {
    return toolRegistry
        .getAll()
        .find((descriptor) => producedElementType(descriptor.produces) === elementType);
}

function inGroups(descriptor: ToolDescriptor, groups: GroupFilter | undefined): boolean {
    if (!groups) return true;
    return "only" in groups ? descriptor.group === groups.only : descriptor.group !== groups.except;
}

/**
 * Pick the descriptors in one location — narrowed to `groups` when given — ordered
 * by `order` (ties keep registration order — `Array#sort` is stable). `filter`
 * copies before `sort`, so the registry's own array is never mutated. Pure +
 * exported for testing.
 */
export function selectToolsForLocation(
    all: readonly ToolDescriptor[],
    location: ToolLocation,
    groups?: GroupFilter,
): readonly ToolDescriptor[] {
    return all
        .filter((descriptor) => (descriptor.location ?? DEFAULT_LOCATION) === location)
        .filter((descriptor) => inGroups(descriptor, groups))
        .sort((a, b) => (a.order ?? DEFAULT_ORDER) - (b.order ?? DEFAULT_ORDER));
}

/**
 * Reactive read of all registered descriptors (native + injected plugin tools).
 * Internal to the registry — consumers read a location via {@link useToolsForLocation};
 * export it only when a real unfiltered-list consumer appears.
 */
function useToolDescriptors(): readonly ToolDescriptor[] {
    return useSyncExternalStore(toolRegistry.subscribe, toolRegistry.getAll, toolRegistry.getAll);
}

/**
 * Reactive read of one location's descriptors, narrowed to one `group` when given.
 * A runtime plugin (un)load — or a native installer registering on a flag —
 * mutates the registry, so the chrome re-renders and picks up / drops the
 * tools. The derive runs in `useMemo` over the snapshot, not inside
 * `getSnapshot` (`useSyncExternalStore` requires a cached snapshot).
 */
export function useToolsForLocation(
    location: ToolLocation,
    group?: string,
): readonly ToolDescriptor[] {
    const all = useToolDescriptors();
    return useMemo(
        () =>
            selectToolsForLocation(
                all,
                location,
                group === undefined ? undefined : { only: group },
            ),
        [all, location, group],
    );
}

/**
 * Reactive read of one location's descriptors outside `group` — the complement of
 * {@link useToolsForLocation} with a group, so two pieces of chrome can split one
 * location between them without either listing the other's tools.
 */
export function useToolsForLocationExcluding(
    location: ToolLocation,
    group: string,
): readonly ToolDescriptor[] {
    const all = useToolDescriptors();
    return useMemo(
        () => selectToolsForLocation(all, location, { except: group }),
        [all, location, group],
    );
}

/** Test seam — the registry is module-level singleton state. */
export function __resetToolRegistry(): void {
    toolRegistry.clear();
}

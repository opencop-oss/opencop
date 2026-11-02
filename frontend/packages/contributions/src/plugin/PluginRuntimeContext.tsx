// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    createContext,
    createElement,
    use,
    type ComponentType,
    type ReactElement,
    type ReactNode,
} from "react";
import type { PluginContext } from "./context.ts";

const PluginRuntimeContext = createContext<PluginContext | null>(null);

/**
 * Provides a plugin's injected {@link PluginContext} to its contributed UI. The
 * host wraps each plugin-contributed component in this provider bound to the
 * owning plugin's context.
 */
export function PluginContextProvider(props: {
    value: PluginContext;
    children: ReactNode;
}): ReactElement {
    return <PluginRuntimeContext value={props.value}>{props.children}</PluginRuntimeContext>;
}

/** Reads the injected {@link PluginContext} from within plugin UI. */
export function usePluginContext(): PluginContext {
    const ctx = use(PluginRuntimeContext);
    if (!ctx) {
        throw new Error("usePluginContext must be used within a plugin's PluginContextProvider");
    }
    return ctx;
}

/**
 * `Component` bound to a plugin's context ONCE — at registration, not at each host
 * render site. The host renders the returned component directly; its identity is
 * stable for the plugin's lifetime, so it never remounts.
 */
export function bindToPluginContext<P extends object>(
    Component: ComponentType<P>,
    context: PluginContext,
    displayName: string,
): ComponentType<P> {
    const Bound = (props: P) =>
        createElement(PluginContextProvider, {
            value: context,
            children: createElement(Component, props),
        });
    Bound.displayName = displayName;
    return Bound;
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Element } from "@opencop/elements";
import type { InputSpec } from "@opencop/tools";
import { useToolStore, styleKey } from "./tool.store";
import { useSessionDraft } from "~/features/map/interaction-runtime/session/selectors";
import {
    getInteractionState,
    sessionDraft,
} from "~/features/map/interaction-runtime/session/interaction.store";

/**
 * Read the current drawing/editing object from the interaction session.
 */
export function useToolDraft() {
    return useSessionDraft();
}

/**
 * Read the current style value for a spec from the store,
 * falling back to the spec's initial default.
 */
export function useStyleValue<T>(toolKey: string, specLabel: string, initialDefault: T): T {
    const styleValue = useToolStore(
        (s) => (s.styleValues[styleKey(toolKey, specLabel)] as T) ?? initialDefault,
    );
    return styleValue;
}

export function getCurrentStyleValue<T>(toolKey: string, specLabel: string, initialDefault: T): T {
    return (
        (useToolStore.getState().styleValues[styleKey(toolKey, specLabel)] as T) ?? initialDefault
    );
}

/**
 * The session's product when it is an element, resolved outside React
 * (style-change callbacks). Style inputs write element style, and only a tool
 * whose product is an element may declare them, so any other draft has none.
 */
function currentSessionElement(): Element | null {
    const draft = sessionDraft(getInteractionState().session);
    return draft instanceof Element ? draft : null;
}

/**
 * Returns an onChange callback that updates the element AND persists
 * the value in the store for next-element reuse.
 *
 * Pure during render — no side effects. Style application to new elements
 * is handled by the interaction runner's onUpdate.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function createStyleChangeHandler<T extends InputSpec<any>>(
    spec: T,
    toolKey: string,
    specLabel: string,
) {
    return (v: T["defaultValue"]) => {
        useToolStore.getState().setStyleValue(styleKey(toolKey, specLabel), v);
        const el = currentSessionElement();
        if (el) spec.onChange(el, v as never);
    };
}

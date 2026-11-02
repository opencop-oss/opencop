// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render as rtlRender, type RenderOptions } from "@testing-library/react";
import { ChakraProvider, defaultSystem } from "@chakra-ui/react";
import {
    ColorModeProvider,
    DisplayPreferencesProvider,
    type DisplayPreferences,
} from "@opencop/ui";
import { ToolSessionScope, type ToolSession } from "@opencop/tools";
import { useMemo, useState, type ReactElement, type ReactNode } from "react";
import i18next from "i18next";
import { initReactI18next } from "react-i18next";
import en from "../locales/en.json";
import { noop } from "lodash";

/**
 * The plugin's own render harness, mirroring `symbology-plugin/src/test`.
 *
 * Not the app's `~/test`: a plugin cannot import host source. Only this plugin's
 * i18next namespace is registered, which also proves its components address
 * strings as `textbox:…` rather than relying on the host bundle.
 */
void i18next.use(initReactI18next).init({
    lng: "en",
    fallbackLng: "en",
    defaultNS: "translation",
    resources: { en: { translation: {}, textbox: en } },
    interpolation: { escapeValue: false },
});

/** A `ToolSession` with nothing in progress; spread to override per test. */
export const idleToolSession: ToolSession = {
    draft: null,
    params: {},
    phase: "idle",
    setParams: noop,
    invalidate: noop,
    activate: noop,
    cancel: noop,
};

const displayPreferences: DisplayPreferences = {
    measurementSystem: "METRIC",
    coordFormat: "DD",
    setCoordFormat: noop,
    angleUnit: "degrees",
    setAngleUnit: noop,
    angleOrientation: "cw",
};

export interface PluginRenderOptions extends Omit<RenderOptions, "wrapper"> {
    /** The session the editor will read. Defaults to {@link idleToolSession}. */
    readonly session?: ToolSession;
    /** Defaults to metric; set to check what an operator in feet sees. */
    readonly preferences?: Partial<DisplayPreferences>;
}

export function render(
    ui: ReactElement,
    { session, preferences, ...options }: PluginRenderOptions = {},
) {
    /**
     * Re-renders when the editor invalidates or changes the params, the way the
     * host's scope does.
     *
     * With a fixed revision, a no-op `invalidate` and params that never change,
     * a controlled field never sees its own edits: React restores the previous
     * value after each keystroke, so a test that types several characters only
     * exercises the first one — on the drafted path *and* the staged one, which
     * reads the params rather than a draft.
     */
    const Wrapper = ({ children }: { children: ReactNode }) => {
        const [revision, setRevision] = useState(0);
        const base = session ?? idleToolSession;
        const [params, setParams] = useState(base.params);

        const live = useMemo(
            () => ({
                ...base,
                params,
                setParams: (next: Parameters<ToolSession["setParams"]>[0]) => {
                    base.setParams(next);
                    setParams(next);
                },
                invalidate: (...args: Parameters<ToolSession["invalidate"]>) => {
                    base.invalidate(...args);
                    setRevision((previous) => previous + 1);
                },
            }),
            [base, params],
        );

        return (
            <ChakraProvider value={defaultSystem}>
                <ColorModeProvider>
                    <DisplayPreferencesProvider value={{ ...displayPreferences, ...preferences }}>
                        <ToolSessionScope session={live} revision={revision}>
                            {children}
                        </ToolSessionScope>
                    </DisplayPreferencesProvider>
                </ColorModeProvider>
            </ChakraProvider>
        );
    };

    return rtlRender(ui, { wrapper: Wrapper, ...options });
}

export { screen, within, waitFor, act } from "@testing-library/react";
export { default as userEvent } from "@testing-library/user-event";

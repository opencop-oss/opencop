// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    render as rtlRender,
    renderHook as rtlRenderHook,
    type RenderHookOptions,
    type RenderOptions,
} from "@testing-library/react";
import { useEffect, type ReactElement, type ReactNode } from "react";
import { QueryClient } from "@tanstack/react-query";
import { MemoryRouter, useLocation } from "react-router";
import { AUTH_QUERY_KEY, type AuthUser, type OpencopNotification, type Page } from "@opencop/api";
import { OcThemeProvider as ThemeProvider } from "@opencop/ui";
// Side-effect import: initialises the global i18next instance (initReactI18next),
// so `useTranslation()` resolves real translations without an <I18nextProvider>.
import "~/i18n/config";
import { HostProviders } from "~/HostProviders";
import { notificationsPageQueryKey } from "~/features/notifications/notifications.queries";
import { createTestUser } from "./fixtures/user";

export interface AppRenderOptions extends Omit<RenderOptions, "wrapper"> {
    /**
     * Authenticated user seeded into the auth query cache. Defaults to
     * {@link createTestUser}. Pass `null` for an unauthenticated render — the
     * component then gates behind AuthProvider's loading/redirect flow.
     */
    readonly user?: AuthUser | null;
    readonly queryClient?: QueryClient;
    /** MemoryRouter initial entry. Defaults to `/`. */
    readonly initialRoute?: string;
    /** Observe MemoryRouter path/query changes from outside providers that may replace children. */
    readonly onLocationChange?: (location: ObservedLocation) => void;
    /** Pre-seeded cache entries, keyed by JSON-stringified queryKey. */
    readonly queryData?: Readonly<Record<string, unknown>>;
}

export type ObservedLocation = {
    pathname: string;
    search: string;
};

function createTestQueryClient(): QueryClient {
    return new QueryClient({
        defaultOptions: {
            // `staleTime: Infinity` keeps `queryData`-seeded entries fresh so no
            // background refetch fires on mount (which the no-network guarantee would
            // then fail unless the test also registered a handler). Production sets
            // `60_000`; Infinity is the standard for a seed-based harness. Tests that
            // assert refetch behaviour pass their own client.
            queries: { retry: false, staleTime: Infinity },
            mutations: { retry: false },
        },
    });
}

const EMPTY_NOTIFICATIONS_PAGE: Page<OpencopNotification> = {
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
};

/** Every source is enabled on register; the app's visibility policy is not under test here. */
const ENABLE_EVERY_SOURCE = { shouldEnableOnRegister: () => true };

interface HarnessConfig {
    queryClient: QueryClient;
    user: AuthUser | null;
    initialRoute: string;
    onLocationChange?: (location: ObservedLocation) => void;
}

/** Splits harness-only options from the leftover RTL options and wires state. */
function buildConfig(options: AppRenderOptions): { config: HarnessConfig; rest: RenderOptions } {
    const {
        user = createTestUser(),
        queryClient = createTestQueryClient(),
        initialRoute = "/",
        onLocationChange,
        queryData,
        ...rest
    } = options;

    // Authenticated by default: seed the auth cache so AuthProvider's query — which
    // has a 5-minute staleTime — treats it as fresh and fires no `/me` fetch.
    if (user) queryClient.setQueryData(AUTH_QUERY_KEY, user);
    // The drop-notification provider fetches this page on mount; seeding it keeps that out of
    // every test. A caller's own client, or `queryData`, may already hold one.
    if (queryClient.getQueryData(notificationsPageQueryKey()) === undefined)
        queryClient.setQueryData(notificationsPageQueryKey(), EMPTY_NOTIFICATIONS_PAGE);

    if (queryData) {
        for (const [key, data] of Object.entries(queryData)) {
            queryClient.setQueryData(JSON.parse(key), data);
        }
    }

    return { config: { queryClient, user, initialRoute, onLocationChange }, rest };
}

function LocationObserver({ onLocationChange }: Pick<HarnessConfig, "onLocationChange">) {
    const location = useLocation();

    useEffect(() => {
        onLocationChange?.({ pathname: location.pathname, search: location.search });
    }, [location.pathname, location.search, onLocationChange]);

    return null;
}

function makeWrapper(config: HarnessConfig) {
    return function Providers({ children }: { children: ReactNode }) {
        return (
            <ThemeProvider>
                <MemoryRouter initialEntries={[config.initialRoute]}>
                    <LocationObserver onLocationChange={config.onLocationChange} />
                    <HostProviders
                        queryClient={config.queryClient}
                        ingestPolicy={ENABLE_EVERY_SOURCE}
                    >
                        {children}
                    </HostProviders>
                </MemoryRouter>
            </ThemeProvider>
        );
    };
}

/**
 * Renders `ui` inside the app's own provider tree ({@link HostProviders}) plus the
 * theme, i18n and a memory router. The app's start-up effects (source registration,
 * native and plugin installation) are not mounted. Network is mocked at the HTTP boundary by
 * the shared MSW server. Returns the constructed instances so the test can drive them.
 *
 * Components that need map context mock `~/features/map/MapManagerContext`
 * directly (a narrow internal-boundary mock the CI guard permits) — the harness
 * does not mount `MapManagerProvider` (it drags in react-map-gl/WebGL).
 */
function customRender(ui: ReactElement, options: AppRenderOptions = {}) {
    const { config, rest } = buildConfig(options);

    return {
        // `rest` forwards legit RTL options (container, baseElement, …).
        ...rtlRender(ui, { wrapper: makeWrapper(config), ...rest }),
        queryClient: config.queryClient,
        user: config.user,
    };
}

/** Hook counterpart of {@link customRender}; mounts the same provider chain. */
function customRenderHook<Result, Props>(
    callback: (props: Props) => Result,
    options: AppRenderOptions & Omit<RenderHookOptions<Props>, "wrapper"> = {},
) {
    const { config } = buildConfig(options);

    return {
        ...rtlRenderHook(callback, {
            wrapper: makeWrapper(config),
            initialProps: options.initialProps,
        }),
        queryClient: config.queryClient,
        user: config.user,
    };
}

// One import surface: re-export the rest of RTL, then shadow `render`/`renderHook`.
export * from "@testing-library/react";
export { customRender as render };
export { customRenderHook as renderHook };
export { default as userEvent } from "@testing-library/user-event";
export { createTestUser } from "./fixtures/user";
export { createTestPreferences } from "./fixtures/preferences";
export { server, http, HttpResponse } from "./server";
export { API_BASE } from "./handlers";

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createBrowserRouter, Navigate, Outlet } from "react-router";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./queryClient";
import { HostProviders } from "./HostProviders";
import { ErrorBoundaryWrapper } from "./features/errors/ErrorBoundaryWrapper";
import { DropNotification } from "./features/notifications/DropNotification";
import { ProtectedRoute } from "./features/auth/ProtectedRoute";
import { BaseLayout } from "./layouts/BaseLayout";
import { MapView } from "./views/MapView";
import { AdminHubCards } from "./views/admin/AdminHubView";
import { DataSourcesView } from "./views/DataSourcesView";
import { LayersView } from "./views/LayersView";
import { ThemeView } from "./views/ThemeView";
import { AdminHubView } from "./views/admin/AdminHubView";
import { AdminUsersView } from "./views/admin/AdminUsersView";
import { AdminLayersView } from "./views/admin/AdminLayersView";
import { AdminDataSourcesView } from "./views/admin/AdminDataSourcesView";
import { AdminMappersView } from "./views/admin/AdminMappersView";
import { AdminImageryView } from "./views/admin/AdminImageryView";
import { AdminBannersView } from "./views/admin/AdminBannersView";
import { AdminRuntimeFeaturesView } from "./views/admin/AdminRuntimeFeaturesView";
import { AdminPluginsView } from "./views/admin/AdminPluginsView";
import { FeatureFlaggedRoute } from "./features/admin/runtime-features/FeatureFlaggedRoute";
import { AccessDenied } from "./views/auth/AccessDenied";
import { LoggedOut } from "./views/auth/LoggedOut";
import { LoginFailed } from "./views/auth/LoginFailed";
import { NotFound } from "./views/auth/NotFound";
import { MapperBuilderPage } from "./features/mapper-builder/MapperBuilderPage";
import {
    LEGACY_MAPPER_BUILDER_PATH,
    LEGACY_MAPPERS_PAGE_PATH,
    LegacyMapperBuilderRedirect,
    LegacyMappersPageRedirect,
} from "./features/mapper-builder/LegacyMapperBuilderRedirect";
import { MAPPER_BUILDER_PATH } from "./features/data-sources/data-resources.util";
import { UserPreferencesView } from "./views/UserPreferencesView";
import { tileQueries } from "./features/imagery/imagery.queries";
import { shouldEnableSource } from "./features/filters/panel-exclusion.util";
import { RegisterMySources } from "./features/websocket/RegisterMySources";
import { PluginLoader } from "./features/plugin-manager/PluginLoader";
import { NativeSubjectInstaller } from "./features/contributions/NativeSubjectInstaller";
import { StreamPipManager } from "./features/events/StreamPipManager";

// Providers for authenticated routes (includes WebSocket, Map, etc.)
function AppProviders() {
    return (
        <HostProviders
            queryClient={queryClient}
            ingestPolicy={{ shouldEnableOnRegister: shouldEnableSource }}
            effects={
                <>
                    <RegisterMySources />
                    <NativeSubjectInstaller />
                    <PluginLoader />
                </>
            }
        >
            <ErrorBoundaryWrapper componentName="DropNotificationConsumers">
                <ErrorBoundaryWrapper componentName="DropNotification">
                    <DropNotification />
                </ErrorBoundaryWrapper>
                <ErrorBoundaryWrapper componentName="StreamPipManager">
                    <StreamPipManager />
                </ErrorBoundaryWrapper>
                <Outlet />
            </ErrorBoundaryWrapper>
        </HostProviders>
    );
}

// Minimal providers for public routes (no auth required)
function PublicProviders() {
    return (
        <QueryClientProvider client={queryClient}>
            <Outlet />
        </QueryClientProvider>
    );
}

export const router = createBrowserRouter([
    // Public routes (no auth required)
    {
        element: <PublicProviders />,
        HydrateFallback: null,
        children: [
            {
                path: "/login_success",
                element: <Navigate to="/map" replace />,
            },
            {
                path: "/logged-out",
                element: <LoggedOut />,
            },
            {
                path: "/login_failed",
                element: <LoginFailed />,
            },
            {
                path: "/access_denied",
                element: <AccessDenied />,
            },
            {
                path: "*",
                element: <NotFound />,
            },
        ],
    },
    // Protected routes (require auth)
    {
        element: <AppProviders />,
        HydrateFallback: null,
        children: [
            {
                element: <BaseLayout />,
                // eslint-disable-next-line i18next/no-literal-string -- last-resort fallback, providers may not be available
                errorElement: <div>Error</div>,
                children: [
                    {
                        path: "/",
                        element: <Navigate to="/map" replace />,
                    },
                    {
                        path: "/map",
                        loader: () => queryClient.ensureQueryData(tileQueries.list()),
                        element: (
                            <ProtectedRoute>
                                <MapView />
                            </ProtectedRoute>
                        ),
                    },
                    {
                        path: "/layers",
                        element: (
                            <ProtectedRoute>
                                <LayersView />
                            </ProtectedRoute>
                        ),
                    },
                    {
                        path: "/data-sources",
                        element: (
                            <ProtectedRoute>
                                <DataSourcesView />
                            </ProtectedRoute>
                        ),
                    },
                    {
                        path: "/user/preferences",
                        element: (
                            <ProtectedRoute>
                                <UserPreferencesView />
                            </ProtectedRoute>
                        ),
                    },
                    {
                        path: `${MAPPER_BUILDER_PATH}/new`,
                        element: (
                            <ProtectedRoute roles={["ROLE_admin"]}>
                                <MapperBuilderPage />
                            </ProtectedRoute>
                        ),
                    },
                    {
                        path: `${MAPPER_BUILDER_PATH}/:id/edit`,
                        element: (
                            <ProtectedRoute roles={["ROLE_admin"]}>
                                <MapperBuilderPage />
                            </ProtectedRoute>
                        ),
                    },
                    {
                        path: `${LEGACY_MAPPER_BUILDER_PATH}/new`,
                        element: <LegacyMapperBuilderRedirect />,
                    },
                    {
                        path: `${LEGACY_MAPPER_BUILDER_PATH}/:id/edit`,
                        element: <LegacyMapperBuilderRedirect />,
                    },
                    {
                        path: `${LEGACY_MAPPERS_PAGE_PATH}/mappers/new`,
                        element: <LegacyMapperBuilderRedirect />,
                    },
                    {
                        path: `${LEGACY_MAPPERS_PAGE_PATH}/mappers/:id/edit`,
                        element: <LegacyMapperBuilderRedirect />,
                    },
                    {
                        path: `${LEGACY_MAPPERS_PAGE_PATH}/*`,
                        element: <LegacyMappersPageRedirect />,
                    },
                    {
                        path: "/admin",
                        element: (
                            <ProtectedRoute roles={["ROLE_admin"]}>
                                <AdminHubView />
                            </ProtectedRoute>
                        ),
                        children: [
                            { index: true, element: <AdminHubCards /> },
                            {
                                path: "users",
                                element: <AdminUsersView />,
                                handle: { subtitleKey: "adminPanel.subtitleUsers" },
                            },
                            {
                                path: "layers",
                                element: <AdminLayersView />,
                                handle: { subtitleKey: "adminPanel.subtitleLayers" },
                            },
                            {
                                path: "data-sources",
                                element: <AdminDataSourcesView />,
                                handle: {
                                    subtitleKey: "adminPanel.subtitleDataSources",
                                },
                            },
                            {
                                path: "mappers",
                                element: <AdminMappersView />,
                                handle: { subtitleKey: "adminPanel.subtitleMappers" },
                            },
                            {
                                path: "imagery",
                                element: <AdminImageryView />,
                                handle: { subtitleKey: "adminPanel.subtitleImagery" },
                            },
                            {
                                path: "banners",
                                element: <AdminBannersView />,
                                handle: { subtitleKey: "adminPanel.subtitleBanners" },
                            },
                            {
                                // Role-gated only. This page is the only surface that can set a
                                // runtime feature's audience — there is no delete endpoint and no
                                // Swagger in production — so flag-gating it would let an admin set
                                // that flag to `NONE` and lose the repair surface permanently.
                                // Pinned by router.test.ts and AdminRuntimeFeaturesView.test.tsx.
                                path: "runtime-features",
                                element: <AdminRuntimeFeaturesView />,
                                handle: { subtitleKey: "adminPanel.subtitleRuntimeFeatures" },
                            },
                            {
                                // The panel this replaced. Without the redirect a bookmark falls
                                // through to the catch-all, which sits under `PublicProviders` and
                                // renders a bare NotFound with no app shell — that reads as a
                                // session error rather than a moved page.
                                path: "feature-flags",
                                element: <Navigate to="/admin/runtime-features" replace />,
                            },
                            {
                                path: "plugins",
                                element: <AdminPluginsView />,
                                handle: { subtitleKey: "adminPanel.subtitlePlugins" },
                            },
                        ],
                    },
                    {
                        path: "/themes",
                        element: (
                            <ProtectedRoute roles={["ROLE_admin"]}>
                                <FeatureFlaggedRoute flag="themes-page">
                                    <ThemeView />
                                </FeatureFlaggedRoute>
                            </ProtectedRoute>
                        ),
                    },
                ],
            },
        ],
    },
]);

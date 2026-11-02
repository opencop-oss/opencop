// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, VStack } from "@chakra-ui/react";
import Logger from "js-logger";
import { log } from "@deck.gl/core";
import "maplibre-gl/dist/maplibre-gl.css";
import MapLibre from "react-map-gl/maplibre";
import { useQuery } from "@tanstack/react-query";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { MapErrorFallback } from "~/features/errors/ErrorFallbacks";
import { TripProvider } from "~/features/events/sections/TripContext";
import { TripOverlay } from "~/features/events/sections/TripOverlay";
import { EventClipboard } from "~/features/events/EventClipboard";
import { useInitialMapStyle } from "~/features/imagery/use-initial-map-style.hook";
import { ActiveBasemapProvider } from "~/features/imagery/ActiveBasemapContext";
import { BasemapPicker } from "~/features/imagery/BasemapPicker";
import { DeckLayerProvider } from "~/features/map/DeckLayerContext";
import { MapManagerProvider } from "~/features/map/MapManagerContext";
import { StackedElementsEffect } from "~/features/map/StackedElementsEffect";
import { SelectionBoundaryLayer } from "~/features/selection/SelectionBoundaryLayer";
import { EditHandlesLayer } from "~/features/map/EditHandlesLayer";
import { MapStatusBar } from "~/features/map/MapStatusBar";
import { WeatherLayer } from "~/features/map/WeatherLayer";
import { ImageryOverlayLayer } from "~/features/map/ImageryOverlayLayer";
import { InfoPanel } from "~/features/mapbar/InfoPanel";
import { MapFilterActivePopup } from "~/features/map-filter/MapFilterActivePopup";
import { PendingCaptureHandler } from "~/features/mapcapture/PendingCaptureHandler";
import { PendingNotificationHandler } from "~/features/notifications/PendingNotificationHandler";
import { TerrainRestorer } from "~/features/terrain/TerrainRestorer";
import { MapToolbar } from "~/features/toolbar/MapToolbar";
import { DrawHintOutlet } from "~/features/toolbar/DrawHintOutlet";
import { ToolEditorOutlet } from "~/features/toolbar/ToolEditorOutlet";
import { CoordFormatProvider } from "~/features/coordinates/CoordFormatContext";
import { HostDisplayPreferences } from "~/features/preferences/HostDisplayPreferences";
import { MapCrosshair } from "~/features/coordinates/MapCrosshair";
import { HistoricalDataProvider } from "~/features/historical/data-plane/HistoricalDataContext";
import { HistoricalPanel } from "~/features/historical/controls/HistoricalPanel";
import { MapLayout, SidebarAwarePositioner } from "~/layouts/MapLayout";
import { preferencesQueries } from "~/features/preferences/preferences.queries";
import { MapContextMenu } from "~/features/map/MapContextMenu";
import { RenderMetrics } from "~/features/map/RenderMetrics";
import { StackedElements } from "~/features/events/StackedElements";
import { useIsStackedObjectSelectionOpen } from "~/features/map/stacked-object-selection.store";
import { useMapStore } from "~/features/map/core/map.store";
import { useViewActions } from "~/features/map/core/slices/view/view.hook";
import { useResolvedInitialView } from "~/features/map/core/use-resolved-initial-view.hook";
import { MapStoreSync } from "~/features/map/MapStoreSync";
import { SelectionRestorer } from "~/features/map/SelectionRestorer";
import { useEffect } from "react";
import { RegisterWatchboxAlerts } from "~/features/websocket/RegisterWatchboxAlerts";
import { SceneView } from "~/features/map/scene/SceneView";
import { SceneLayer } from "~/features/map/scene/SceneLayer";
import { WatchboxSceneLayer } from "~/features/watchbox/WatchboxSceneLayer";
import { TerrainLayer } from "~/features/map/TerrainLayer";
import { usePluginLoadSettled } from "~/features/plugin-manager/use-plugin-load-settled.hook";

log.level = 0;

const logger = Logger.get("map");

declare global {
    interface Window {
        __stats: boolean;
    }
}

function MapViewInner() {
    const { style: initialStyle, tileId: initialTileId } = useInitialMapStyle();
    const { data: preferences } = useQuery(preferencesQueries.current());
    const { setViewState } = useViewActions();
    const stackedOpen = useIsStackedObjectSelectionOpen();
    const initialView = useResolvedInitialView(!!preferences, preferences?.initialView?.mapView);
    const pluginsSettled = usePluginLoadSettled();

    // Get zoom behavior preference - convert backend enum to MapLibre format
    // Backend: "CENTER" | "MOUSE" → MapLibre: scrollZoom object with around:"center" or true
    // Default to "CENTER" when preferences haven't loaded yet
    const scrollZoomConfig =
        (preferences?.zoomBehavior ?? "CENTER") === "CENTER" ? { around: "center" as const } : true;

    // Seed the store when initialView resolves (or refreshes, on rare preferences
    // refetches). When the URL already matches the resolved view, the persist
    // subscription's skip-identical guard prevents a redundant `pushState`. This
    // is what repopulates the URL on bare-`/map` SPA-nav return.
    useEffect(() => {
        if (initialView) useMapStore.setState(initialView);
    }, [initialView]);

    // Defer rendering until the initial style is resolved to avoid flashing the
    // local fallback background before the user's preferred tile loads, and until
    // the plugins have loaded, so the first frame already draws the element types
    // a plugin's mappers bring.
    if (!initialStyle || !preferences || !pluginsSettled) return <MapLayout>{null}</MapLayout>;

    return (
        <MapLayout>
            <MapLibre
                initialViewState={initialView}
                onLoad={(e) => {
                    const [[west, south], [east, north]] = e.target.getBounds().toArray();
                    setViewState({ bounds: [west, south, east, north] });
                }}
                onMove={(e) => {
                    const [[west, south], [east, north]] = e.target.getBounds().toArray();
                    setViewState({ ...e.viewState, bounds: [west, south, east, north] });
                }}
                style={{ width: "100%", height: "100%", position: "absolute" }}
                mapStyle={initialStyle}
                attributionControl={false}
                scrollZoom={scrollZoomConfig}
                onError={(e) => logger.error("[MapLibre] Map error:", e.error)}
            >
                <ActiveBasemapProvider resolvedTileId={initialTileId}>
                    <MapManagerProvider>
                        <MapStoreSync />
                        <TripProvider>
                            <HistoricalDataProvider>
                                <DeckLayerProvider>
                                    <TerrainLayer />
                                    <ImageryOverlayLayer />
                                    <WeatherLayer />
                                    <SceneView>
                                        <SceneLayer />
                                        <WatchboxSceneLayer />
                                    </SceneView>
                                    <SelectionBoundaryLayer />
                                    <EditHandlesLayer />
                                    <EventClipboard />
                                    <TripOverlay />
                                    <MapCrosshair />
                                </DeckLayerProvider>
                                <SelectionRestorer />
                                <TerrainRestorer />
                                <StackedElementsEffect />
                                <CoordFormatProvider>
                                    <HostDisplayPreferences>
                                        <MapContextMenu />
                                        {stackedOpen && <StackedElements />}
                                        <SidebarAwarePositioner>
                                            <>
                                                <RegisterWatchboxAlerts />
                                                <PendingNotificationHandler />
                                                <PendingCaptureHandler />
                                                <MapToolbar />
                                                <Flex
                                                    flex="1"
                                                    flexDir="row"
                                                    justify="end"
                                                    p="2"
                                                    minH="0"
                                                    pointerEvents="none"
                                                >
                                                    <Flex
                                                        flex="1"
                                                        minH="0"
                                                        position="relative"
                                                        pointerEvents="none"
                                                    >
                                                        <ToolEditorOutlet />
                                                        {/* Map-wide status the operator reads, not controls:
                                                            top center of the map left of any open side panel,
                                                            so the panel never covers it. The pill and the hints
                                                            stack, so both can show at once. */}
                                                        <VStack
                                                            position="absolute"
                                                            top="0"
                                                            left="50%"
                                                            transform="translateX(-50%)"
                                                            gap="2"
                                                            pointerEvents="none"
                                                        >
                                                            <Box
                                                                pointerEvents="auto"
                                                                _empty={{ display: "none" }}
                                                            >
                                                                <MapFilterActivePopup />
                                                            </Box>
                                                            <DrawHintOutlet />
                                                        </VStack>
                                                    </Flex>
                                                    <InfoPanel />
                                                    <RenderMetrics placement="bottom" />
                                                </Flex>
                                            </>
                                            <Box position="relative" pointerEvents="none">
                                                <Box
                                                    position="absolute"
                                                    bottom="100%"
                                                    left="0"
                                                    w="full"
                                                    pointerEvents="none"
                                                >
                                                    <ErrorBoundaryWrapper componentName="BasemapPicker">
                                                        <BasemapPicker />
                                                    </ErrorBoundaryWrapper>
                                                </Box>
                                                <HistoricalPanel />
                                            </Box>
                                            <MapStatusBar />
                                        </SidebarAwarePositioner>
                                    </HostDisplayPreferences>
                                </CoordFormatProvider>
                            </HistoricalDataProvider>
                        </TripProvider>
                    </MapManagerProvider>
                </ActiveBasemapProvider>
            </MapLibre>
        </MapLayout>
    );
}

export function MapView() {
    return (
        <ErrorBoundaryWrapper componentName="MapView" fallback={MapErrorFallback}>
            <MapViewInner />
        </ErrorBoundaryWrapper>
    );
}

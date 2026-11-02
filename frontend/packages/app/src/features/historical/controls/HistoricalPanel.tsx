// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Text } from "@chakra-ui/react";
import { Histogram, Panel, ResizeHandle } from "@opencop/ui";
import { debounce } from "lodash";
import { useCallback, useEffect, useMemo, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { HistogramLegend } from "../histogram/HistogramLegend";
import { useHistogramModel } from "../histogram/use-histogram-model.hook";
import { HistoricalControls } from "./HistoricalControls";
import { MAX_PANEL_HEIGHT, MIN_PANEL_HEIGHT } from "../data-plane/historical.store";
import { HistoricalStatusBar, STATUS_BAR_HEIGHT } from "./HistoricalStatusBar";
import {
    useHistoricalActions,
    useHistoricalMode,
    useHistoricalState,
    useIsActivated,
    usePanelHeight,
    usePlayheadPosition,
    useSettingsOpen,
    useShowActiveEvents,
    useShowActiveEventUpdates,
} from "../data-plane/historical.store";
import { HistogramSettings } from "../histogram/HistogramSettings";
import { useHistoricalStatusModel } from "./use-historical-status-model.hook";
import { useHistoricalUrlSync } from "./use-historical-url-sync.hook";
import { useRangeTimestamps } from "./use-range-timestamps.hook";

// Brush is mouseup-driven, so each gesture fires once. Debounce collapses
// rapid sequences of separate gestures into a single submit, avoiding wasted
// REST round-trips when the user is exploring.
const BRUSH_DEBOUNCE_MS = 150;

export function HistoricalPanel(): ReactElement | null {
    useHistoricalUrlSync();

    const mode = useHistoricalMode();

    if (mode !== "historical") return null;

    return <HistoricalPanelContent />;
}

function HistoricalPanelContent(): ReactElement {
    const { t } = useTranslation();
    const { sourceIds, from, to, intervalMode } = useHistoricalState();
    const activated = useIsActivated();
    const panelHeight = usePanelHeight();
    const playheadPosition = usePlayheadPosition();
    const { pushZoom, setPlayheadPosition, setPanelHeight } = useHistoricalActions();
    const timezone = useUserTimezone();
    const showActiveEvents = useShowActiveEvents();
    const showActiveEventUpdates = useShowActiveEventUpdates();
    const settingsOpen = useSettingsOpen();
    const status = useHistoricalStatusModel();
    const { startLabel: rangeStartLabel, endLabel: rangeEndLabel } = useRangeTimestamps(
        from,
        to,
        timezone,
    );

    // All multi-source histogram derivation (colors, visible series, buckets,
    // effective range, query state) lives in the model hook so this component
    // stays composition.
    const {
        sourceColors,
        histogramBuckets,
        sourceSeries,
        visibleSourceColors,
        visibleChunks,
        effectiveRange,
        isLoading,
        allFailed,
    } = useHistogramModel({ sourceIds, from, to, intervalMode, activated });

    // `pushZoom` is referentially stable (zustand actions slice), so the
    // debounced wrapper is constructed once. Pending calls cancel on unmount
    // and on mode exit (the panel unmounts when leaving historical mode).
    const debouncedBrush = useMemo(() => debounce(pushZoom, BRUSH_DEBOUNCE_MS), [pushZoom]);
    useEffect(() => () => debouncedBrush.cancel(), [debouncedBrush]);
    const handleBrush = useCallback(
        (brushFrom: string, brushTo: string) => {
            debouncedBrush(brushFrom, brushTo);
        },
        [debouncedBrush],
    );

    const handleResize = useCallback(
        (delta: number) => {
            setPanelHeight(panelHeight + delta);
        },
        [panelHeight, setPanelHeight],
    );

    return (
        <Box w="full" overflow="hidden" pointerEvents="auto">
            <ResizeHandle
                onResize={handleResize}
                label={t("historical.resizeHandle")}
                valueNow={panelHeight}
                valueMin={MIN_PANEL_HEIGHT}
                valueMax={MAX_PANEL_HEIGHT}
                invertDelta
                size="md"
            />
            <Panel
                w="full"
                borderRadius="0"
                flexDir="column"
                gap="0"
                bg={settingsOpen ? "bg.subtle" : undefined}
            >
                <HistoricalControls />
                {settingsOpen ? (
                    // Reserve the status-bar row's height in settings mode so the
                    // panel stays the same height as the histogram view when the
                    // user toggles settings (no jump).
                    <Box mx="3" mb="1" h={STATUS_BAR_HEIGHT} />
                ) : (
                    <HistoricalStatusBar
                        tier={status.tier}
                        activated={status.activated}
                        zoomDepth={status.zoomDepth}
                        playheadSet={status.playheadSet}
                        rangeStartLabel={rangeStartLabel}
                        rangeEndLabel={rangeEndLabel}
                        onRetry={status.onRetry}
                        onDismiss={status.onDismiss}
                        onPopZoom={status.onPopZoom}
                        onResetZoom={status.onResetZoom}
                    />
                )}
                <Box px="3" pb="2">
                    <ErrorBoundaryWrapper componentName="Histogram">
                        {settingsOpen ? (
                            <HistogramSettings minHeight={panelHeight} />
                        ) : allFailed ? (
                            // Only when *every* source's chart failed. A single
                            // failed source used to blank the chart for all of
                            // them; now the healthy ones still render and the
                            // status bar reports the ones that didn't.
                            <Box
                                h={`${panelHeight}px`}
                                display="flex"
                                alignItems="center"
                                justifyContent="center"
                            >
                                <Text fontSize="xs" color="red.fg">
                                    {t("historical.loadError")}
                                </Text>
                            </Box>
                        ) : (
                            <Histogram
                                buckets={activated ? histogramBuckets : []}
                                height={panelHeight}
                                isLoading={activated && isLoading && sourceIds.length > 0}
                                emptyText={
                                    activated ? undefined : t("historical.statusHintActivate")
                                }
                                timezone={timezone}
                                onBrush={activated ? handleBrush : undefined}
                                playheadPosition={playheadPosition}
                                onPlayheadChange={activated ? setPlayheadPosition : undefined}
                                // The chunk-overlay bars, brush, and playhead map
                                // time→pixel over this domain. Use the planner's
                                // effective range so they align with the density
                                // bars (which tile that same range); fall back to
                                // the requested window until a response arrives.
                                rangeStart={effectiveRange?.start ?? from ?? undefined}
                                rangeEnd={effectiveRange?.end ?? to ?? undefined}
                                chunkRanges={activated ? visibleChunks : undefined}
                                sourceColors={activated ? visibleSourceColors : undefined}
                                sourceSeries={activated ? sourceSeries : undefined}
                                showExpectedActiveEvents
                                showActiveEvents={showActiveEvents}
                                showActiveEventUpdates={showActiveEventUpdates}
                            />
                        )}
                        {activated && sourceIds.length > 1 && (
                            // Keep the legend mounted in settings mode but hidden
                            // (visibility:hidden reserves its exact height) so
                            // toggling Settings doesn't shrink the panel. Hidden
                            // from view, focus order, and assistive tech.
                            <Box
                                visibility={settingsOpen ? "hidden" : "visible"}
                                aria-hidden={settingsOpen || undefined}
                            >
                                <HistogramLegend
                                    sourceIds={sourceIds}
                                    sourceColors={sourceColors}
                                />
                            </Box>
                        )}
                    </ErrorBoundaryWrapper>
                </Box>
            </Panel>
        </Box>
    );
}

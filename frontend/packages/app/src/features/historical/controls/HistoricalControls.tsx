// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Box, Button, Flex, HStack, Select, Text } from "@chakra-ui/react";
import { OcInputLabel, OcSelect, Tooltip, type SelectItem } from "@opencop/ui";
import { useQuery } from "@tanstack/react-query";
import { useCallback, useMemo, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaCog, FaTimes } from "react-icons/fa";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerQueries } from "~/features/layers/layers.queries";
import { isOffered } from "~/features/filters/panel-exclusion.util";
import { usePanelExcludedIds } from "~/features/filters/use-panel-excluded-ids.hook";
import {
    MAX_HISTORICAL_RANGE_DAYS,
    isRangeOverLimit,
    useHistoricalActions,
    useHistoricalState,
    useIsActivated,
    useSettingsOpen,
    type HistoricalSubjectType,
} from "../data-plane/historical.store";
import { HistoricalRangePicker } from "./HistoricalRangePicker";

/** Source select item; `archived` drives the "Archived" badge in the dropdown. */
type SourceItem = SelectItem & { archived: boolean };

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function HistoricalControls(): ReactElement {
    const { t } = useTranslation();

    const { sourceIds, from, to } = useHistoricalState();
    const { setSourceIds, activate, toggleSettings } = useHistoricalActions();
    const activated = useIsActivated();
    const settingsOpen = useSettingsOpen();

    // Archived subjects stay selectable here — historical activity predates the
    // archive — so we list active + archived together and tag the archived ones
    // with an "Archived" badge (they remain fully selectable, not disabled).
    // `/me/sources` doesn't return `archivedAt` (`/me/layers` does), so archived-ness
    // is derived from which query the row came from (the same approach as the My
    // Data Sources / My Layers active/archived views), not a field.
    const { data: activeSources } = useQuery(dataSourceQueries.myList());
    const { data: archivedSources } = useQuery(dataSourceQueries.myList(true));
    const { data: activeLayers } = useQuery(layerQueries.myList());
    const { data: archivedLayers } = useQuery(layerQueries.myList(true));

    // Subjects the user removed from the map panel aren't offered, but one already picked stays
    // listed so its chip keeps a name.
    const excludedIds = usePanelExcludedIds();
    const offered = useCallback(
        (id: string) => isOffered(excludedIds, id, sourceIds),
        [excludedIds, sourceIds],
    );

    // `color: "fg.muted"` mutes the selected value in the trigger — OcSelect
    // applies `color` in the value-text display path. The dropdown row is muted
    // separately in renderItem below (which ignores `color`).
    const layerItems: SourceItem[] = useMemo(
        () => [
            // Active first, archived last.
            ...(activeLayers ?? [])
                .filter((l) => offered(l.layerId))
                .map((l) => ({
                    value: l.layerId,
                    label: l.layerName || l.layerId,
                    archived: false,
                })),
            ...(archivedLayers ?? [])
                .filter((l) => offered(l.layerId))
                .map((l) => ({
                    value: l.layerId,
                    label: l.layerName || l.layerId,
                    color: "fg.muted",
                    archived: true,
                })),
        ],
        [activeLayers, archivedLayers, offered],
    );

    const sourceItems: SourceItem[] = useMemo(
        () => [
            ...(activeSources ?? [])
                .filter((ds) => offered(ds.serverId))
                .map((ds) => ({
                    value: ds.serverId,
                    label: ds.serverName || ds.serverId,
                    archived: false,
                })),
            ...(archivedSources ?? [])
                .filter((ds) => offered(ds.serverId))
                .map((ds) => ({
                    value: ds.serverId,
                    label: ds.serverName || ds.serverId,
                    color: "fg.muted",
                    archived: true,
                })),
        ],
        [activeSources, archivedSources, offered],
    );

    // Resolve a picked id back to its subject kind so the submit sends the right
    // `subjectType`. Layer and data-source ids occupy distinct UUID spaces, so a
    // flat lookup is unambiguous.
    const subjectTypeById = useMemo(() => {
        const map = new Map<string, HistoricalSubjectType>();
        for (const item of layerItems) map.set(item.value, "LAYER");
        for (const item of sourceItems) map.set(item.value, "SOURCE");
        return map;
    }, [layerItems, sourceItems]);

    const sourceGroups = useMemo(
        () => [
            { label: t("historical.sourceGroupLayers", "Layers"), items: layerItems },
            { label: t("historical.sourceGroupSources", "Data Sources"), items: sourceItems },
        ],
        [t, layerItems, sourceItems],
    );

    // Soft validation: pickers stay unconstrained (any pair of dates is
    // selectable), but Activate is disabled when the span exceeds the backend
    // limit and the status bar surfaces a warning. Backend still enforces this
    // as defense in depth.
    const rangeExceedsLimit = isRangeOverLimit(from, to);
    const canActivate = sourceIds.length > 0 && !!from && !!to && !rangeExceedsLimit;

    // Reason surfaced via tooltip on the disabled Activate button. Priority
    // matches the order the user would see the controls fail.
    const activateDisabledReason =
        sourceIds.length === 0
            ? t("historical.activateDisabled.noSource", "Select a source first")
            : !from || !to
              ? t("historical.activateDisabled.noRange", "Select a date range")
              : rangeExceedsLimit
                ? t("historical.activateDisabled.rangeTooLarge", {
                      days: MAX_HISTORICAL_RANGE_DAYS,
                      defaultValue: "Range exceeds {{days}} days",
                  })
                : null;

    return (
        <Flex justifyContent="space-between" alignItems="center" gap="3" px="3" py="2">
            <Flex gap="3" alignItems="center" flexWrap="wrap" flex="1" minW="0">
                {settingsOpen && (
                    <Text fontSize="sm" fontWeight="semibold">
                        {t("historical.settings.title", "Histogram Settings")}
                    </Text>
                )}
                {!settingsOpen && (
                    <>
                        <HStack gap="1">
                            <OcInputLabel label={t("historical.source", "Source")} />
                            <OcSelect
                                multiple
                                groups={sourceGroups}
                                values={sourceIds}
                                // Each picked id carries its subject kind (data
                                // source vs layer) so the data plane submits the
                                // right `subjectType` per subject.
                                onValuesChange={(ids) =>
                                    setSourceIds(
                                        ids,
                                        Object.fromEntries(
                                            ids.map((id) => [
                                                id,
                                                subjectTypeById.get(id) ?? "SOURCE",
                                            ]),
                                        ),
                                    )
                                }
                                placeholder={t("historical.sourcePlaceholder", "Select sources")}
                                // Collapse the trigger so multiple selections don't
                                // widen it: one subject shows its name, several show
                                // "Multiple". The checked rows live in the dropdown.
                                renderValueText={(selected) =>
                                    selected.length === 0 ? null : selected.length === 1 ? (
                                        <Text truncate>{selected[0].label}</Text>
                                    ) : (
                                        <Text>{t("historical.multipleSources", "Multiple")}</Text>
                                    )
                                }
                                size="xs"
                                controlProps={{ minW: "9rem", maxW: "12rem" }}
                                // Dropdown sizes to its widest row (full name +
                                // "Archived" badge + checkmark), floored at the trigger
                                // width and capped so a pathological name can't run away.
                                contentProps={{
                                    width: "max-content",
                                    minW: "14rem",
                                    maxW: "28rem",
                                }}
                                renderItem={(item) => (
                                    <Flex align="center" gap="2" flex="1" minW="0">
                                        <Select.ItemText
                                            color={item.color}
                                            flex="1"
                                            minW="0"
                                            truncate
                                        >
                                            {item.label}
                                        </Select.ItemText>
                                        {item.archived && (
                                            <Badge
                                                size="sm"
                                                colorPalette="gray"
                                                variant="subtle"
                                                flexShrink="0"
                                            >
                                                {t("historical.sourceArchived", "Archived")}
                                            </Badge>
                                        )}
                                    </Flex>
                                )}
                            />
                        </HStack>

                        <HStack gap="1">
                            <OcInputLabel label={t("historical.timeRange", "Range")} />
                            <HistoricalRangePicker />
                        </HStack>

                        {!activated && (
                            <Tooltip
                                content={activateDisabledReason ?? ""}
                                disabled={!activateDisabledReason}
                                uppercase={false}
                            >
                                {/* Wrapping span receives the hover events the
                                disabled button blocks, so the tooltip still
                                surfaces the reason while the action stays
                                un-clickable. */}
                                <Box as="span" display="inline-block">
                                    <Button
                                        size="xs"
                                        variant="solid"
                                        colorPalette="brand"
                                        disabled={!canActivate}
                                        onClick={activate}
                                        aria-label={t("historical.activate", "Activate")}
                                    >
                                        {t("historical.activate", "Activate")}
                                    </Button>
                                </Box>
                            </Tooltip>
                        )}

                        {/* Zoom step-back / reset controls live in the status bar
                        next to the zoom-depth indicator (see
                        HistoricalStatusBar in HistoricalPanel.tsx). */}
                    </>
                )}
            </Flex>

            {/* Settings cog stays pinned to the toolbar's right edge regardless
                of how many rows the controls wrap onto — the previous Spacer-
                based layout dropped the cog onto its own row at narrower
                widths. */}
            <Button
                size="xs"
                variant="ghost"
                flexShrink="0"
                onClick={toggleSettings}
                aria-label={
                    settingsOpen
                        ? t("historical.settings.close", "Close settings")
                        : t("historical.settings.open", "Histogram settings")
                }
                aria-pressed={settingsOpen}
            >
                {settingsOpen ? <FaTimes /> : <FaCog />}
            </Button>
        </Flex>
    );
}

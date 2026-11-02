// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Float,
    Flex,
    HStack,
    Icon,
    IconButton,
    Spinner,
    Text,
    VStack,
} from "@chakra-ui/react";
import { useCallback, useMemo, useState, type MouseEvent } from "react";
import { FaEye, FaEyeSlash, FaGlobe, FaTriangleExclamation } from "react-icons/fa6";
import { ROW_CHEVRON_SIZE, ROW_ICON_BUTTON_SIZE, SourceActionsMenu } from "./SourceActionsMenu";
import { useTranslation } from "react-i18next";
import { useSpinDelay } from "spin-delay";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { TacticalText, Tooltip } from "@opencop/ui";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { SourceElementList } from "./SourceElementList";
import type { AccessScope } from "@opencop/api";
import { TbChevronDown, TbPinFilled, TbStack2, TbServer } from "react-icons/tb";
import { useFilterPanelActions } from "./filter-panel.actions";
import { useSourceCount, useSourceState } from "@opencop/ingest";
import { useFilterPanelStore } from "./filter-panel.store";
import { dataSourceMatchesSearch, isOffered } from "./panel-exclusion.util";
import { usePanelExcludedIds } from "./use-panel-excluded-ids.hook";

type SourceKind = "layer" | "dataSource";

interface SourceFilterProps {
    sourceId: string;
    sourceName: string;
    kind: SourceKind;
    accessScope?: AccessScope;
    /** Active panel search; a layer matched via a data source auto-expands to show the matches. */
    searchTerm?: string;
    /** Marks the copy listed in the pinned block so screen readers can tell it from the in-group copy. */
    inPinnedBlock?: boolean;
}

export function SourceFilter({
    sourceId,
    sourceName,
    kind,
    accessScope,
    searchTerm = "",
    inPinnedBlock = false,
}: SourceFilterProps) {
    const { t } = useTranslation();
    const visible = useFilterPanelStore((state) =>
        state.hiddenSources.every((id) => id !== sourceId),
    );
    const textVisible = useFilterPanelStore((state) => state.textVisibleSources.includes(sourceId));
    const setTextVisibleSources = useFilterPanelStore((state) => state.setTextVisibleSources);
    const pinned = useFilterPanelStore((state) => state.pinnedSources.includes(sourceId));
    const togglePinnedSource = useFilterPanelStore((state) => state.togglePinnedSource);
    const { hideSources, showSources } = useFilterPanelActions();
    const syncing = useSourceState(sourceId) === "syncing";
    const sourceCount = useSourceCount(sourceId);

    // Show spinner immediately (delay: 0) with a 500ms minimum hold to avoid flicker.
    // This deviates from our standard spin-delay config (delay: 500, minDuration: 200)
    // because the spinner sits directly next to the toggle button the user just clicked.
    const showSpinner = useSpinDelay(syncing, { delay: 0, minDuration: 500, ssr: false });
    const [isExpanded, setIsExpanded] = useState(false);
    const [nestedToggled, setNestedToggled] = useState<boolean | null>(null);
    const excludedSet = usePanelExcludedIds();

    // Nested rows come from the unfiltered layer lookup, so a source outside `myList` can't be
    // pinned: the pinned block is built from `myList` and the row would never list anywhere.
    const { data: myDataSources } = useQuery({
        ...dataSourceQueries.myList(),
        enabled: kind === "dataSource",
    });
    const canPin = kind === "layer" || (myDataSources ?? []).some((ds) => ds.serverId === sourceId);

    const { data: layerDataSources = [] } = useQuery({
        ...layerQueries.dataSources(sourceId),
        enabled: kind === "layer",
    });
    const nestedDataSources = useMemo(
        () => layerDataSources.filter((ds) => isOffered(excludedSet, ds.serverId)),
        [layerDataSources, excludedSet],
    );
    const hasNested = kind === "layer" && nestedDataSources.length > 0;

    const lowerSearch = searchTerm.toLowerCase();
    const matchingNested = lowerSearch
        ? nestedDataSources.filter((ds) => dataSourceMatchesSearch(ds, lowerSearch))
        : [];
    const layerNameMatches = lowerSearch !== "" && sourceName.toLowerCase().includes(lowerSearch);
    // A layer matched only through its data sources lists just the matching ones.
    const shownNested =
        matchingNested.length > 0 && !layerNameMatches ? matchingNested : nestedDataSources;
    const isNestedExpanded = nestedToggled ?? matchingNested.length > 0;

    // Warning icon: only for layers, show when any child data source is hidden
    const warn = useFilterPanelStore((state) =>
        layerDataSources.some((ds) => state.hiddenSources.some((id) => id === ds.serverId)),
    );
    const warningTooltip =
        kind === "layer" && warn ? t("filters.someDataSourcesHidden") : undefined;

    // i18n labels based on kind
    const hideLabel = t(kind === "layer" ? "filters.hideLayer" : "filters.hideDataSource");
    const showLabel = t(kind === "layer" ? "filters.showLayer" : "filters.showDataSource");
    const syncingLabel = t(kind === "layer" ? "filters.syncing" : "filters.syncingDataSource");

    const iconActive = visible && (isExpanded || (hasNested && isNestedExpanded));

    const onToggleText = useCallback(() => {
        setTextVisibleSources((prev) =>
            textVisible ? prev.filter((id) => id !== sourceId) : [...prev, sourceId],
        );
    }, [sourceId, textVisible, setTextVisibleSources]);

    const queryClient = useQueryClient();

    // Resolved at click time so a toggle before the render-time query lands, or after it fails,
    // still reaches the layer's data sources; a failed lookup toggles the layer alone.
    const onToggle = useCallback(
        async (event: MouseEvent) => {
            event.stopPropagation();
            const dataSources =
                kind === "layer"
                    ? await queryClient
                          .ensureQueryData(layerQueries.dataSources(sourceId))
                          .catch(() => [])
                    : [];
            const ids = [
                sourceId,
                ...dataSources.map((ds) => ds.serverId).filter((id) => isOffered(excludedSet, id)),
            ];
            if (visible) {
                hideSources(ids);
            } else {
                showSources(ids);
            }
        },
        [kind, visible, sourceId, queryClient, excludedSet, hideSources, showSources],
    );

    const onToggleNested = useCallback(
        (event: MouseEvent) => {
            event.stopPropagation();
            setNestedToggled(!isNestedExpanded);
        },
        [isNestedExpanded],
    );

    return (
        <VStack w="full" gap={1} align="stretch">
            <Box
                role="group"
                aria-label={sourceName}
                aria-description={inPinnedBlock ? t("filters.pinned") : undefined}
                w="full"
                borderWidth="1px"
                borderColor="border"
                borderRadius="sm"
                bg="bg.elevated"
                transition="background-color 0.2s ease-out"
            >
                <Flex
                    px={1.5}
                    py={0.5}
                    align="center"
                    justify="space-between"
                    userSelect="none"
                    cursor="default"
                >
                    <Flex w="full" align="start">
                        <HStack w="full" justify="space-between">
                            <VStack align="start" minW={0} flex={1}>
                                <HStack gap={1.5}>
                                    {hasNested && (
                                        <IconButton
                                            variant="plain"
                                            minW="auto"
                                            boxSize={`${ROW_CHEVRON_SIZE}px`}
                                            p="0"
                                            flexShrink={0}
                                            aria-label={t("filters.toggleLayerDataSourcesFor", {
                                                name: sourceName,
                                            })}
                                            aria-expanded={isNestedExpanded}
                                            onClick={onToggleNested}
                                        >
                                            <Icon
                                                color="slate.text.muted"
                                                boxSize={`${ROW_CHEVRON_SIZE}px`}
                                                rotate={isNestedExpanded ? "0deg" : "-90deg"}
                                                transition="transform 0.2s ease"
                                            >
                                                <TbChevronDown size={ROW_CHEVRON_SIZE} />
                                            </Icon>
                                        </IconButton>
                                    )}
                                    <Box position="relative" flexShrink={0} display="inline-flex">
                                        <Icon color={iconActive ? "active" : "slate.text.muted"}>
                                            {kind === "layer" ? (
                                                <TbStack2 size={16} />
                                            ) : (
                                                <TbServer size={16} />
                                            )}
                                        </Icon>
                                        {accessScope === "GLOBAL" && (
                                            <Float placement="top-end" offsetX="0" offsetY="2px">
                                                <Tooltip
                                                    content={t("adminLayers.scopeGlobal")}
                                                    uppercase={false}
                                                >
                                                    <Box
                                                        as="span"
                                                        color="type.success"
                                                        display="inline-flex"
                                                        cursor="default"
                                                    >
                                                        <FaGlobe size={9} />
                                                    </Box>
                                                </Tooltip>
                                            </Float>
                                        )}
                                    </Box>
                                    <TacticalText
                                        fontWeight="600"
                                        wordBreak="break-word"
                                        color="bg.contrast"
                                    >
                                        {sourceName}
                                    </TacticalText>
                                    {pinned && (
                                        <Box
                                            as="span"
                                            role="img"
                                            aria-label={t("filters.pinned")}
                                            color="fg.muted"
                                            flexShrink={0}
                                            display="inline-flex"
                                        >
                                            <TbPinFilled size={12} />
                                        </Box>
                                    )}
                                    {visible && (
                                        <Text fontSize="xs" color="fg.muted" flexShrink={0}>
                                            ({sourceCount})
                                        </Text>
                                    )}
                                    {warningTooltip && (
                                        <Tooltip content={warningTooltip} uppercase={false}>
                                            <Box
                                                as="span"
                                                tabIndex={0}
                                                role="img"
                                                aria-label={warningTooltip}
                                                color="fg.muted"
                                                flexShrink={0}
                                                ml={1}
                                                cursor="default"
                                                onClick={(e: MouseEvent) => e.stopPropagation()}
                                            >
                                                <FaTriangleExclamation size={12} />
                                            </Box>
                                        </Tooltip>
                                    )}
                                </HStack>
                            </VStack>
                            <Flex align="center" gap={0.5} flexShrink={0}>
                                {showSpinner && <Spinner size="xs" />}
                                <SourceActionsMenu
                                    sourceName={sourceName}
                                    textVisible={textVisible}
                                    objectListOpen={isExpanded}
                                    objectListDisabled={sourceCount === 0}
                                    pinned={pinned}
                                    onToggleText={onToggleText}
                                    onToggleObjectList={() => setIsExpanded(!isExpanded)}
                                    onTogglePin={
                                        canPin ? () => togglePinnedSource(sourceId) : undefined
                                    }
                                />
                                <Tooltip
                                    content={
                                        syncing ? syncingLabel : visible ? hideLabel : showLabel
                                    }
                                    uppercase={false}
                                >
                                    <IconButton
                                        aria-label={
                                            syncing ? syncingLabel : visible ? hideLabel : showLabel
                                        }
                                        size="xs"
                                        boxSize={ROW_ICON_BUTTON_SIZE}
                                        minW={ROW_ICON_BUTTON_SIZE}
                                        variant="ghost"
                                        color={syncing ? "fg.muted" : visible ? "fg" : "fg.muted"}
                                        onClick={syncing ? undefined : onToggle}
                                        disabled={syncing}
                                    >
                                        {visible ? <FaEye size={16} /> : <FaEyeSlash size={16} />}
                                    </IconButton>
                                </Tooltip>
                            </Flex>
                        </HStack>
                    </Flex>
                </Flex>

                {isExpanded && sourceCount > 0 && <SourceElementList sourceId={sourceId} />}
            </Box>

            {hasNested && isNestedExpanded && (
                <VStack pl={4} gap={1} align="stretch">
                    {shownNested.map((ds) => (
                        <SourceFilter
                            key={ds.serverId}
                            sourceId={ds.serverId}
                            sourceName={ds.serverName ?? ds.serverId}
                            kind="dataSource"
                        />
                    ))}
                </VStack>
            )}
        </VStack>
    );
}

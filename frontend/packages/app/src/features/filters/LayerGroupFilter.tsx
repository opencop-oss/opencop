// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Float, Flex, HStack, Icon, IconButton, Text, VStack } from "@chakra-ui/react";
import { useCallback, useMemo, useState, type MouseEvent } from "react";
import { FaEye, FaEyeSlash, FaGlobe } from "react-icons/fa6";
import { TbChevronDown, TbFolder, TbFolderOpen } from "react-icons/tb";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { uniq } from "lodash";
import { TacticalText, Tooltip } from "@opencop/ui";
import type { AccessScope } from "@opencop/api";
import { useFilterPanelActions } from "./filter-panel.actions";
import { useFilterPanelStore } from "./filter-panel.store";
import { SourceFilter } from "./SourceFilter";
import { ROW_CHEVRON_SIZE, ROW_ICON_BUTTON_SIZE } from "./SourceActionsMenu";
import { isOffered } from "./panel-exclusion.util";
import { usePanelExcludedIds } from "./use-panel-excluded-ids.hook";
import { layerQueries } from "~/features/layers/layers.queries";
import type { MyLayerGroup } from "~/features/layer-groups/layer-groups.queries";

interface LayerGroupFilterProps {
    group: MyLayerGroup;
    /** Layers rendered under the group, narrowed by the active search. */
    layers: { layerId: string; layerName: string; accessScope?: AccessScope }[];
    /** Every offered member layer; the group eye toggles all of them, whatever the search shows. Defaults to `layers`. */
    toggleLayerIds?: string[];
    defaultExpanded?: boolean;
    searchTerm?: string;
}

export function LayerGroupFilter({
    group,
    layers,
    toggleLayerIds,
    defaultExpanded = false,
    searchTerm,
}: LayerGroupFilterProps) {
    const { t } = useTranslation();
    // `defaultExpanded` tracks the live search; a manual toggle takes over from then on.
    const [toggled, setToggled] = useState<boolean | null>(null);
    const isExpanded = toggled ?? defaultExpanded;

    const layerIds = useMemo(() => layers.map((l) => l.layerId), [layers]);
    const memberLayerIds = toggleLayerIds ?? layerIds;

    const isGroupHidden = useFilterPanelStore((state) =>
        state.hiddenLayerGroups.includes(group.id),
    );
    const visible = !isGroupHidden;

    const { hideLayerGroup, showLayerGroup } = useFilterPanelActions();

    const queryClient = useQueryClient();
    const excludedSet = usePanelExcludedIds();

    // Data sources are fetched per layer, so resolve them at click time rather than eagerly for
    // every member layer; layers whose lookup fails still toggle.
    const onToggleVisibility = useCallback(
        async (event: MouseEvent) => {
            event.stopPropagation();
            const results = await Promise.allSettled(
                memberLayerIds.map((id) =>
                    queryClient.ensureQueryData(layerQueries.dataSources(id)),
                ),
            );
            const dataSourceIds = results.flatMap((r) =>
                r.status === "fulfilled"
                    ? r.value.map((ds) => ds.serverId).filter((id) => isOffered(excludedSet, id))
                    : [],
            );
            const ids = uniq([...memberLayerIds, ...dataSourceIds]);
            if (visible) {
                hideLayerGroup(group.id, ids);
            } else {
                showLayerGroup(group.id, ids);
            }
        },
        [
            visible,
            group.id,
            memberLayerIds,
            queryClient,
            excludedSet,
            hideLayerGroup,
            showLayerGroup,
        ],
    );

    const hideLabel = t("filters.hideLayerGroup");
    const showLabel = t("filters.showLayerGroup");

    return (
        <VStack w="full" gap={1} align="stretch">
            <Box
                role="group"
                aria-label={group.name}
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
                    cursor={layers.length > 0 ? "pointer" : "default"}
                    onClick={layers.length > 0 ? () => setToggled(!isExpanded) : undefined}
                >
                    <Flex w="full" align="start">
                        <HStack w="full" justify="space-between">
                            <VStack align="start" minW={0} flex={1}>
                                <HStack gap={1.5}>
                                    <Icon
                                        color={layers.length > 0 ? "slate.text.muted" : "gray.600"}
                                        boxSize={`${ROW_CHEVRON_SIZE}px`}
                                        rotate={isExpanded ? "0deg" : "-90deg"}
                                        transition="transform 0.2s ease"
                                    >
                                        <TbChevronDown size={ROW_CHEVRON_SIZE} />
                                    </Icon>
                                    <Box position="relative" flexShrink={0} display="inline-flex">
                                        <Icon color={isExpanded ? "active" : "slate.text.muted"}>
                                            {isExpanded ? (
                                                <TbFolderOpen size={16} />
                                            ) : (
                                                <TbFolder size={16} />
                                            )}
                                        </Icon>
                                        {group.accessScope === "GLOBAL" && (
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
                                        {group.name}
                                    </TacticalText>
                                    <Text fontSize="xs" color="fg.muted" flexShrink={0}>
                                        ({layers.length})
                                    </Text>
                                </HStack>
                            </VStack>
                            <IconButton
                                aria-label={visible ? hideLabel : showLabel}
                                size="xs"
                                boxSize={ROW_ICON_BUTTON_SIZE}
                                minW={ROW_ICON_BUTTON_SIZE}
                                variant="ghost"
                                color={visible ? "fg" : "fg.muted"}
                                onClick={onToggleVisibility}
                            >
                                {visible ? <FaEye size={16} /> : <FaEyeSlash size={16} />}
                            </IconButton>
                        </HStack>
                    </Flex>
                </Flex>
            </Box>

            {isExpanded && layers.length > 0 && (
                <VStack pl={4} gap={1} align="stretch">
                    {layers.map((layer) => (
                        <SourceFilter
                            key={layer.layerId}
                            sourceId={layer.layerId}
                            sourceName={layer.layerName}
                            accessScope={layer.accessScope}
                            kind="layer"
                            searchTerm={searchTerm}
                        />
                    ))}
                </VStack>
            )}
        </VStack>
    );
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Collapsible, Flex, Spinner, Text, VStack } from "@chakra-ui/react";
import { useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { FaCircleInfo } from "react-icons/fa6";
import { TbChevronDown, TbChevronRight } from "react-icons/tb";
import { useQuery } from "@tanstack/react-query";
import type { MapTile } from "@opencop/api";
import { SELECTED_TILE_KEY } from "@opencop/api";
import { ListPanelItem, OcSwitch, Tooltip, scrollbarStyles } from "@opencop/ui";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { getTileKind } from "~/features/imagery/tile-config.util";
import { isWeatherTile } from "~/features/weather/weather.util";
import { useTerrainActions, useTerrainState } from "../map/core/slices/terrain/terrain.hook";
import { useSelectedTerrainActions, useSelectedTerrainState } from "./selected-terrain.store";

const TERRAIN_SWITCH_ROOT_PROPS = { cursor: "pointer" } as const;

// Bare <button> so the icon is "interactive content" per the HTML label-
// activation spec — clicks on it do NOT toggle the switch the label is bound
// to. A non-interactive <span> would forward clicks to the switch regardless
// of preventDefault / stopPropagation. tabIndex / aria-hidden keep it out of
// the tab order and a11y tree (the Tooltip carries the description text).
const INFO_ICON_BUTTON_STYLE: CSSProperties = {
    display: "inline-flex",
    background: "transparent",
    border: 0,
    padding: 0,
    cursor: "default",
};

export function TerrainPanel() {
    const { t } = useTranslation();
    const terrainId = useSelectedTerrainState((s) => s.terrainId);
    const textureId = useSelectedTerrainState((s) => s.textureId);
    const { setTerrainId, setTextureId, reset: resetSelection } = useSelectedTerrainActions();

    const { data: tiles = [], isLoading } = useQuery(tileQueries.list());
    const terrainTiles = tiles.filter((t) => getTileKind(t) === "TERRAIN_MESH");
    const rasterTiles = tiles.filter((t) => getTileKind(t) === "RASTER" && !isWeatherTile(t));

    const drapeEnabled = useTerrainState((s) => s.drape);
    const depthCompare = useTerrainState((s) => s.depthCompare);
    const { setDrape, setDepthCompare } = useTerrainActions();

    const [configOpen, setConfigOpen] = useState(true);

    const selectedTerrain = terrainId ? terrainTiles.find((t) => t.id === terrainId) : null;

    const handleDrapeToggle = (checked: boolean) => {
        setDrape(checked);
    };

    const handleDepthCompareToggle = (checked: boolean) => {
        setDepthCompare(checked ? "always" : "lessequal");
    };

    const handleSelectTerrain = (tile: MapTile) => {
        if (terrainId === tile.id) {
            resetSelection();
        } else {
            // Select terrain — auto-pick a texture from the current imagery
            // basemap if it's RASTER, otherwise fall back to first raster tile
            let defaultTextureId: string | null = null;
            const imageryTileId = sessionStorage.getItem(SELECTED_TILE_KEY);
            const imageryTile = imageryTileId ? tiles.find((t) => t.id === imageryTileId) : null;
            if (imageryTile && getTileKind(imageryTile) === "RASTER") {
                defaultTextureId = imageryTile.id;
            } else if (rasterTiles.length > 0) {
                defaultTextureId = rasterTiles[0].id;
            }

            setTerrainId(tile.id);
            if (defaultTextureId) setTextureId(defaultTextureId);
        }
    };

    const handleSelectTexture = (tile: MapTile) => {
        setTextureId(tile.id);
    };

    if (isLoading) {
        return (
            <Flex align="center" justify="center" h="200px" w="full">
                <Spinner size="lg" color="active" />
            </Flex>
        );
    }

    return (
        <VStack w="full" h="full" gap={4} align="stretch">
            {/* Configuration Section — collapsible, only when terrain is selected */}
            {terrainId && (
                <Collapsible.Root open={configOpen} onOpenChange={(e) => setConfigOpen(e.open)}>
                    <Collapsible.Trigger asChild>
                        <Flex
                            w="full"
                            justify="space-between"
                            align="center"
                            cursor="pointer"
                            userSelect="none"
                            py="1"
                        >
                            <Text
                                fontSize="xs"
                                fontWeight="600"
                                color="fg.muted"
                                textTransform="uppercase"
                            >
                                {t("terrain.configurationSection")}
                            </Text>
                            <Box color="fg.muted" fontSize="sm">
                                {configOpen ? <TbChevronDown /> : <TbChevronRight />}
                            </Box>
                        </Flex>
                    </Collapsible.Trigger>
                    <Collapsible.Content>
                        <VStack gap="3" py="2">
                            <OcSwitch
                                rootProps={TERRAIN_SWITCH_ROOT_PROPS}
                                label={
                                    <Tooltip
                                        content={t("terrain.depthCompareInfo")}
                                        contentProps={{ maxW: "250px", whiteSpace: "pre-line" }}
                                    >
                                        <Flex
                                            as="span"
                                            align="center"
                                            gap="1"
                                            cursor="default"
                                            display="inline-flex"
                                        >
                                            <Text
                                                as="span"
                                                fontSize="xs"
                                                fontWeight="600"
                                                color="active"
                                                textTransform="uppercase"
                                            >
                                                {t("terrain.depthCompare")}
                                            </Text>
                                            <button
                                                type="button"
                                                tabIndex={-1}
                                                aria-hidden="true"
                                                style={INFO_ICON_BUTTON_STYLE}
                                                onMouseDown={(e) => e.preventDefault()}
                                            >
                                                <FaCircleInfo
                                                    size={10}
                                                    color="var(--chakra-colors-fg-muted)"
                                                />
                                            </button>
                                        </Flex>
                                    </Tooltip>
                                }
                                checked={depthCompare === "always"}
                                onCheckedChange={handleDepthCompareToggle}
                            />
                            <OcSwitch
                                rootProps={TERRAIN_SWITCH_ROOT_PROPS}
                                label={
                                    <Tooltip
                                        content={t("terrain.drapeInfo")}
                                        contentProps={{ maxW: "250px", whiteSpace: "pre-line" }}
                                    >
                                        <Flex
                                            as="span"
                                            align="center"
                                            gap="1"
                                            cursor="default"
                                            display="inline-flex"
                                        >
                                            <Text
                                                as="span"
                                                fontSize="xs"
                                                fontWeight="600"
                                                color="active"
                                                textTransform="uppercase"
                                            >
                                                {t("terrain.drape")}
                                            </Text>
                                            <button
                                                type="button"
                                                tabIndex={-1}
                                                aria-hidden="true"
                                                style={INFO_ICON_BUTTON_STYLE}
                                                onMouseDown={(e) => e.preventDefault()}
                                            >
                                                <FaCircleInfo
                                                    size={10}
                                                    color="var(--chakra-colors-fg-muted)"
                                                />
                                            </button>
                                        </Flex>
                                    </Tooltip>
                                }
                                checked={drapeEnabled}
                                onCheckedChange={handleDrapeToggle}
                            />
                        </VStack>
                    </Collapsible.Content>
                </Collapsible.Root>
            )}

            {/* Terrain Source Section */}
            <Box>
                <Text
                    fontSize="xs"
                    fontWeight="600"
                    color="fg.muted"
                    textTransform="uppercase"
                    mb="2"
                >
                    {t("terrain.terrainSection")}
                </Text>
                <VStack
                    w="full"
                    overflowY="auto"
                    borderRadius="sm"
                    p="1"
                    userSelect="none"
                    css={scrollbarStyles}
                    gap={2}
                >
                    {terrainTiles.map((tile, i) => {
                        return (
                            <TileItem
                                key={tile.id}
                                tile={tile}
                                isSelected={terrainId === tile.id}
                                disabled={rasterTiles.length === 0}
                                index={i + 1}
                                onSelect={handleSelectTerrain}
                                ariaLabel={t("terrain.selectTerrain", { name: tile.name })}
                            />
                        );
                    })}
                    {rasterTiles.length === 0 && terrainTiles.length > 0 && (
                        <Text color="fg.muted" fontSize="xs" px="1">
                            {t("terrain.addRasterFirst")}
                        </Text>
                    )}
                </VStack>
            </Box>

            {/* Texture Overlay Section — only visible when terrain is selected */}
            {selectedTerrain && (
                <Box>
                    <Text
                        fontSize="xs"
                        fontWeight="600"
                        color="fg.muted"
                        mb="2"
                        textTransform="uppercase"
                    >
                        {t("terrain.textureSection")}
                    </Text>
                    {rasterTiles.length === 0 ? (
                        <VStack w="full" py={4} gap={1}>
                            <Text color="gray.400" fontSize="sm">
                                {t("terrain.noRasterTiles")}
                            </Text>
                        </VStack>
                    ) : (
                        <VStack
                            w="full"
                            overflowY="auto"
                            borderRadius="sm"
                            p="1"
                            userSelect="none"
                            css={scrollbarStyles}
                            gap={2}
                        >
                            {rasterTiles.map((tile, i) => {
                                return (
                                    <TileItem
                                        key={tile.id}
                                        tile={tile}
                                        isSelected={textureId === tile.id}
                                        index={i}
                                        onSelect={handleSelectTexture}
                                        ariaLabel={t("terrain.selectTexture", { name: tile.name })}
                                    />
                                );
                            })}
                        </VStack>
                    )}
                </Box>
            )}
        </VStack>
    );
}

// ---------------------------------------------------------------------------
// Shared tile item component
// ---------------------------------------------------------------------------

interface TileItemProps {
    tile: MapTile;
    isSelected: boolean;
    disabled?: boolean;
    index: number;
    onSelect: (tile: MapTile) => void;
    ariaLabel: string;
}

function TileItem({ tile, isSelected, disabled, index, onSelect, ariaLabel }: TileItemProps) {
    return (
        <ListPanelItem
            index={index}
            isSelected={isSelected}
            disabled={disabled}
            onClick={() => onSelect(tile)}
            ariaLabel={ariaLabel}
            title={tile.name}
            subtitle={tile.description}
            compact
        />
    );
}

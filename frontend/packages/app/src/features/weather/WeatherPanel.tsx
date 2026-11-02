// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert, Box, Button, Text, VStack } from "@chakra-ui/react";
import { LayerToggleBody, PanelSectionHeading, SortableLayerList } from "@opencop/ui";
import { arrayMove } from "@dnd-kit/sortable";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getWeatherOpacity, useWeatherStore } from "./weather.store";
import { isSupportedWeatherTile } from "./weather.util";
import { SortableWeatherEntry } from "./SortableWeatherEntry";
import { tileQueries } from "~/features/imagery/imagery.queries";

interface WeatherTileToggleProps {
    id: string;
    label: string;
    opacityLabel: string;
}

function WeatherTileToggle({ id, label, opacityLabel }: WeatherTileToggleProps) {
    const layers = useWeatherStore((s) => s.layers);
    const checked = layers.find((l) => l.id === id)?.active ?? false;
    const opacity = getWeatherOpacity(layers, id);
    const toggleLayer = useWeatherStore((s) => s.toggleLayer);
    const setOpacity = useWeatherStore((s) => s.setOpacity);

    return (
        <LayerToggleBody
            name={label}
            checked={checked}
            opacity={opacity}
            opacityLabel={opacityLabel}
            onCheckedChange={() => toggleLayer(id)}
            onOpacityChange={(nextOpacity) => setOpacity(id, nextOpacity)}
        />
    );
}

export function WeatherPanel() {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const layers = useWeatherStore((s) => s.layers);
    const reorderLayers = useWeatherStore((s) => s.reorderLayers);

    const {
        data: allTiles = [],
        isError,
        isLoading,
    } = useQuery({
        ...tileQueries.list(),
    });

    const weatherTiles = useMemo(() => allTiles.filter(isSupportedWeatherTile), [allTiles]);

    const layerIds = useMemo(() => layers.map((l) => l.id), [layers]);

    const opacityLabel = t("weather.opacity") as string;

    // Show tiles that exist, plus any active ones still in the store (e.g. during error state).
    const visibleTiles = useMemo(() => {
        if (isError) {
            const activeIds = new Set(layers.filter((l) => l.active).map((l) => l.id));
            return weatherTiles.length > 0
                ? weatherTiles
                : allTiles.filter((tile) => isSupportedWeatherTile(tile) && activeIds.has(tile.id));
        }
        return weatherTiles;
    }, [weatherTiles, allTiles, layers, isError]);

    // Render tiles in layers sequence (last in list = on top visually = highest z-index).
    const orderedVisibleTiles = useMemo(() => {
        return [...visibleTiles].sort((a, b) => {
            const ai = layerIds.indexOf(a.id);
            const bi = layerIds.indexOf(b.id);
            return (ai === -1 ? Infinity : ai) - (bi === -1 ? Infinity : bi);
        });
    }, [visibleTiles, layerIds]);

    return (
        <VStack w="full" align="stretch" gap="4">
            <Box>
                <PanelSectionHeading>{t("weather.layersSection")}</PanelSectionHeading>
                <SortableLayerList ids={layerIds} onReorder={(ids) => reorderLayers(ids)}>
                    <VStack w="full" align="stretch" gap="0" divideY="1px">
                        {orderedVisibleTiles.map((tile, i) => (
                            <SortableWeatherEntry
                                key={tile.id}
                                id={tile.id}
                                canMoveUp={i > 0}
                                canMoveDown={i < orderedVisibleTiles.length - 1}
                                onMoveUp={() => {
                                    const idx = layerIds.indexOf(tile.id);
                                    if (idx > 0) reorderLayers(arrayMove(layerIds, idx, idx - 1));
                                }}
                                onMoveDown={() => {
                                    const idx = layerIds.indexOf(tile.id);
                                    if (idx < layerIds.length - 1)
                                        reorderLayers(arrayMove(layerIds, idx, idx + 1));
                                }}
                            >
                                <WeatherTileToggle
                                    id={tile.id}
                                    label={tile.name}
                                    opacityLabel={opacityLabel}
                                />
                            </SortableWeatherEntry>
                        ))}
                    </VStack>
                </SortableLayerList>
                {isError && (
                    <Alert.Root status="error" size="sm" mt="3">
                        <Alert.Indicator />
                        <Alert.Content>
                            <Alert.Description>{t("weather.sourceLoadFailed")}</Alert.Description>
                        </Alert.Content>
                        <Button
                            size="xs"
                            variant="ghost"
                            flexShrink={0}
                            onClick={() =>
                                queryClient.invalidateQueries({
                                    queryKey: tileQueries.lists(),
                                })
                            }
                        >
                            {t("weather.retry")}
                        </Button>
                    </Alert.Root>
                )}
                {!isLoading && !isError && weatherTiles.length === 0 && (
                    <Text mt="3" fontSize="sm" color="fg.muted">
                        {t("weather.noSourcesAvailable")}
                    </Text>
                )}
            </Box>
        </VStack>
    );
}

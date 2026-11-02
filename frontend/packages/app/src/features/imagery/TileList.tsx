// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Center, Flex, Spinner, Text, VStack } from "@chakra-ui/react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { tileQueries, wmsCapabilitiesQueries } from "~/features/imagery/imagery.queries";
import type { MapTile, WmsTileConfig, WmtsTileConfig } from "@opencop/api";
import { SortableLayerList } from "@opencop/ui";
import { isKvpWmtsUrl } from "@opencop/layers";
import { arrayMove } from "@dnd-kit/sortable";
import { isImageryOverlayCapable } from "./imagery.util";
import { isWeatherTile } from "~/features/weather/weather.util";
import { getTileKind } from "./tile-config.util";
import type { ImageryOverlayEntry } from "./imagery-overlay.store";
import { useImageryOverlayStore, useImageryOverlayActions } from "./imagery-overlay.store";
import { SortableImageryEntry } from "./SortableImageryEntry";

function buildOverlayTooltip(
    tile: MapTile,
    kindLabel: string,
    hasHistory: string,
    noHistory: string,
): ReactNode {
    const isWmsCapable = tile.config.tile.kind === "WMS" || tile.config.tile.kind === "WMTS";
    return (
        <VStack align="start" gap="1" maxW="xs">
            <Text fontSize="xs" fontWeight="semibold">
                {tile.name}
            </Text>
            {tile.description && <Text fontSize="xs">{tile.description}</Text>}
            <Text fontSize="2xs" color="fg.muted">
                {kindLabel}
            </Text>
            <Text fontSize="xs" color={isWmsCapable ? "type.success" : "fg.subtle"}>
                {isWmsCapable ? hasHistory : noHistory}
            </Text>
        </VStack>
    );
}

interface WmsAwareSortableEntryProps {
    overlay: ImageryOverlayEntry;
    tile: MapTile;
    kindLabel: string;
    candidateIdx: number;
    totalCandidates: number;
    candidateIds: string[];
    onReorderCandidates: (ids: string[]) => void;
    onOpacityChange: (v: number) => void;
    onVisibleChange: (v: boolean) => void;
    initialOpen?: boolean;
}

function WmsAwareSortableEntry({
    overlay,
    tile,
    kindLabel,
    candidateIdx,
    totalCandidates,
    candidateIds,
    onReorderCandidates,
    onOpacityChange,
    onVisibleChange,
    initialOpen,
}: WmsAwareSortableEntryProps) {
    const { t } = useTranslation();
    const { setOverlayTime } = useImageryOverlayActions();

    const tileUrl = tile.url ?? "";
    const isWms = tile.config.tile.kind === "WMS";
    const isWmts = tile.config.tile.kind === "WMTS";
    const isKvpWmts = isWmts && isKvpWmtsUrl(tileUrl);

    const wmsLayerName = isWms ? ((tile.config.tile as WmsTileConfig).layers[0] ?? "") : "";
    const kvpWmtsLayerName = isKvpWmts ? ((tile.config.tile as WmtsTileConfig).layer ?? "") : "";

    const [detailsOpen, setDetailsOpen] = useState(initialOpen ?? false);
    if (initialOpen && !detailsOpen) {
        setDetailsOpen(true);
    }

    const { data: wmsCapabilities, isFetched: wmsFetched } = useQuery({
        ...wmsCapabilitiesQueries.timeDimension(tileUrl, wmsLayerName),
        enabled: isWms && !!tileUrl && !!wmsLayerName && (overlay.visible || detailsOpen),
    });

    const { data: kvpWmtsCapabilities, isFetched: kvpWmtsFetched } = useQuery({
        ...wmsCapabilitiesQueries.kvpWmtsTimeDimension(tileUrl, kvpWmtsLayerName),
        enabled: isKvpWmts && !!tileUrl && !!kvpWmtsLayerName && (overlay.visible || detailsOpen),
    });

    const capabilities = isWms ? wmsCapabilities : kvpWmtsCapabilities;
    const capsFetched = isWms ? wmsFetched : kvpWmtsFetched;
    const hasTimeControls = capsFetched && !!capabilities;

    // overlay.time === null is the "always latest" sentinel — the layer omits the TIME
    // parameter so the server returns its current data on every tile fetch. We only write
    // a date into the store when the user explicitly picks one. For display purposes we
    // fall back to capabilities.timeMax so the picker starts at the latest available date.
    const effectiveTime = overlay.time ?? capabilities?.timeMax.toISO() ?? null;

    const nameTooltip = buildOverlayTooltip(
        tile,
        kindLabel,
        t("imagery.overlay.hasHistory"),
        t("imagery.overlay.noHistory"),
    );

    return (
        <SortableImageryEntry
            id={overlay.id}
            name={tile.name}
            kindLabel={kindLabel}
            nameTooltip={nameTooltip}
            opacity={overlay.opacity}
            visible={overlay.visible}
            canMoveUp={candidateIdx > 0}
            canMoveDown={candidateIdx < totalCandidates - 1}
            onMoveUp={() => {
                if (candidateIdx > 0)
                    onReorderCandidates(arrayMove(candidateIds, candidateIdx, candidateIdx - 1));
            }}
            onMoveDown={() => {
                if (candidateIdx < totalCandidates - 1)
                    onReorderCandidates(arrayMove(candidateIds, candidateIdx, candidateIdx + 1));
            }}
            onOpacityChange={onOpacityChange}
            onVisibleChange={onVisibleChange}
            hasTimeControls={hasTimeControls}
            time={effectiveTime}
            timeMin={capabilities?.timeMin ?? null}
            timeMax={capabilities?.timeMax ?? null}
            step={capabilities?.step ?? null}
            onTimeChange={(ti) => setOverlayTime(overlay.id, ti)}
            detailsOpen={detailsOpen}
            onDetailsOpenChange={setDetailsOpen}
        />
    );
}

export function TileList() {
    const { t } = useTranslation();
    const { data: allTiles = [], isLoading } = useQuery(tileQueries.list());

    const overlays = useImageryOverlayStore((s) => s.overlays);
    const { toggleOverlay, setOverlayOpacity, setOverlayVisible, reorderOverlays } =
        useImageryOverlayActions();

    const [justActivatedIds, setJustActivatedIds] = useState<Set<string>>(() => new Set());

    const overlayCandidates = useMemo(
        () =>
            allTiles.filter((tile) => {
                const kind = getTileKind(tile);
                return (
                    (kind === "RASTER" || kind === "WMS" || kind === "WMTS") &&
                    !isWeatherTile(tile) &&
                    isImageryOverlayCapable(tile)
                );
            }),
        [allTiles],
    );

    // userOrder tracks the user's explicit drag-to-reorder sequence.
    // candidateOrder merges it with the live candidate list: kept entries
    // preserve their slot, new candidates are appended, stale ones are dropped.
    const [userOrder, setUserOrder] = useState<string[]>([]);
    const candidateOrder = useMemo(() => {
        const currentIds = new Set(overlayCandidates.map((t) => t.id));
        const kept = userOrder.filter((id) => currentIds.has(id));
        const added = overlayCandidates.filter((t) => !userOrder.includes(t.id)).map((t) => t.id);
        return [...kept, ...added];
    }, [overlayCandidates, userOrder]);

    const overlayById = useMemo(
        () =>
            new Map(
                overlays.flatMap((o) => {
                    const tile = allTiles.find((ti) => ti.id === o.id);
                    return tile ? [[o.id, { overlay: o, tile }] as const] : [];
                }),
            ),
        [overlays, allTiles],
    );

    const candidateById = useMemo(
        () => new Map(overlayCandidates.map((t) => [t.id, t])),
        [overlayCandidates],
    );

    // Ordered candidate list — falls back to API order before candidateOrder initialises.
    const orderedCandidates = useMemo(() => {
        if (candidateOrder.length === 0) return overlayCandidates;
        return candidateOrder.flatMap((id) => {
            const tile = candidateById.get(id);
            return tile ? [tile] : [];
        });
    }, [candidateOrder, candidateById, overlayCandidates]);

    if (isLoading) {
        return (
            <Center h="200px" w="full">
                <Spinner size="lg" color="active" />
            </Center>
        );
    }

    if (overlayCandidates.length === 0) {
        return (
            <VStack w="full" py={10} gap={2}>
                <Text color="fg.muted" fontSize="sm">
                    {t("imagery.noTiles")}
                </Text>
                <Text color="fg.muted" fontSize="xs">
                    {t("imagery.contactAdmin")}
                </Text>
            </VStack>
        );
    }

    const totalCandidates = orderedCandidates.length;
    const candidateIds = orderedCandidates.map((t) => t.id);

    return (
        <Flex direction="column" flex="1 1 0" minH="0px" w="full" userSelect="none" overflow="auto">
            <SortableLayerList
                ids={candidateIds}
                onReorder={(newIds) => {
                    setUserOrder(newIds);
                    reorderOverlays(newIds);
                }}
            >
                <VStack w="full" align="stretch" gap="0" divideY="1px">
                    {orderedCandidates.map((tile, i) => {
                        const kind = getTileKind(tile);
                        const kindLabel = t(`adminImagery.kinds.${kind.toLowerCase()}` as never);
                        const activeEntry = overlayById.get(tile.id);
                        const overlay: ImageryOverlayEntry = activeEntry?.overlay ?? {
                            id: tile.id,
                            opacity: 50,
                            time: null,
                            visible: false,
                        };

                        return (
                            <WmsAwareSortableEntry
                                key={tile.id}
                                overlay={overlay}
                                tile={tile}
                                kindLabel={kindLabel}
                                candidateIdx={i}
                                totalCandidates={totalCandidates}
                                candidateIds={candidateIds}
                                onReorderCandidates={(newIds) => {
                                    setUserOrder(newIds);
                                    reorderOverlays(newIds);
                                }}
                                onOpacityChange={(v) => {
                                    if (activeEntry) setOverlayOpacity(tile.id, v);
                                }}
                                onVisibleChange={(v) => {
                                    if (activeEntry) {
                                        setOverlayVisible(tile.id, v);
                                    } else if (v) {
                                        setJustActivatedIds((prev) => new Set(prev).add(tile.id));
                                        toggleOverlay(tile.id);
                                    }
                                }}
                                initialOpen={justActivatedIds.has(tile.id)}
                            />
                        );
                    })}
                </VStack>
            </SortableLayerList>
        </Flex>
    );
}

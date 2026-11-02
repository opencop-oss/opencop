// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { StyleSpecification } from "maplibre-gl";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMap } from "react-map-gl/maplibre";
import type { MapTile } from "@opencop/api";
import { SELECTED_TILE_KEY } from "@opencop/api";
import { tileQueries } from "~/features/imagery/imagery.queries";
import {
    preferencesQueries,
    preferencesMutations,
} from "~/features/preferences/preferences.queries";
import { isWeatherTile } from "~/features/weather/weather.util";
import { getTileKind } from "./tile-config.util";
import { resolveMapStyle } from "./imagery.util";
import { useToast } from "~/features/notifications/use-toast.hook";
import type { BasemapRow } from "./basemap.types";
import { BasemapToggleBar } from "./BasemapToggleBar";
import { BasemapCardPanel } from "./BasemapCardPanel";
import { useSetActiveBasemap } from "~/features/imagery/ActiveBasemapContext";

export function BasemapPicker() {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const mapRefs = useMap();
    const { showToast } = useToast();
    // Each pick gets a generation, so an older pick of any tile, the same one included, can be
    // told from the latest one at every point it resumes.
    const pickGenerationRef = useRef(0);
    const [open, setOpen] = useState(false);
    const [globalFilter, setGlobalFilter] = useState("");

    const { data: allTiles = [] } = useQuery(tileQueries.list());
    const { data: preferences } = useQuery(preferencesQueries.current());
    const { mutate: updateDefaultTile } = useMutation(preferencesMutations.update(queryClient));
    const setActiveBasemap = useSetActiveBasemap();

    const [clickedTileId, setClickedTileId] = useState<string | null>(() =>
        sessionStorage.getItem(SELECTED_TILE_KEY),
    );

    const basemapTiles = useMemo(
        () =>
            allTiles
                .filter((tile) => {
                    const kind = getTileKind(tile);
                    return (kind === "VECTOR" || kind === "RASTER") && !isWeatherTile(tile);
                })
                .sort((a, b) => a.name.localeCompare(b.name)),
        [allTiles],
    );

    const selectedTileId = useMemo(() => {
        if (basemapTiles.length === 0) return null;
        if (clickedTileId && basemapTiles.some((t) => t.id === clickedTileId)) return clickedTileId;
        const defaultId = preferences?.defaultTile?.id;
        if (defaultId && basemapTiles.some((t) => t.id === defaultId)) return defaultId;
        return basemapTiles.find((t) => t.primary)?.id ?? null;
    }, [basemapTiles, clickedTileId, preferences?.defaultTile?.id]);

    async function handleSelectTile(tile: MapTile) {
        const generation = ++pickGenerationRef.current;
        const overtaken = () => generation !== pickGenerationRef.current;
        setClickedTileId(tile.id);
        try {
            // Every await, and the catch, is followed by this check: a later pick can complete
            // during any of them, and the older handler must then neither apply its style, commit
            // its choice, nor undo the later pick's highlight.
            const detail = await queryClient.fetchQuery(tileQueries.detail(tile.id));
            if (overtaken()) return;
            const style = resolveMapStyle(detail);
            const mapMaybe = mapRefs.current?.getMap();
            if (!mapMaybe) return;
            const map = mapMaybe;

            // A style object, unlike a URL, lets setStyle() diff instead of blanking the map
            // for a frame; fetching it first also lands a failing provider in the catch below.
            let resolvedStyle: StyleSpecification | string = style;
            if (typeof style === "string") {
                const res = await fetch(style);
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                if (overtaken()) return;
                resolvedStyle = (await res.json()) as StyleSpecification;
                if (overtaken()) return;
            }

            map.setStyle(resolvedStyle);
            // Committed only now. `useInitialMapStyle` reads this key whenever the tile list or the
            // default changes, so a choice stored before its style applied could be promoted
            // mid-switch and, if the switch then failed, never retracted.
            sessionStorage.setItem(SELECTED_TILE_KEY, tile.id);
            setActiveBasemap(tile.id);
            updateDefaultTile({ defaultTileId: tile.id });
        } catch {
            if (overtaken()) return;
            // Back to the committed selection; the map is still on it.
            setClickedTileId(sessionStorage.getItem(SELECTED_TILE_KEY));
            showToast({ type: "error", title: t("imagery.basemapPicker.loadError") });
        }
    }

    const basemapRows = useMemo<BasemapRow[]>(
        () =>
            basemapTiles.map((tile) => {
                const kind = getTileKind(tile);
                return {
                    id: tile.id,
                    name: tile.name,
                    kindLabel: t(`adminImagery.kinds.${kind.toLowerCase()}` as never),
                    description: tile.description ?? null,
                    tile,
                };
            }),
        [basemapTiles, t],
    );

    const filteredRows = useMemo(() => {
        if (!globalFilter.trim()) return basemapRows;
        const q = globalFilter.toLowerCase();
        return basemapRows.filter(
            (r) => r.name.toLowerCase().includes(q) || r.kindLabel.toLowerCase().includes(q),
        );
    }, [basemapRows, globalFilter]);

    return (
        <Box w="full" pointerEvents="none">
            <Box px="2" pb="1.5" pointerEvents="auto">
                <BasemapToggleBar
                    open={open}
                    onToggle={() => setOpen((prev) => !prev)}
                    search={globalFilter}
                    onSearchChange={setGlobalFilter}
                />
            </Box>
            <BasemapCardPanel
                open={open}
                rows={filteredRows}
                selectedRowId={selectedTileId}
                onSelect={(tile) => void handleSelectTile(tile)}
            />
        </Box>
    );
}

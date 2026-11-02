// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import { isEqual } from "lodash";
import type { Map as MaplibreMap, MapSourceDataEvent, MapStyleDataEvent } from "maplibre-gl";
import { useMemo, useSyncExternalStore } from "react";
import { useMap } from "react-map-gl/maplibre";
import { useShallow } from "zustand/react/shallow";
import { useActiveBasemapTileId } from "~/features/imagery/ActiveBasemapContext";
import { useImageryOverlayStore } from "~/features/imagery/imagery-overlay.store";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { useSelectedTerrainState } from "~/features/terrain/selected-terrain.store";
import { useWeatherStore } from "~/features/weather/weather.store";
import { useTerrainState } from "~/features/map/core/slices/terrain/terrain.hook";
import { collectMapCredits, readStyleAttributions } from "~/features/map/map-attribution.util";

const EMPTY: string[] = [];
const subscribeNowhere = () => () => {};
const getEmpty = () => EMPTY;

/**
 * The credits for what is on the map now, each parsed once into its text and links; see
 * `collectMapCredits` for which sources count and in what order.
 */
export function useMapAttributions() {
    const styleAttributions = useStyleAttributions();
    const { data: tiles } = useQuery(tileQueries.list());
    const activeBasemapTileId = useActiveBasemapTileId();
    // Ids only: the arrays behind them are rebuilt on every opacity or time tick, and the credits
    // depend on which overlays are visible and which weather layers are active, not on how they look.
    const visibleOverlayIds = useImageryOverlayStore(
        useShallow((s) => s.overlays.filter((o) => o.visible).map((o) => o.id)),
    );
    const activeWeatherIds = useWeatherStore(
        useShallow((s) => s.layers.filter((l) => l.active).map((l) => l.id)),
    );
    const terrainOn = useTerrainState((s) => s.terrainConfig !== null);
    const terrainId = useSelectedTerrainState((s) => s.terrainId);
    const textureId = useSelectedTerrainState((s) => s.textureId);

    return useMemo(
        () =>
            collectMapCredits({
                styleAttributions,
                attributionByTileId: new Map(
                    (tiles ?? []).map((tile) => [tile.id, tile.attribution]),
                ),
                activeBasemapTileId,
                terrain: terrainOn ? { terrainId, textureId } : null,
                visibleOverlayIds,
                activeWeatherIds,
            }),
        [
            styleAttributions,
            tiles,
            activeBasemapTileId,
            visibleOverlayIds,
            activeWeatherIds,
            terrainOn,
            terrainId,
            textureId,
        ],
    );
}

function useStyleAttributions() {
    const { current: mapRef } = useMap();
    const map = mapRef?.getMap();
    const store = useMemo(() => (map ? createStyleAttributionStore(map) : null), [map]);
    return useSyncExternalStore(
        store?.subscribe ?? subscribeNowhere,
        store?.getSnapshot ?? getEmpty,
    );
}

/**
 * The style's source attributions as an external store, re-read on the events MapLibre's own
 * AttributionControl listens to: `styledata`, `sourcedata` for `metadata` and `visibility`, and
 * `terrain`. A layer entering or leaving its zoom range arrives as `visibility`, which MapLibre fires
 * when a source's used flag flips. "content" sourcedata fires per tile and never changes a source's
 * attribution, so it is skipped. The snapshot keeps its identity until the strings change.
 */
function createStyleAttributionStore(map: MaplibreMap) {
    let snapshot = readStyleAttributions(map);
    return {
        getSnapshot: () => snapshot,
        subscribe(onChange: () => void) {
            const refresh = () => {
                const next = readStyleAttributions(map);
                if (isEqual(snapshot, next)) return;
                snapshot = next;
                onChange();
            };
            const onData = (e: MapStyleDataEvent | MapSourceDataEvent) => {
                if (
                    e.dataType === "style" ||
                    ("sourceDataType" in e &&
                        (e.sourceDataType === "metadata" || e.sourceDataType === "visibility"))
                ) {
                    refresh();
                }
            };
            // A style may have loaded between creating the store and subscribing to it.
            refresh();
            map.on("styledata", onData);
            map.on("sourcedata", onData);
            map.on("terrain", refresh);
            return () => {
                map.off("styledata", onData);
                map.off("sourcedata", onData);
                map.off("terrain", refresh);
            };
        },
    };
}

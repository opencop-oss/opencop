// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Image, Skeleton } from "@chakra-ui/react";
import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { assertNever } from "assert-never";
import Logger from "js-logger";
import type { MapTile } from "@opencop/api";
import { getStyleUrl } from "@opencop/api";
import { getTileKind } from "./tile-config.util";
import { renderVectorTilePreview } from "./render-vector-tile-preview.util";
import { buildRasterPreviewUrl } from "./tile-preview-image.util";
import { hasTokenPlaceholder } from "./imagery.util";
import { tileQueries } from "./imagery.queries";

const logger = Logger.get("imagery");

type PreviewState =
    | { kind: "loading" } // waiting for token fetch or VECTOR render queue
    | { kind: "img"; src: string } // URL to try loading — on error, switch to failed
    | { kind: "failed" }; // permanent fallback — no preview available

// Session-scoped caches keyed by tile.id. Not cleared on tile update — a tile
// whose URL or token changes mid-session will serve a stale preview until reload.
const previewCache = new Map<string, string>();
const failedCache = new Set<string>();

// At most one MapLibre WebGL context alive at a time for VECTOR snapshots.
let renderQueue: Promise<void> = Promise.resolve();

interface TilePreviewImageProps {
    tile: MapTile;
    onNoPreview?: () => void;
}

export function TilePreviewImage({ tile, onNoPreview }: TilePreviewImageProps) {
    // Ref so async paths (VECTOR render queue) always call the latest callback
    // without being listed as an effect dependency.
    const onNoPreviewRef = useRef(onNoPreview);
    useEffect(() => {
        onNoPreviewRef.current = onNoPreview;
    });

    // The list endpoint returns token: null for protected tiles. Fetch the detail
    // endpoint (which includes the real token) so the preview URL can be resolved.
    const tileId = tile.id;
    const tileKind = getTileKind(tile);
    const needsToken = hasTokenPlaceholder(tile.url);
    const { data: tileDetail, isError: tokenFetchFailed } = useQuery({
        ...tileQueries.detail(tileId),
        staleTime: Infinity,
        enabled: needsToken && !previewCache.has(tileId) && !failedCache.has(tileId),
    });

    const [preview, setPreview] = useState<PreviewState>(() => {
        if (failedCache.has(tileId)) return { kind: "failed" };

        const cached = previewCache.get(tileId);
        if (cached) return { kind: "img", src: cached };

        if (tileKind === "RASTER") {
            if (needsToken) return { kind: "loading" };
            const src = buildRasterPreviewUrl(getStyleUrl(tile));
            previewCache.set(tileId, src);
            return { kind: "img", src };
        }

        return { kind: "loading" };
    });

    // A failed token fetch (401/403/404) leaves tileDetail undefined forever — the derive
    // block and the VECTOR effect both gate on tileDetail, so the card would shimmer permanently
    // without this explicit transition to the failed state.
    useEffect(() => {
        if (!needsToken || !tokenFetchFailed || failedCache.has(tileId)) return;
        failedCache.add(tileId);
        // eslint-disable-next-line react-hooks/set-state-in-effect, @eslint-react/hooks-extra/no-direct-set-state-in-use-effect -- query error is external state; the failedCache guard makes this fire at most once per tile
        setPreview({ kind: "failed" });
        onNoPreviewRef.current?.();
    }, [needsToken, tokenFetchFailed, tileId]);

    // Notify parent once on mount if this tile was already known to have failed.
    useEffect(() => {
        if (failedCache.has(tileId)) onNoPreviewRef.current?.();
    }, [tileId]);

    // RASTER token preview: derive synchronously during render rather than via useEffect+setState.
    // tileDetail arriving is a React Query re-render, so the "loading" → "img" transition happens
    // here on the same frame. BasemapCardPanel.tsx:17-22 uses the same pattern correctly — a
    // useState setter changes the condition it is guarded by, unlike a module-level Map.set.
    if (preview.kind === "loading" && tileKind === "RASTER" && needsToken && tileDetail) {
        const src = buildRasterPreviewUrl(getStyleUrl(tileDetail));
        previewCache.set(tileId, src);
        setPreview({ kind: "img", src });
    }

    useEffect(() => {
        if (previewCache.has(tileId) || failedCache.has(tileId)) return;
        if (tileKind !== "VECTOR") return;
        if (needsToken && !tileDetail) return;

        const effectiveTile = tileDetail ?? tile;
        const controller = new AbortController();

        renderQueue = renderQueue
            .then(async () => {
                const src = await renderVectorTilePreview(effectiveTile, controller.signal);
                if (src) {
                    previewCache.set(tileId, src);
                    if (!controller.signal.aborted) setPreview({ kind: "img", src });
                } else if (!controller.signal.aborted) {
                    failedCache.add(tileId);
                    setPreview({ kind: "failed" });
                    onNoPreviewRef.current?.();
                }
            })
            .catch((err: unknown) => {
                // Keep the queue alive — a single failed render must not block all subsequent ones.
                logger.error(`vector tile preview render failed for tile ${tileId}`, err);
            });

        return () => controller.abort();
    }, [tileId, tileKind, needsToken, tileDetail, tile]);

    switch (preview.kind) {
        case "loading":
            return <Skeleton w="full" h="full" />;
        case "failed":
            return null;
        case "img":
            return (
                <Image
                    src={preview.src}
                    alt=""
                    w="full"
                    h="full"
                    objectFit="cover"
                    display="block"
                    draggable={false}
                    onError={() => {
                        failedCache.add(tileId);
                        setPreview({ kind: "failed" });
                        onNoPreviewRef.current?.();
                    }}
                />
            );
        default:
            return assertNever(preview);
    }
}

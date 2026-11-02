// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { AccessorContext, Accessor, DefaultProps, LayerProps } from "@deck.gl/core";
import { BitmapLayer, type BitmapLayerProps } from "@deck.gl/layers";
import {
    TileLayer,
    type GeoBoundingBox,
    type NonGeoBoundingBox,
    _WMSLayer as WMSLayer,
    type WMSLayerProps,
} from "@deck.gl/geo-layers";
import {
    CompositeLayer,
    createIterable,
    type Layer,
    type LayersList,
    type UpdateParameters,
} from "@deck.gl/core";
import { WMSSource } from "@loaders.gl/wms";
import { ImageSource } from "@loaders.gl/loader-utils";
import { access } from "../layer.util";

// _WMSLayer._createImageSource checks `props.data instanceof ImageSource`, which fails for
// pre-created WMSImageSource objects because DataSource (their base class) does not extend
// ImageSource at runtime despite the TypeScript declaration. Subclassing and overriding with
// a typeof-string check avoids the broken runtime hierarchy while preserving string-source handling.
class WMSLayerOverride extends WMSLayer {
    override _createImageSource(props: WMSLayerProps): ImageSource {
        if (typeof props.data !== "string") {
            return props.data;
        }
        return super._createImageSource(props);
    }

    // The disposed flag has to live in `state`, not on the instance. deck.gl re-creates layer
    // objects on every render and transfers `state` to the new instance, deliberately leaving
    // the old one pointing at the same object ("We keep the state ref on old layers to support
    // async actions" — core layer.ts _transferState). So a request started by instance A can
    // outlive A's replacement by B, and it is B that eventually gets finalized: an instance
    // field set on B would be invisible to A, and A is the `this` that raises the error.
    declare state: WMSLayer["state"] & { disposed?: boolean };

    // _WMSLayer schedules GetMap on a debounced timer (0ms after a data change, 500ms after a
    // viewport change) but its finalizeState is an empty TODO. Toggle a WMS overlay off inside
    // that window and the timer still fires against a torn-down layer, which throws reading its
    // own state ("Cannot read properties of undefined") before the request even resolves.
    //
    // super.finalizeState() is upstream's empty method, not Layer's — keep the call so we inherit
    // a real implementation for free if upstream ever fills the TODO in.
    override finalizeState(): void {
        clearTimeout(this.state._timeoutId);
        this.state.disposed = true;
        super.finalizeState();
    }

    // A GetMap already in flight cannot be cancelled — loaders.gl calls fetch(url) with no
    // AbortSignal (@loaders.gl/wms wms-source.ts, getMap). When it later rejects, loadImage's
    // catch raises against a layer that is gone and the error reaches deck's onError. Suppress
    // only that post-teardown case; a live layer still reports normally. Optional-chained
    // because raiseError can fire before initializeState has run.
    override raiseError(error: Error, message: string): void {
        if (this.state?.disposed) return;
        super.raiseError(error, message);
    }
}

function isGeoBoundingBox(bbox: GeoBoundingBox | NonGeoBoundingBox): bbox is GeoBoundingBox {
    return "west" in bbox;
}

export function isKvpWmtsUrl(url: string): boolean {
    return /[?&]service=wmts/i.test(url);
}

/** Strips tile-request-specific params so the base URL can be reused as a WMS service endpoint. */
export function wmsBaseUrl(url: string): string {
    try {
        const u = new URL(url);
        for (const key of ["BBOX", "bbox", "WIDTH", "width", "HEIGHT", "height", "TIME", "time"]) {
            u.searchParams.delete(key);
        }
        return u.toString();
    } catch {
        return url;
    }
}

/** Substitute tile URL placeholders with runtime values. */
export function resolveTileUrl(
    url: string,
    token: string | null | undefined,
    time?: string | null,
): string {
    const timeDate = time ? time.split("T")[0] : null;

    // KVP WMTS: inject TIME as a query parameter when time is set.
    const isKvpWmts = isKvpWmtsUrl(url);
    const withTime = isKvpWmts
        ? timeDate
            ? `${url}&TIME=${timeDate}`
            : url
        : url.replaceAll("{Time}", timeDate ?? "default");

    if (!token) return withTime;

    return withTime
        .replaceAll("{key}", token)
        .replaceAll("{token}", token)
        .replaceAll("{api_key}", token)
        .replaceAll("{apikey}", token)
        .replaceAll("{access_token}", token);
}

/** Infer max zoom from a WMTS GoogleMapsCompatible_Level tile matrix set. */
export function inferWmtsMaxZoom(tileMatrixSet: string): number | undefined {
    const match = /^GoogleMapsCompatible_Level(\d+)$/.exec(tileMatrixSet);
    if (!match) return undefined;

    return Number.parseInt(match[1], 10);
}

// ---------------------------------------------------------------------------
// Internal state types (private — not exported)
// ---------------------------------------------------------------------------

type WMSImageSource = ReturnType<typeof WMSSource.createDataSource>;

interface WmsLayerEntry {
    id: string;
    imageSource: WMSImageSource;
    /** url + optional time fragment; used to detect changes and reuse the ImageSource. */
    cacheKey: string;
    layers: string[];
    opacity: number;
}

interface TileLayerEntry {
    id: string;
    tileUrl: string;
    opacity: number;
    tileSize: number;
    maxZoom: number | null | undefined;
    minZoom: number | null | undefined;
}

type LayerState = { wms: WmsLayerEntry[]; tile: TileLayerEntry[] };

type _RasterOverlayLayerProps<DataT> = {
    data: DataT[];
    getId?: Accessor<DataT, string>;
    getTile: Accessor<DataT, string>;
    getToken?: Accessor<DataT, string | null | undefined>;
    getOpacity?: Accessor<DataT, number>;
    getMaxZoom?: Accessor<DataT, number | null | undefined>;
    getMinZoom?: Accessor<DataT, number | null | undefined>;
    getTileSize?: Accessor<DataT, number>;
    /** Whether this entry should render as a WMS layer. @default false */
    getIsWms?: Accessor<DataT, boolean>;
    /** WMS LAYERS parameter. Only read when getIsWms returns true. */
    getWmsLayers?: Accessor<DataT, string[]>;
    /** WMS VERSION parameter. Only read when getIsWms returns true. */
    getWmsVersion?: Accessor<DataT, string>;
    /** WMS FORMAT parameter. Only read when getIsWms returns true. */
    getWmsFormat?: Accessor<DataT, string>;
    /** WMS TRANSPARENT parameter. Only read when getIsWms returns true. */
    getWmsTransparent?: Accessor<DataT, boolean>;
    /** ISO 8601 timestamp for time-aware layers. null = no time filter. */
    getTime?: Accessor<DataT, string | null>;
    refinementStrategy?: "best-available" | "no-overlap" | "never";
};

export type RasterOverlayLayerProps<DataT = unknown> = _RasterOverlayLayerProps<DataT> & LayerProps;

const defaultProps = {
    getId: {
        type: "accessor",
        value: (_: unknown, { index }: { index: number }) => String(index),
    },
    getTile: { type: "accessor", value: "" },
    getToken: { type: "accessor", value: () => null },
    getOpacity: { type: "accessor", value: 100 },
    getMaxZoom: { type: "accessor", value: () => undefined },
    getMinZoom: { type: "accessor", value: () => undefined },
    getTileSize: { type: "accessor", value: 256 },
    getIsWms: { type: "accessor", value: false },
    getWmsLayers: { type: "accessor", value: () => [] as string[] },
    getWmsVersion: { type: "accessor", value: "1.1.1" },
    getWmsFormat: { type: "accessor", value: "image/png" },
    getWmsTransparent: { type: "accessor", value: true },
    getTime: { type: "accessor", value: () => null },
    refinementStrategy: "best-available",
} as const satisfies DefaultProps<RasterOverlayLayerProps<unknown>>;

// ---------------------------------------------------------------------------
// Accessor helpers\
// ---------------------------------------------------------------------------

interface CommonEntryDatum {
    id: string;
    tileUrl: string;
    token: string | null | undefined;
    opacity: number;
    isWms: boolean;
    time: string | null;
}

interface WmsEntryDatum {
    layers: string[];
    version: string;
    format: string;
    transparent: boolean;
}

interface TileEntryDatum {
    tileSize: number;
    maxZoom: number | null | undefined;
    minZoom: number | null | undefined;
}

function getCommonDatum<DataT>(
    props: Required<
        Pick<
            _RasterOverlayLayerProps<DataT>,
            "getId" | "getTile" | "getToken" | "getOpacity" | "getIsWms" | "getTime"
        >
    >,
    object: DataT,
    objectInfo: AccessorContext<DataT>,
): CommonEntryDatum {
    return {
        id: access(props.getId, object, objectInfo),
        tileUrl: access(props.getTile, object, objectInfo),
        token: access(props.getToken, object, objectInfo),
        opacity: access(props.getOpacity, object, objectInfo) / 100,
        isWms: access(props.getIsWms, object, objectInfo),
        time: access(props.getTime, object, objectInfo),
    };
}

function getWmsEntryDatum<DataT>(
    props: Required<
        Pick<
            _RasterOverlayLayerProps<DataT>,
            "getWmsLayers" | "getWmsVersion" | "getWmsFormat" | "getWmsTransparent"
        >
    >,
    object: DataT,
    objectInfo: AccessorContext<DataT>,
): WmsEntryDatum {
    return {
        layers: access(props.getWmsLayers, object, objectInfo),
        version: access(props.getWmsVersion, object, objectInfo),
        format: access(props.getWmsFormat, object, objectInfo),
        transparent: access(props.getWmsTransparent, object, objectInfo),
    };
}

function getTileEntryDatum<DataT>(
    props: Required<
        Pick<_RasterOverlayLayerProps<DataT>, "getTileSize" | "getMaxZoom" | "getMinZoom">
    >,
    object: DataT,
    objectInfo: AccessorContext<DataT>,
): TileEntryDatum {
    return {
        tileSize: access(props.getTileSize, object, objectInfo),
        maxZoom: access(props.getMaxZoom, object, objectInfo),
        minZoom: access(props.getMinZoom, object, objectInfo),
    };
}

// ---------------------------------------------------------------------------
// RasterOverlayLayer
// ---------------------------------------------------------------------------

export class RasterOverlayLayer<
    DataT = unknown,
    ExtraProps extends object = object,
> extends CompositeLayer<Required<_RasterOverlayLayerProps<DataT>> & ExtraProps> {
    static defaultProps = defaultProps;
    static layerName = "RasterOverlayLayer";
    static componentName = "RasterOverlayLayer";

    declare state: LayerState;

    private buildLayerState(prevWmsById: Map<string, WmsLayerEntry> = new Map()): LayerState {
        const { data } = this.props;
        const wms: WmsLayerEntry[] = [];
        const tile: TileLayerEntry[] = [];
        const { iterable, objectInfo } = createIterable(data);

        for (const object of iterable) {
            objectInfo.index++;
            const { id, tileUrl, token, opacity, isWms, time } = getCommonDatum(
                this.props,
                object,
                objectInfo,
            );

            if (isWms) {
                const { layers, version, format, transparent } = getWmsEntryDatum(
                    this.props,
                    object,
                    objectInfo,
                );
                const baseUrl = wmsBaseUrl(resolveTileUrl(tileUrl, token ?? null, null));
                const timeDate = time ? time.split("T")[0] : null;

                const wmsVersion: "1.1.1" | "1.3.0" =
                    version === "1.1.1" || version === "1.3.0" ? version : "1.1.1";
                const wmsFormat = format === "image/png" ? format : "image/png";
                const cacheKey = [
                    baseUrl,
                    wmsVersion,
                    wmsFormat,
                    String(transparent),
                    ...(timeDate ? [timeDate] : []),
                ].join("\0");
                const prev = prevWmsById.get(id);
                const imageSource =
                    prev?.cacheKey === cacheKey
                        ? prev.imageSource
                        : WMSSource.createDataSource(baseUrl, {
                              // Upstream's own _createImageSource forwards loadOptions here; our
                              // override above short-circuits that path for pre-built sources, so
                              // without this line we silently drop it (visgl/deck.gl#7747 is the
                              // same class of bug: Authorization headers never reaching WMS).
                              //
                              // Shape matters: only `{ core: { fetch: fn } }` is honoured — a
                              // top-level `{ fetch: fn }` is ignored and falls back to global
                              // fetch. Resolved once in the DataSource constructor, and
                              // loadOptions is not part of cacheKey, so swapping it later will
                              // not re-authenticate an existing source; close over a credential
                              // read at call time rather than capturing a token.
                              core: { loadOptions: this.props.loadOptions },
                              wms: {
                                  wmsParameters: {
                                      version: wmsVersion,
                                      format: wmsFormat,
                                      transparent,
                                      ...(timeDate ? { time: timeDate } : {}),
                                  },
                              },
                          });

                wms.push(
                    this.getSubLayerRow(
                        { id, imageSource, cacheKey, layers, opacity },
                        object,
                        objectInfo.index,
                    ),
                );
            } else {
                const { tileSize, maxZoom, minZoom } = getTileEntryDatum(
                    this.props,
                    object,
                    objectInfo,
                );
                tile.push(
                    this.getSubLayerRow(
                        {
                            id,
                            tileUrl: resolveTileUrl(tileUrl, token ?? null, time),
                            opacity,
                            tileSize,
                            maxZoom,
                            minZoom,
                        },
                        object,
                        objectInfo.index,
                    ),
                );
            }
        }

        return { wms, tile };
    }

    initializeState(): void {
        this.state = this.buildLayerState();
    }

    override updateState(params: UpdateParameters<this>): void {
        super.updateState(params);
        if (params.changeFlags.propsOrDataChanged) {
            const prevWmsById = new Map(this.state.wms.map((w) => [w.id, w]));
            this.setState(this.buildLayerState(prevWmsById));
        }
    }

    private renderWmsLayers(state: LayerState): Layer[] {
        const WmsSubLayer = this.getSubLayerClass("wms", WMSLayerOverride);

        return state.wms.map(
            ({ id, imageSource, layers, opacity }) =>
                new WmsSubLayer(
                    {
                        data: imageSource,
                        serviceType: "wms",
                        // EPSG:4326 matches the projection used in the previous tile-based implementation.
                        srs: "EPSG:4326",
                        layers,
                    },
                    this.getSubLayerProps({ id, opacity }),
                ),
        );
    }

    private renderTileLayers(state: LayerState): Layer[] {
        const { refinementStrategy } = this.props;
        const TileSubLayer = this.getSubLayerClass("tile", TileLayer);

        return state.tile.map(({ id, tileUrl, opacity, tileSize, maxZoom, minZoom }) => {
            type TileProps = { bbox?: GeoBoundingBox | NonGeoBoundingBox; data: unknown };
            const renderSubLayers = (props: { tile: TileProps }) => {
                const { bbox, data: tileData } = props.tile;
                if (!bbox || !isGeoBoundingBox(bbox) || tileData == null) return null;
                return new BitmapLayer({
                    ...props,
                    data: undefined,
                    image: tileData as BitmapLayerProps["image"],
                    bounds: [bbox.west, bbox.south, bbox.east, bbox.north],
                    opacity,
                });
            };

            return new TileSubLayer(
                {
                    data: tileUrl,
                    tileSize,
                    refinementStrategy,
                    debounceTime: 200,
                    ...(maxZoom != null ? { maxZoom } : {}),
                    ...(minZoom != null ? { minZoom } : {}),
                    renderSubLayers,
                },
                this.getSubLayerProps({ id, opacity }),
            );
        });
    }

    renderLayers(): Layer | null | LayersList {
        return [...this.renderWmsLayers(this.state), ...this.renderTileLayers(this.state)];
    }
}

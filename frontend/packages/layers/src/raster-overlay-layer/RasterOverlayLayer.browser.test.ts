// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { device, testLayer } from "@deck.gl/test-utils/vitest";
import { LayerManager, WebMercatorViewport } from "@deck.gl/core";
import { RasterOverlayLayer, type RasterOverlayLayerProps } from "./RasterOverlayLayer";

// ---------------------------------------------------------------------------
// Tile layer fixtures
// ---------------------------------------------------------------------------

interface TileTestData {
    id: string;
    url: string;
    opacity: number;
    time: string | null;
    token: string | null;
    tileSize?: number;
    minZoom?: number | null;
    maxZoom?: number | null;
}

const defaultTileEntry: TileTestData = {
    id: "precip",
    url: "https://example.com/precip/{Time}/{z}/{x}/{y}.png?key={key}",
    opacity: 75,
    time: null,
    token: "secret",
    minZoom: 2,
    maxZoom: 6,
    tileSize: 512,
};

const TileOverlayLayer = RasterOverlayLayer<TileTestData>;

function tileLayerProps(
    entries: TileTestData[] = [defaultTileEntry],
    extra: Partial<RasterOverlayLayerProps<TileTestData>> = {},
): RasterOverlayLayerProps<TileTestData> {
    return {
        id: "weather",
        data: entries,
        getId: (d) => d.id,
        getTile: (d) => d.url,
        getToken: (d) => d.token,
        getOpacity: (d) => d.opacity,
        getIsWms: () => false,
        getTime: (d) => d.time,
        getTileSize: (d) => d.tileSize ?? 256,
        getMinZoom: (d) => d.minZoom,
        getMaxZoom: (d) => d.maxZoom,
        ...extra,
    };
}

// ---------------------------------------------------------------------------
// Network boundary
// ---------------------------------------------------------------------------

// A mounted WMS sub-layer issues GetCapabilities immediately and GetMap on a debounced timer, so
// these tests must not be allowed to reach the network. `loadOptions` is the injection point that
// holds: loaders.gl's DataSource resolves its fetch once in the constructor, so the stub belongs
// to the source itself and still applies to any request that lands after a test finishes.
//
// A global `fetch` stub does NOT work here — the requests can outlive the test that scheduled
// them, so an afterAll restore loses the race and the request escapes.
const ONE_BY_ONE_PNG =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

/** Requests the layer issued, so tests can assert on them without touching the network. */
const requestedUrls: string[] = [];

const CAPABILITIES_XML = `<?xml version="1.0" encoding="UTF-8"?>
<WMT_MS_Capabilities version="1.1.1">
  <Service><Name>OGC:WMS</Name><Title>Test WMS</Title></Service>
  <Capability><Layer><Title>Test WMS</Title><Layer><Name>layer1</Name></Layer></Layer></Capability>
</WMT_MS_Capabilities>`;

/** Answers the two request types a mounted WMS layer makes: GetCapabilities, then GetMap. */
function stubFetch(url: string): Promise<Response> {
    requestedUrls.push(url);
    if (/request=getcapabilities/i.test(url)) {
        return Promise.resolve(
            new Response(CAPABILITIES_XML, {
                status: 200,
                headers: { "content-type": "text/xml" },
            }),
        );
    }

    const bytes = Uint8Array.from(atob(ONE_BY_ONE_PNG), (c) => c.codePointAt(0) ?? 0);
    return Promise.resolve(
        new Response(bytes, { status: 200, headers: { "content-type": "image/png" } }),
    );
}

const stubbedLoadOptions = { core: { fetch: stubFetch } };

/** Minimal shape of the loaders.gl WMS source the layer stores in state. */
interface WmsImageSourceLike {
    getImage: (parameters: {
        width: number;
        height: number;
        boundingBox: [[number, number], [number, number]];
        layers: string[];
        crs: string;
    }) => Promise<unknown>;
}

// ---------------------------------------------------------------------------
// WMS layer fixtures
// ---------------------------------------------------------------------------

interface WmsTestData {
    id: string;
    url: string;
    wmsLayers: string[];
    version: string;
    format: string;
    transparent: boolean;
    opacity: number;
    time: string | null;
    token: string | null;
}

const defaultWmsData: WmsTestData = {
    id: "radar",
    url: "https://wms.example.com/service",
    wmsLayers: ["layer1"],
    format: "image/png",
    version: "1.1.1",
    transparent: true,
    opacity: 80,
    time: null,
    token: null,
};

const WmsOverlayLayer = RasterOverlayLayer<WmsTestData>;

function wmsLayerProps(overrides: Partial<WmsTestData> = {}): RasterOverlayLayerProps<WmsTestData> {
    const entry: WmsTestData = { ...defaultWmsData, ...overrides };
    return {
        id: "overlay",
        data: [entry],
        loadOptions: stubbedLoadOptions,
        getId: (d) => d.id,
        getTile: (d) => d.url,
        getToken: (d) => d.token,
        getOpacity: (d) => d.opacity,
        getIsWms: () => true,
        getWmsLayers: (d) => d.wmsLayers,
        getWmsVersion: (d) => d.version,
        getWmsFormat: (d) => d.format,
        getWmsTransparent: (d) => d.transparent,
        getTime: (d) => d.time,
    };
}

// ---------------------------------------------------------------------------
// Tile source tests
// ---------------------------------------------------------------------------

type SubLayerProps = Record<string, unknown>;

describe("tile sources", () => {
    it("renders one TileLayer per entry", () => {
        testLayer({
            Layer: TileOverlayLayer,
            testCases: [
                {
                    title: "two entries produce two sub-layers",
                    props: tileLayerProps([
                        {
                            id: "a",
                            url: "https://example.com/a/{z}/{x}/{y}.png",
                            opacity: 50,
                            time: null,
                            token: null,
                        },
                        {
                            id: "b",
                            url: "https://example.com/b/{z}/{x}/{y}.png",
                            opacity: 25,
                            time: null,
                            token: null,
                        },
                    ]),
                    onAfterUpdate: ({ subLayers }) => {
                        expect(subLayers.map((l) => l.id)).toEqual(["weather-a", "weather-b"]);
                    },
                },
            ],
        });
    });

    it("resolves URL placeholders and normalizes opacity", () => {
        testLayer({
            Layer: TileOverlayLayer,
            testCases: [
                {
                    title: "placeholders resolved, opacity normalized",
                    props: tileLayerProps(),
                    onAfterUpdate: ({ subLayers }) => {
                        const p = subLayers[0].props as SubLayerProps;
                        expect(p.data).toBe(
                            "https://example.com/precip/default/{z}/{x}/{y}.png?key=secret",
                        );
                        expect(subLayers[0].props.opacity).toBeCloseTo(0.75);
                    },
                },
            ],
        });
    });

    it("passes zoom bounds and tile size to each TileLayer", () => {
        testLayer({
            Layer: TileOverlayLayer,
            testCases: [
                {
                    title: "zoom and tile size props forwarded",
                    props: tileLayerProps(),
                    onAfterUpdate: ({ subLayers }) => {
                        const p = subLayers[0].props as SubLayerProps;
                        expect(p.minZoom).toBe(2);
                        expect(p.maxZoom).toBe(6);
                        expect(p.tileSize).toBe(512);
                        expect(p.refinementStrategy).toBe("best-available");
                    },
                },
            ],
        });
    });

    it("gives each source an independent opacity", () => {
        testLayer({
            Layer: TileOverlayLayer,
            testCases: [
                {
                    title: "sibling opacities are independent",
                    props: tileLayerProps([
                        {
                            id: "a",
                            url: "https://example.com/a/{z}/{x}/{y}.png",
                            opacity: 25,
                            time: null,
                            token: null,
                        },
                        {
                            id: "b",
                            url: "https://example.com/b/{z}/{x}/{y}.png",
                            opacity: 75,
                            time: null,
                            token: null,
                        },
                    ]),
                    onAfterUpdate: ({ subLayers }) => {
                        expect(subLayers[0].props.opacity).toBeCloseTo(0.25);
                        expect(subLayers[1].props.opacity).toBeCloseTo(0.75);
                    },
                },
            ],
        });
    });

    it("renderSubLayers re-closes over the updated opacity when opacity changes", () => {
        testLayer({
            Layer: TileOverlayLayer,
            testCases: [
                {
                    title: "initial opacity 75%",
                    props: tileLayerProps(),
                    onAfterUpdate: ({ subLayers }) => {
                        const renderFn = (subLayers[0].props as SubLayerProps)
                            .renderSubLayers as CallableFunction;
                        const bitmap = renderFn({
                            tile: { bbox: { west: 0, south: 0, east: 1, north: 1 }, data: "img" },
                        });
                        expect(bitmap.props.opacity).toBeCloseTo(0.75);
                    },
                },
                {
                    title: "updated opacity 25%",
                    updateProps: { data: [{ ...defaultTileEntry, opacity: 25 }] },
                    onAfterUpdate: ({ subLayers }) => {
                        const renderFn = (subLayers[0].props as SubLayerProps)
                            .renderSubLayers as CallableFunction;
                        const bitmap = renderFn({
                            tile: { bbox: { west: 0, south: 0, east: 1, north: 1 }, data: "img" },
                        });
                        expect(bitmap.props.opacity).toBeCloseTo(0.25);
                    },
                },
            ],
        });
    });

    it("renderSubLayers returns a BitmapLayer with geographic bounds and correct opacity", () => {
        testLayer({
            Layer: TileOverlayLayer,
            testCases: [
                {
                    title: "geographic bounds and opacity in bitmap sublayer",
                    props: tileLayerProps(),
                    onAfterUpdate: ({ subLayers }) => {
                        const renderFn = (subLayers[0].props as SubLayerProps)
                            .renderSubLayers as CallableFunction;
                        const bitmap = renderFn({
                            tile: {
                                bbox: { west: -10, south: -5, east: 10, north: 5 },
                                data: "image-data",
                            },
                        });
                        expect(bitmap.props.bounds).toEqual([-10, -5, 10, 5]);
                        expect(bitmap.props.opacity).toBeCloseTo(0.75);
                    },
                },
            ],
        });
    });
});

// ---------------------------------------------------------------------------
// WMS source tests
// ---------------------------------------------------------------------------

describe("WMS sources", () => {
    it("produces a WMS sub-layer with serviceType wms and EPSG:4326", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "serviceType and srs",
                    props: wmsLayerProps(),
                    onAfterUpdate: ({ subLayers }) => {
                        const p = subLayers[0].props as SubLayerProps;
                        expect(p.serviceType).toBe("wms");
                        expect(p.srs).toBe("EPSG:4326");
                    },
                },
            ],
        });
    });

    it("passes an ImageSource object (not a raw URL string) as data", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "data is an object",
                    props: wmsLayerProps(),
                    onAfterUpdate: ({ subLayers }) => {
                        expect(typeof subLayers[0].props.data).toBe("object");
                        expect(typeof subLayers[0].props.data).not.toBe("string");
                    },
                },
            ],
        });
    });

    it("strips tile-specific params (BBOX, WIDTH, HEIGHT, TIME) from the imageSource URL", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "tile params stripped from imageSource URL",
                    props: wmsLayerProps({
                        url: "https://wms.example.com/service?BBOX=-10,-5,10,5&WIDTH=256&HEIGHT=256&TIME=2024-01-01",
                    }),
                    onAfterUpdate: ({ layer }) => {
                        const { imageSource } = layer.state.wms[0];
                        expect(imageSource.url).not.toContain("BBOX");
                        expect(imageSource.url).not.toContain("WIDTH");
                        expect(imageSource.url).not.toContain("HEIGHT");
                        expect(imageSource.url).not.toContain("TIME");
                    },
                },
            ],
        });
    });

    it("includes the time date in wmsParameters when time is set", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "time date in wmsParameters",
                    props: wmsLayerProps({ time: "2024-03-15T00:00:00.000Z" }),
                    onAfterUpdate: ({ layer }) => {
                        const { imageSource } = layer.state.wms[0];
                        expect(imageSource.wmsParameters.time).toBe("2024-03-15");
                    },
                },
            ],
        });
    });

    it("omits the time key from wmsParameters when time is null", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "time undefined when null",
                    props: wmsLayerProps({ time: null }),
                    onAfterUpdate: ({ layer }) => {
                        const { imageSource } = layer.state.wms[0];
                        // WMSImageSource always sets time: undefined in its defaults;
                        // the value being undefined means TIME is omitted from GetMap URLs.
                        expect(imageSource.wmsParameters.time).toBeUndefined();
                    },
                },
            ],
        });
    });

    it("passes the layers array to the WMS sub-layer", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "layers prop forwarded",
                    props: wmsLayerProps({ wmsLayers: ["radar", "precip", "wind"] }),
                    onAfterUpdate: ({ subLayers }) => {
                        const p = subLayers[0].props as SubLayerProps;
                        expect(p.layers).toEqual(["radar", "precip", "wind"]);
                    },
                },
            ],
        });
    });

    it("normalizes opacity to 0-1", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "opacity normalized",
                    props: wmsLayerProps({ opacity: 80 }),
                    onAfterUpdate: ({ subLayers }) => {
                        expect(subLayers[0].props.opacity).toBeCloseTo(0.8);
                    },
                },
            ],
        });
    });

    it("substitutes token placeholders in the imageSource URL", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "token placeholder replaced in URL",
                    props: wmsLayerProps({
                        url: "https://wms.example.com/service?key={key}",
                        token: "secret123",
                    }),
                    onAfterUpdate: ({ layer }) => {
                        const { imageSource } = layer.state.wms[0];
                        expect(imageSource.url).toContain("key=secret123");
                        expect(imageSource.url).not.toContain("{key}");
                    },
                },
            ],
        });
    });

    it("passes version, format, and transparent into wmsParameters", () => {
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "version, format, transparent in wmsParameters",
                    props: wmsLayerProps({
                        version: "1.3.0",
                        format: "image/png",
                        transparent: false,
                    }),
                    onAfterUpdate: ({ layer }) => {
                        const { imageSource } = layer.state.wms[0];
                        expect(imageSource.wmsParameters.version).toBe("1.3.0");
                        expect(imageSource.wmsParameters.format).toBe("image/png");
                        expect(imageSource.wmsParameters.transparent).toBe(false);
                    },
                },
            ],
        });
    });

    it("cancels the pending GetMap when the layer is removed", async () => {
        requestedUrls.length = 0;
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [{ title: "mount, then finalize", props: wmsLayerProps() }],
        });

        // testLayer finalizes the layer on the way out, which must cancel the pending GetMap.
        // Sync test bodies never yield, so every layer's timer stays queued until the first await
        // in the file and then flushes at once; this wait is that yield. Verified against the
        // unfixed layer, where it catches the leaked requests on every run.
        //
        // This is a negative assertion, so it cannot fail spuriously: with the timer cancelled
        // there is nothing that could make a GetMap appear, however long the suite takes.
        await new Promise((resolve) => setTimeout(resolve, 1000));

        // GetCapabilities is not debounced, so it still goes out — and its presence confirms the
        // layer really is routing through the injected fetch rather than skipping the request.
        expect(requestedUrls.some((u) => /request=getcapabilities/i.test(u))).toBe(true);
        expect(requestedUrls.filter((u) => /request=getmap/i.test(u))).toEqual([]);
    });

    it("stays silent when a GetMap already in flight fails after the layer is removed", async () => {
        // testLayer finalizes synchronously, so it cannot express "remove the layer *during* a
        // request". Drive a real LayerManager instead — dropping the layer from the list is
        // exactly what toggling the overlay off does.
        const requests: string[] = [];
        const errors: string[] = [];
        let failGetMap: ((error: Error) => void) | undefined;

        const hangingFetch = (url: string): Promise<Response> => {
            requests.push(url);
            if (/request=getcapabilities/i.test(url)) {
                return Promise.resolve(
                    new Response(CAPABILITIES_XML, {
                        status: 200,
                        headers: { "content-type": "text/xml" },
                    }),
                );
            }
            // Never settles until the test decides — the request is in flight.
            return new Promise<Response>((_resolve, reject) => {
                failGetMap = reject;
            });
        };

        const manager = new LayerManager(device, {
            viewport: new WebMercatorViewport({
                width: 100,
                height: 100,
                longitude: 0,
                latitude: 0,
                zoom: 1,
            }),
        });
        manager.setProps({ onError: (error: Error) => errors.push(error.message) });
        manager.setLayers([
            new WmsOverlayLayer({
                ...wmsLayerProps(),
                loadOptions: { core: { fetch: hangingFetch } },
            }),
        ]);
        manager.updateLayers();

        // Positive wait: resolves as soon as the debounced GetMap goes out.
        for (let i = 0; i < 100 && !requests.some((u) => /request=getmap/i.test(u)); i++) {
            await new Promise((resolve) => setTimeout(resolve, 50));
        }
        expect(requests.some((u) => /request=getmap/i.test(u))).toBe(true);

        manager.setLayers([]); // the user toggles the overlay off mid-request
        manager.updateLayers();
        failGetMap?.(new Error("Failed to fetch"));
        await new Promise((resolve) => setTimeout(resolve, 100));

        expect(errors).toEqual([]);
        manager.finalize();
    });

    it("stays silent when the requesting instance was replaced before the layer was removed", async () => {
        // deck.gl re-creates layer objects every render and transfers state to the new instance,
        // so the layer that issued the request is usually NOT the layer that gets finalized. A
        // disposed flag kept on the instance would be set on the survivor and never seen by the
        // instance whose request is still in flight.
        const requests: string[] = [];
        const errors: string[] = [];
        // One rejector per in-flight GetMap, in order — index 0 belongs to instance A.
        const pendingGetMaps: ((error: Error) => void)[] = [];

        const hangingFetch = (url: string): Promise<Response> => {
            requests.push(url);
            if (/request=getcapabilities/i.test(url)) {
                return Promise.resolve(
                    new Response(CAPABILITIES_XML, {
                        status: 200,
                        headers: { "content-type": "text/xml" },
                    }),
                );
            }
            return new Promise<Response>((_resolve, reject) => {
                pendingGetMaps.push(reject);
            });
        };

        const loadOptions = { core: { fetch: hangingFetch } };
        const manager = new LayerManager(device, {
            viewport: new WebMercatorViewport({
                width: 100,
                height: 100,
                longitude: 0,
                latitude: 0,
                zoom: 1,
            }),
        });
        manager.setProps({ onError: (error: Error) => errors.push(error.message) });

        // Instance A issues the request.
        manager.setLayers([new WmsOverlayLayer({ ...wmsLayerProps(), loadOptions })]);
        manager.updateLayers();
        for (let i = 0; i < 100 && pendingGetMaps.length === 0; i++) {
            await new Promise((resolve) => setTimeout(resolve, 50));
        }
        expect(pendingGetMaps).toHaveLength(1);

        // Re-render with a changed opacity: same id and same cacheKey, so the imageSource is
        // reused, but the sub-layer instance is rebuilt — B replaces A, and A is never finalized.
        manager.setLayers([
            new WmsOverlayLayer({ ...wmsLayerProps({ opacity: 40 }), loadOptions }),
        ]);
        manager.updateLayers();

        // The overlay is toggled off: B is the instance that gets finalized.
        manager.setLayers([]);
        manager.updateLayers();

        // A's request — the first one — is the one that fails.
        pendingGetMaps[0](new Error("Failed to fetch"));
        await new Promise((resolve) => setTimeout(resolve, 100));

        expect(errors).toEqual([]);
        manager.finalize();
    });

    it("routes GetMap through the injected fetch instead of the network", async () => {
        requestedUrls.length = 0;
        let imageSource: WmsImageSourceLike | undefined;

        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "capture the constructed image source",
                    props: wmsLayerProps(),
                    onAfterUpdate: ({ layer }) => {
                        imageSource = layer.state.wms[0].imageSource;
                    },
                },
            ],
        });

        // Drive the request directly rather than waiting on the layer's debounced timer: the
        // assertion is that the source we built talks to our fetch, and awaiting a timer would
        // make this test a race against the rest of the suite.
        await imageSource?.getImage({
            width: 256,
            height: 256,
            boundingBox: [
                [-10, -5],
                [10, 5],
            ],
            layers: ["layer1"],
            crs: "EPSG:4326",
        });

        const getMap = requestedUrls.find((u) => /request=getmap/i.test(u)) ?? "";
        expect(getMap).toContain("https://wms.example.com/service");
        expect(getMap).toContain("LAYERS=layer1");
        expect(getMap).toContain("VERSION=1.1.1");
    });

    it("reuses the same imageSource when only opacity changes", () => {
        let firstSource: unknown;
        testLayer({
            Layer: WmsOverlayLayer,
            testCases: [
                {
                    title: "initial render",
                    props: wmsLayerProps(),
                    onAfterUpdate: ({ layer }) => {
                        firstSource = layer.state.wms[0].imageSource;
                    },
                },
                {
                    title: "opacity change — same imageSource reused",
                    updateProps: { data: [{ ...defaultWmsData, opacity: 40 }] },
                    onAfterUpdate: ({ layer }) => {
                        expect(layer.state.wms[0].imageSource).toBe(firstSource);
                    },
                },
            ],
        });
    });
});

// ---------------------------------------------------------------------------
// Mixed WMS and tile sources
// ---------------------------------------------------------------------------

describe("mixed WMS and tile sources", () => {
    it("renders WMS sources before tile sources, each as the correct type", () => {
        interface MixedData {
            id: string;
            url: string;
            isWms: boolean;
            opacity: number;
            wmsLayers?: string[];
        }

        const entries: MixedData[] = [
            {
                id: "wms-radar",
                url: "https://wms.example.com/service",
                isWms: true,
                opacity: 60,
                wmsLayers: ["layer1"],
            },
            {
                id: "xyz-tiles",
                url: "https://tiles.example.com/{z}/{x}/{y}.png",
                isWms: false,
                opacity: 40,
            },
        ];

        const MixedLayer = RasterOverlayLayer<MixedData>;

        testLayer({
            Layer: MixedLayer,
            testCases: [
                {
                    title: "WMS before tile, correct ids and types",
                    props: {
                        id: "overlay",
                        data: entries,
                        loadOptions: stubbedLoadOptions,
                        getId: (d) => d.id,
                        getTile: (d) => d.url,
                        getOpacity: (d) => d.opacity,
                        getIsWms: (d) => d.isWms,
                        getWmsLayers: (d) => d.wmsLayers ?? [],
                        getTime: () => null,
                    },
                    onAfterUpdate: ({ subLayers }) => {
                        expect(subLayers).toHaveLength(2);
                        // WMS layers come first (renderWmsLayers before renderTileLayers)
                        expect(subLayers[0].id).toBe("overlay-wms-radar");
                        expect((subLayers[0].props as SubLayerProps).serviceType).toBe("wms");
                        expect(subLayers[1].id).toBe("overlay-xyz-tiles");
                        expect(subLayers[1].props.data).toBe(
                            "https://tiles.example.com/{z}/{x}/{y}.png",
                        );
                    },
                },
            ],
        });
    });
});

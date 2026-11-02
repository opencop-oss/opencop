// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { MapTile } from "@opencop/api";
import { SELECTED_TILE_KEY } from "@opencop/api";
import {
    API_BASE,
    HttpResponse,
    act,
    createTestPreferences,
    http,
    render,
    screen,
    server,
    userEvent,
    waitFor,
} from "~/test";
import { ActiveBasemapProvider, useActiveBasemapTileId } from "./ActiveBasemapContext";
import { BasemapPicker } from "./BasemapPicker";

const { setStyle, showToast } = vi.hoisted(() => ({ setStyle: vi.fn(), showToast: vi.fn() }));
vi.mock("react-map-gl/maplibre", () => ({
    useMap: () => ({ current: { getMap: () => ({ setStyle }) } }),
}));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));
// The cards are the picker's only input, so one button per row keeps this on the switch sequence.
// Like the real cards, each button reports whether its row is selected, and the panel re-enables
// pointer events under the picker's pass-through wrapper.
vi.mock("./BasemapCardPanel", () => ({
    BasemapCardPanel: ({
        rows,
        selectedRowId,
        onSelect,
    }: {
        rows: { id: string; tile: MapTile }[];
        selectedRowId: string | null;
        onSelect: (tile: MapTile) => void;
    }) => (
        <div style={{ pointerEvents: "auto" }}>
            {rows.map((row) => (
                <button
                    key={row.id}
                    type="button"
                    aria-pressed={row.id === selectedRowId}
                    onClick={() => onSelect(row.tile)}
                >
                    {row.id}
                </button>
            ))}
        </div>
    ),
}));
vi.mock("./BasemapToggleBar", () => ({ BasemapToggleBar: () => null }));

const STYLE_HOST = "https://styles.example.org";
const styleUrl = (id: string) => `${STYLE_HOST}/${id}.json`;

function vectorTile(id: string, primary = false): MapTile {
    return {
        id,
        primary,
        enabled: true,
        accessScope: "GLOBAL",
        name: id,
        description: `${id} tile`,
        url: styleUrl(id),
        token: null,
        attribution: null,
        config: {
            type: "BASEMAP",
            tile: { kind: "VECTOR", tileSize: 512, minZoom: null, maxZoom: null },
        },
    };
}

/**
 * One response from the style host: its headers arrive at once, its body waits for `release` or
 * fails on `fail`. `bodyRequested` resolves when the picker calls `json()` on that response, which
 * is where a later pick can overtake it; `bodyDone` once that read has settled either way.
 */
interface StalledStyle {
    bodyRequested: Promise<void>;
    bodyDone: Promise<void>;
    release: () => void;
    fail: () => void;
}
interface ReadSignal {
    requested: () => void;
    done: () => void;
}
/** Responses still to be handed out per style id, in request order. */
const pendingStreams = new Map<string, ReadableStream<Uint8Array>[]>();
/** Read signals per style URL, in the order the picker reads those responses. */
const readSignals = new Map<string, ReadSignal[]>();
/** Every stall made in the test, released at teardown so a failed assertion leaves no hung read. */
const stalls: StalledStyle[] = [];

function stallStyle(id: string): StalledStyle {
    let release = () => {};
    let fail = () => {};
    let requested = () => {};
    let done = () => {};
    const outcome = new Promise<string>((resolve, reject) => {
        release = () => resolve(JSON.stringify({ version: 8, name: id, sources: {}, layers: [] }));
        fail = () => reject(new Error(`style ${id} unavailable`));
    });
    const stall: StalledStyle = {
        bodyRequested: new Promise<void>((resolve) => (requested = resolve)),
        bodyDone: new Promise<void>((resolve) => (done = resolve)),
        release,
        fail,
    };
    const stream = new ReadableStream<Uint8Array>({
        async pull(controller) {
            try {
                controller.enqueue(new TextEncoder().encode(await outcome));
                controller.close();
            } catch (error) {
                controller.error(error);
            }
        },
    });
    pendingStreams.set(id, [...(pendingStreams.get(id) ?? []), stream]);
    readSignals.set(styleUrl(id), [...(readSignals.get(styleUrl(id)) ?? []), { requested, done }]);
    stalls.push(stall);
    return stall;
}

function Active() {
    const tileId = useActiveBasemapTileId();
    return <output>{tileId}</output>;
}

function mounted() {
    return (
        <ActiveBasemapProvider resolvedTileId="a">
            <Active />
            <BasemapPicker />
        </ActiveBasemapProvider>
    );
}

const styledNames = () => setStyle.mock.calls.map(([style]) => (style as { name: string }).name);
const pressed = (name: string) =>
    screen.getByRole("button", { name }).getAttribute("aria-pressed") === "true";

describe("BasemapPicker", () => {
    const patched: string[] = [];

    beforeEach(() => {
        sessionStorage.setItem(SELECTED_TILE_KEY, "a");
        patched.length = 0;
        server.use(
            http.get(`${API_BASE}/map/tiles`, () =>
                HttpResponse.json([vectorTile("a", true), vectorTile("b"), vectorTile("c")]),
            ),
            http.get(`${API_BASE}/map/tiles/:id`, ({ params }) =>
                HttpResponse.json(vectorTile(String(params.id), params.id === "a")),
            ),
            http.patch(`${API_BASE}/me/preferences`, async ({ request }) => {
                const body = (await request.json()) as { defaultTileId: string };
                patched.push(body.defaultTileId);
                return HttpResponse.json(createTestPreferences());
            }),
            // The third-party style host: a stalled response where the test asked for one.
            http.get(`${STYLE_HOST}/:id.json`, ({ params }) => {
                const id = String(params.id);
                const stream = pendingStreams.get(id)?.shift();
                if (!stream) {
                    return HttpResponse.json({ version: 8, name: id, sources: {}, layers: [] });
                }
                return new HttpResponse(stream, {
                    headers: { "Content-Type": "application/json" },
                });
            }),
        );
        // The picker's own body read is the seam, not the stream: the interceptor tees the body
        // before handing the response over, so a stream pull says nothing about who is reading.
        const readJson = Response.prototype.json;
        vi.spyOn(Response.prototype, "json").mockImplementation(function (this: Response) {
            const signal = readSignals.get(this.url)?.shift();
            signal?.requested();
            const read = readJson.call(this);
            if (signal) void read.then(signal.done, signal.done);
            return read;
        });
    });

    afterEach(() => {
        for (const stall of stalls.splice(0)) stall.release();
        pendingStreams.clear();
        readSignals.clear();
        sessionStorage.clear();
        vi.restoreAllMocks();
        vi.clearAllMocks();
    });

    it("drops a style that arrives after a later pick has completed", async () => {
        const b = stallStyle("b");
        const c = stallStyle("c");
        render(mounted());

        await userEvent.click(await screen.findByRole("button", { name: "b" }));
        await b.bodyRequested;
        await userEvent.click(screen.getByRole("button", { name: "c" }));
        await c.bodyRequested;
        c.release();
        await waitFor(() => expect(styledNames()).toEqual(["c"]));

        // b's body lands after c is already on the map.
        await act(async () => {
            b.release();
            await b.bodyDone;
        });

        expect(styledNames()).toEqual(["c"]);
        expect(sessionStorage.getItem(SELECTED_TILE_KEY)).toBe("c");
        expect(screen.getByRole("status")).toHaveTextContent("c");
        expect(pressed("c")).toBe(true);
        await waitFor(() => expect(patched).toEqual(["c"]));
    });

    it("lets a retry of the same tile win over its failed first attempt", async () => {
        const first = stallStyle("b");
        const retry = stallStyle("b");
        render(mounted());

        await userEvent.click(await screen.findByRole("button", { name: "b" }));
        await first.bodyRequested;
        await userEvent.click(screen.getByRole("button", { name: "b" }));
        await retry.bodyRequested;

        // The first attempt fails while the retry is still pending, then the retry lands.
        await act(async () => {
            first.fail();
            await first.bodyDone;
        });
        retry.release();
        await waitFor(() => expect(styledNames()).toEqual(["b"]));

        expect(sessionStorage.getItem(SELECTED_TILE_KEY)).toBe("b");
        expect(screen.getByRole("status")).toHaveTextContent("b");
        expect(pressed("b")).toBe(true);
        expect(showToast).not.toHaveBeenCalled();
        await waitFor(() => expect(patched).toEqual(["b"]));
    });

    it("keeps the committed selection when a switch fails", async () => {
        server.use(
            http.get(`${API_BASE}/map/tiles/b`, () =>
                HttpResponse.json({ message: "unavailable" }, { status: 503 }),
            ),
        );
        render(mounted());

        await userEvent.click(await screen.findByRole("button", { name: "b" }));
        await waitFor(() => expect(showToast).toHaveBeenCalledTimes(1));

        expect(setStyle).not.toHaveBeenCalled();
        expect(sessionStorage.getItem(SELECTED_TILE_KEY)).toBe("a");
        expect(screen.getByRole("status")).toHaveTextContent("a");
        expect(pressed("a")).toBe(true);
        expect(patched).toEqual([]);
    });
});

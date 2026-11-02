// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, server, http, HttpResponse, API_BASE } from "~/test";
import { describe, expect, it, vi, beforeEach } from "vitest";

const mockIsSupported = vi.fn();
const { hlsConfigs } = vi.hoisted(() => ({ hlsConfigs: [] as unknown[] }));

// hls.js is a system boundary (media engine) — stub it. spin-delay is a timing
// util whose 500ms delay would make the loading assertions non-deterministic —
// stub it too so tests drive `showSpinner` directly. Neither is a
// framework-library or API-client mock, so both stay.
vi.mock("hls.js", () => ({
    default: class MockHls {
        static isSupported() {
            return mockIsSupported();
        }
        static Events = { MANIFEST_PARSED: "hlsManifestParsed", ERROR: "hlsError" };
        constructor(config?: unknown) {
            hlsConfigs.push(config);
        }
        loadSource = vi.fn();
        attachMedia = vi.fn();
        on = vi.fn();
        destroy = vi.fn();
    },
}));

const mockUseSpinDelay = vi.fn();
vi.mock("spin-delay", () => ({
    useSpinDelay: (...args: unknown[]) => mockUseSpinDelay(...args),
}));

import { HlsPlayer } from "./HlsPlayer";

const RTSP_URL = "rtsp://host/stream";
const RTSPS_URL = "rtsps://host/stream";
const SRT_URL = "srt://host:9000?streamid=compass";
const HLS_URL = "https://cdn.example.com/stream.m3u8";

// POST /events/:sourceId/:objectId/attachments/:attachmentId/stream/prepare
const prepareUrl = `${API_BASE}/events/src-1/obj-1/attachments/att-1/stream/prepare`;

describe("HlsPlayer", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        hlsConfigs.length = 0;
        mockIsSupported.mockReturnValue(false);
        // happy-dom's canPlayType returns "" (unsupported) — no override needed.
        mockUseSpinDelay.mockImplementation((loading: boolean) => loading);
    });

    describe("RTSP stream conversion", () => {
        it("shows a spinner while converting an rtsp:// URL", () => {
            // The query is still pending at the synchronous assert below (its fetch
            // resolves in a later microtask), so isLoading is true → spinner. We rely
            // on the pass-through spin-delay mock rather than forcing it true, which
            // would also trip the harness's auth loading screen.
            server.use(http.post(prepareUrl, () => HttpResponse.json({ hlsUrl: HLS_URL })));
            render(
                <HlsPlayer
                    url={RTSP_URL}
                    streamKind="RTSP"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            expect(screen.getByText("Fetching stream feed...")).toBeInTheDocument();
        });

        it("shows a spinner while converting an rtsps:// URL", () => {
            server.use(http.post(prepareUrl, () => HttpResponse.json({ hlsUrl: HLS_URL })));
            render(
                <HlsPlayer
                    url={RTSPS_URL}
                    streamKind="RTSP"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            expect(screen.getByText("Fetching stream feed...")).toBeInTheDocument();
        });

        it("blames the server, not the URL, when the conversion query fails", async () => {
            server.use(http.post(prepareUrl, () => new HttpResponse(null, { status: 500 })));
            render(
                <HlsPlayer
                    url={RTSP_URL}
                    streamKind="RTSP"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            expect(
                await screen.findByText(
                    "Could not start this stream on the server. Try again shortly.",
                ),
            ).toBeInTheDocument();
        });

        it("blames the server when the conversion response contains no hlsUrl", async () => {
            server.use(http.post(prepareUrl, () => HttpResponse.json({})));
            render(
                <HlsPlayer
                    url={RTSP_URL}
                    streamKind="RTSP"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            expect(
                await screen.findByText(
                    "Could not start this stream on the server. Try again shortly.",
                ),
            ).toBeInTheDocument();
        });

        it("renders the video element after a successful conversion", async () => {
            server.use(http.post(prepareUrl, () => HttpResponse.json({ hlsUrl: HLS_URL })));
            render(
                <HlsPlayer
                    url={RTSP_URL}
                    streamKind="RTSP"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            await waitFor(() => expect(document.querySelector("video")).not.toBeNull());
        });
    });

    describe("SRT stream conversion", () => {
        it("converts an srt:// URL through the same prepare request as RTSP", async () => {
            let prepared = 0;
            server.use(
                http.post(prepareUrl, () => {
                    prepared += 1;
                    return HttpResponse.json({ hlsUrl: HLS_URL });
                }),
            );
            render(
                <HlsPlayer
                    url={SRT_URL}
                    streamKind="SRT"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            expect(screen.getByText("Fetching stream feed...")).toBeInTheDocument();
            await waitFor(() => expect(document.querySelector("video")).not.toBeNull());
            expect(prepared).toBe(1);
        });
    });

    describe("hls.js manifest policy", () => {
        it("keeps hls.js from giving up before the converted-lane timeout on RTSP and SRT", async () => {
            mockIsSupported.mockReturnValue(true);
            server.use(http.post(prepareUrl, () => HttpResponse.json({ hlsUrl: HLS_URL })));
            render(
                <HlsPlayer
                    url={RTSP_URL}
                    streamKind="RTSP"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            await waitFor(() => expect(hlsConfigs).toHaveLength(1));
            expect(hlsConfigs[0]).toMatchObject({
                manifestLoadPolicy: {
                    default: { maxLoadTimeMs: 100_000, timeoutRetry: null },
                },
            });
        });

        it("leaves hls.js defaults for a direct HLS stream", async () => {
            mockIsSupported.mockReturnValue(true);
            render(
                <HlsPlayer
                    url={HLS_URL}
                    streamKind="HLS"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            await waitFor(() => expect(hlsConfigs).toHaveLength(1));
            expect(hlsConfigs[0]).toBeUndefined();
        });
    });

    describe("HLS stream kind", () => {
        it("renders the video element directly without a conversion request", () => {
            // No stream/prepare handler is registered: if the component fired the
            // conversion query for a direct HLS URL, MSW would fail the test with an
            // unmocked-request error. Passing proves the query stayed disabled.
            render(
                <HlsPlayer
                    url={HLS_URL}
                    streamKind="HLS"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            expect(document.querySelector("video")).not.toBeNull();
        });
    });

    describe("VideoPlayer — unsupported browser fallback (Critical fix)", () => {
        it("shows stream unavailable when neither HLS.js nor native HLS is supported", async () => {
            // mockIsSupported returns false (set in beforeEach); happy-dom's
            // canPlayType returns "" by default, so the else branch fires.
            render(
                <HlsPlayer
                    url={HLS_URL}
                    streamKind="HLS"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            // The offline text also lives in the aria-live status span; scope to the visible copy.
            expect(
                await screen.findByText(
                    "Stream unavailable — check the URL points at a reachable stream.",
                    { selector: "p" },
                ),
            ).toBeInTheDocument();
        });
    });

    describe("VideoPlayer — layout (Critical fix)", () => {
        it("wraps the video element in a container with position: relative", () => {
            // position: relative keeps the absolutely-positioned loading video from
            // escaping the component's layout boundary.
            render(
                <HlsPlayer
                    url={HLS_URL}
                    streamKind="HLS"
                    sourceId="src-1"
                    objectId="obj-1"
                    attachmentId="att-1"
                />,
            );
            const video = document.querySelector("video");
            expect(video).not.toBeNull();
            const container = video?.parentElement;
            expect(container && getComputedStyle(container).position).toBe("relative");
        });
    });
});

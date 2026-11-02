// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, act } from "~/test";
import { describe, expect, it, vi, beforeEach } from "vitest";

const { mockIsSupported, players, playResult, createPlayer, loggingConfigs } = vi.hoisted(() => {
    const created: MockPlayer[] = [];
    // What the next player's play() returns; tests set `.current` before rendering.
    const playResult: { current: Promise<void> } = { current: Promise.resolve() };
    class MockPlayer {
        handlers = new Map<string, () => void>();
        attachMediaElement = vi.fn();
        load = vi.fn();
        play = vi.fn(() => playResult.current);
        pause = vi.fn();
        unload = vi.fn();
        detachMediaElement = vi.fn();
        destroy = vi.fn();
        on = vi.fn((event: string, handler: () => void) => {
            this.handlers.set(event, handler);
        });
    }
    return {
        mockIsSupported: vi.fn(),
        players: created,
        playResult,
        // Recorded outside vi.fn so clearAllMocks can't erase the call made when the module loads.
        loggingConfigs: [] as unknown[],
        createPlayer: vi.fn<(source: unknown, config?: unknown) => MockPlayer>(() => {
            const player = new MockPlayer();
            created.push(player);
            return player;
        }),
    };
});

// mpegts.js is the media engine, a system boundary — stub it like hls.js in HlsPlayer.test.tsx.
vi.mock("mpegts.js", () => ({
    default: {
        isSupported: () => mockIsSupported(),
        createPlayer,
        Events: { ERROR: "error" },
        LoggingControl: {
            getConfig: () => ({ enableWarn: true, enableError: true, globalTag: "mpegts.js" }),
            applyConfig: (config: unknown) => {
                loggingConfigs.push(config);
            },
        },
    },
}));

import { MpegTsPlayer } from "./MpegTsPlayer";

const TS_URL = "https://cam.example.com/feed.ts";
const OFFLINE_TEXT = "Stream unavailable — check the URL points at a reachable stream.";

function setProtocol(protocol: string) {
    Object.defineProperty(window.location, "protocol", { value: protocol, configurable: true });
}

describe("MpegTsPlayer", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        players.length = 0;
        mockIsSupported.mockReturnValue(true);
        playResult.current = Promise.resolve();
    });

    it("attaches a live player to the video element and starts it", () => {
        render(<MpegTsPlayer url={TS_URL} />);

        expect(createPlayer).toHaveBeenCalledWith(
            { type: "mpegts", isLive: true, url: TS_URL },
            expect.objectContaining({ autoCleanupSourceBuffer: true }),
        );
        const [player] = players;
        expect(player.attachMediaElement).toHaveBeenCalledWith(document.querySelector("video"));
        expect(player.load).toHaveBeenCalled();
        expect(player.play).toHaveBeenCalled();
        expect(screen.getByText("Fetching stream feed...")).toBeInTheDocument();
    });

    it("tears the player down in order when unmounted", () => {
        const { unmount } = render(<MpegTsPlayer url={TS_URL} />);
        const [player] = players;
        const calls: string[] = [];
        player.pause.mockImplementation(() => void calls.push("pause"));
        player.unload.mockImplementation(() => void calls.push("unload"));
        player.detachMediaElement.mockImplementation(() => void calls.push("detach"));
        player.destroy.mockImplementation(() => void calls.push("destroy"));

        unmount();

        expect(calls).toEqual(["pause", "unload", "detach", "destroy"]);
    });

    it("shows the video once it is playing", () => {
        render(<MpegTsPlayer url={TS_URL} />);

        act(() => {
            document.querySelector("video")?.dispatchEvent(new Event("playing"));
        });

        expect(screen.queryByText("Fetching stream feed...")).not.toBeInTheDocument();
        expect(screen.queryByText(OFFLINE_TEXT, { selector: "p" })).not.toBeInTheDocument();
    });

    it("goes offline when the player reports an error", () => {
        render(<MpegTsPlayer url={TS_URL} />);

        act(() => players[0].handlers.get("error")?.());

        expect(screen.getByText(OFFLINE_TEXT, { selector: "p" })).toBeInTheDocument();
    });

    it("goes offline when play() is rejected", async () => {
        playResult.current = Promise.reject(new Error("blocked"));

        render(<MpegTsPlayer url={TS_URL} />);

        expect(await screen.findByText(OFFLINE_TEXT, { selector: "p" })).toBeInTheDocument();
    });

    it("does not go offline when play() rejects with an AbortError", async () => {
        const aborted = Promise.reject(new DOMException("interrupted", "AbortError"));
        playResult.current = aborted;

        render(<MpegTsPlayer url={TS_URL} />);
        await aborted.catch(() => undefined);

        expect(screen.queryByText(OFFLINE_TEXT, { selector: "p" })).not.toBeInTheDocument();
        expect(screen.getByText("Fetching stream feed...")).toBeInTheDocument();
    });

    it("shows the offline state without creating a player when MSE is unsupported", () => {
        mockIsSupported.mockReturnValue(false);

        render(<MpegTsPlayer url={TS_URL} />);

        expect(createPlayer).not.toHaveBeenCalled();
        expect(screen.getByText(OFFLINE_TEXT, { selector: "p" })).toBeInTheDocument();
    });

    it("blocks an http:// stream on an https page without creating a player", () => {
        const original = window.location.protocol;
        setProtocol("https:");
        try {
            render(<MpegTsPlayer url="http://cam.example.com/feed.ts" />);

            expect(
                screen.getByText("Can't be shown: this feed uses http:// on a secure page."),
            ).toBeInTheDocument();
            expect(createPlayer).not.toHaveBeenCalled();
            expect(document.querySelector("video")).toBeNull();
        } finally {
            setProtocol(original);
        }
    });

    it("gives the video an accessible name", () => {
        render(<MpegTsPlayer url={TS_URL} />);

        expect(document.querySelector("video")).toHaveAttribute("aria-label", "Live Stream");
    });

    it("keeps mpegts.js to warnings and errors", () => {
        expect(loggingConfigs[0]).toMatchObject({
            enableWarn: true,
            enableError: true,
            enableVerbose: false,
            enableDebug: false,
            enableInfo: false,
        });
    });

    it("closes native Picture-in-Picture with the player rather than leave a frozen window", () => {
        const exitPip = vi.fn(() => Promise.resolve());
        const { unmount } = render(<MpegTsPlayer url={TS_URL} />);
        const video = document.querySelector("video");
        Object.defineProperty(document, "pictureInPictureElement", {
            value: video,
            configurable: true,
        });
        Object.defineProperty(document, "exitPictureInPicture", {
            value: exitPip,
            configurable: true,
        });
        try {
            unmount();

            expect(exitPip).toHaveBeenCalled();
        } finally {
            Reflect.deleteProperty(document, "pictureInPictureElement");
            Reflect.deleteProperty(document, "exitPictureInPicture");
        }
    });

    it("keeps the native Picture-in-Picture control available", () => {
        render(<MpegTsPlayer url={TS_URL} />);

        expect(document.querySelector("video")).not.toHaveAttribute("disablepictureinpicture");
    });

    it("says it is still connecting when loading runs long, then gives up", () => {
        vi.useFakeTimers();
        try {
            render(<MpegTsPlayer url={TS_URL} />);
            expect(screen.getByText("Fetching stream feed...")).toBeInTheDocument();

            act(() => {
                vi.advanceTimersByTime(10_000);
            });
            expect(
                screen.getByText("Still connecting — this can take a while.", { selector: "p" }),
            ).toBeInTheDocument();
            expect(screen.queryByText(OFFLINE_TEXT, { selector: "p" })).not.toBeInTheDocument();

            act(() => {
                vi.advanceTimersByTime(20_000);
            });
            expect(screen.getByText(OFFLINE_TEXT, { selector: "p" })).toBeInTheDocument();
        } finally {
            vi.useRealTimers();
        }
    });
});

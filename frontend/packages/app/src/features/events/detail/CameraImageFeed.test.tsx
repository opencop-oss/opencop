// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor } from "~/test";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { fireEvent } from "@testing-library/react";

// spin-delay's 500ms delay would make the loading assertions non-deterministic —
// stub it so tests drive the loading state directly, matching HlsPlayer.test.tsx.
const mockUseSpinDelay = vi.fn();
vi.mock("spin-delay", () => ({
    useSpinDelay: (...args: unknown[]) => mockUseSpinDelay(...args),
}));

import { CameraImageFeed } from "./CameraImageFeed";

const CAMERA_URL = "http://10.0.4.7/mjpg/video.cgi";
const OFFLINE_TEXT = "Stream unavailable — check the URL points at a reachable stream.";
const STALE_TEXT = "Refresh delayed — image may be stale.";
// RECONNECT_INTERVAL_MS + STALE_GRACE_MS
const STALE_THRESHOLD_MS = 25_000;

function images() {
    return Array.from(document.querySelectorAll<HTMLImageElement>("img"));
}

/**
 * The one currently shown to the operator. Reads the component's own state attribute rather than
 * computed styling — Chakra emits CSS classes, not inline styles.
 */
function visibleImage() {
    return images().find((img) => img.dataset.slotState === "active");
}

function standbyImage() {
    return images().find((img) => img.dataset.slotState === "standby");
}

describe("CameraImageFeed", () => {
    beforeEach(() => {
        mockUseSpinDelay.mockImplementation((loading: boolean) => loading);
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("renders an img pointing at the camera URL", () => {
        render(<CameraImageFeed url={CAMERA_URL} />);
        expect(images()[0]?.getAttribute("src")).toContain(CAMERA_URL);
    });

    it("cache-busts the URL so a live feed is never served from cache", () => {
        render(<CameraImageFeed url={CAMERA_URL} />);
        expect(images()[0]?.getAttribute("src")).toMatch(/[?&]_ocr=\d+/);
    });

    it("appends the cache-buster with & when the URL already has a query string", () => {
        render(<CameraImageFeed url={`${CAMERA_URL}?resolution=640x480`} />);
        expect(images()[0]?.getAttribute("src")).toContain("?resolution=640x480&_ocr=");
    });

    it("inserts the cache-buster before the #fragment rather than after it", () => {
        render(<CameraImageFeed url={`${CAMERA_URL}#live`} />);
        const src = images()[0]?.getAttribute("src") ?? "";
        expect(src).toMatch(/\?_ocr=\d+#live$/);
    });

    it("does not re-encode an already-encoded query value", () => {
        render(<CameraImageFeed url={`${CAMERA_URL}?sig=a%2Fb`} />);
        expect(images()[0]?.getAttribute("src")).toContain("?sig=a%2Fb&_ocr=");
    });

    it("issues no stream/prepare request", () => {
        // No MSW handler is registered for stream/prepare: if the component fired the
        // conversion query, MSW would fail the test with an unmocked-request error.
        render(<CameraImageFeed url={CAMERA_URL} />);
        expect(images()).toHaveLength(1);
    });

    it("shows the loading state until the first frame arrives", () => {
        render(<CameraImageFeed url={CAMERA_URL} />);
        expect(screen.getByText("Fetching stream feed...")).toBeInTheDocument();
    });

    it("reveals the feed once a frame loads", async () => {
        render(<CameraImageFeed url={CAMERA_URL} />);
        fireEvent.load(images()[0]!);
        await waitFor(() => expect(visibleImage()).toBeDefined());
    });

    it("gives up after the load timeout when no frame ever arrives", async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        render(<CameraImageFeed url={CAMERA_URL} />);
        await vi.advanceTimersByTimeAsync(10_000);
        // The offline text also lives in the aria-live status span; scope to the visible copy.
        expect(await screen.findByText(OFFLINE_TEXT, { selector: "p" })).toBeInTheDocument();
    });

    describe("reconnect", () => {
        it("opens a second connection before the first one is dropped", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);

            await vi.advanceTimersByTimeAsync(15_000);

            // Both are live: the old one keeps painting while the replacement warms up.
            await waitFor(() => expect(images()).toHaveLength(2));
            expect(visibleImage()).toBe(images()[0]);
        });

        it("swaps to the replacement only once it has a frame, then drops the old one", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);
            await vi.advanceTimersByTimeAsync(15_000);
            await waitFor(() => expect(images()).toHaveLength(2));

            fireEvent.load(images()[1]!);

            await waitFor(() => expect(images()).toHaveLength(1));
            expect(visibleImage()).toBeDefined();
            expect(screen.queryByText(OFFLINE_TEXT)).not.toBeInTheDocument();
        });

        it("keeps showing the current feed when a reconnect attempt fails", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);
            await vi.advanceTimersByTimeAsync(15_000);
            await waitFor(() => expect(images()).toHaveLength(2));

            fireEvent.error(images()[1]!);

            await waitFor(() => expect(images()).toHaveLength(1));
            expect(visibleImage()).toBeDefined();
            expect(screen.queryByText(OFFLINE_TEXT)).not.toBeInTheDocument();
        });

        it("keeps showing the frozen last frame and starts a reconnect when the active feed errors", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);

            fireEvent.error(images()[0]!);

            // The active slot is never unmounted on its own error — it keeps rendering its last
            // decoded frame while a standby reconnect starts immediately in the background.
            expect(visibleImage()).toBe(images()[0]);
            expect(screen.queryByText(OFFLINE_TEXT)).not.toBeInTheDocument();
            await waitFor(() => expect(images()).toHaveLength(2));
        });

        it("does not restart an already-pending reconnect when the active feed errors again", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);
            fireEvent.error(images()[0]!);
            await waitFor(() => expect(images()).toHaveLength(2));
            const pendingStandby = images()[1]!;

            fireEvent.error(images()[0]!);

            // A second active-slot error must not discard the reconnect already under way.
            expect(images()[1]).toBe(pendingStandby);
        });

        it("goes offline after the active feed fails once and recovery keeps getting refused", async () => {
            // An <img> errors at most once per src, so the active slot can only ever fail once —
            // every subsequent attempt to recover happens on the standby slot instead. A camera
            // that stays down must still be detected through those refused recovery attempts.
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);

            fireEvent.error(images()[0]!);
            await waitFor(() => expect(images()).toHaveLength(2));
            fireEvent.error(standbyImage()!); // immediate recovery attempt refused
            await waitFor(() => expect(images()).toHaveLength(1));

            await vi.advanceTimersByTimeAsync(15_000);
            await waitFor(() => expect(images()).toHaveLength(2));
            fireEvent.error(standbyImage()!); // next scheduled reconnect refused

            expect(await screen.findByText(OFFLINE_TEXT, { selector: "p" })).toBeInTheDocument();
        });

        it("never goes offline no matter how many times the standby reconnect fails", async () => {
            // A camera that only accepts one concurrent connection fails every standby
            // reconnect forever — that says nothing about the still-healthy active feed.
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);

            for (let attempt = 0; attempt < 5; attempt++) {
                await vi.advanceTimersByTimeAsync(15_000);
                await waitFor(() => expect(images()).toHaveLength(2));
                fireEvent.error(images()[1]!);
                await waitFor(() => expect(images()).toHaveLength(1));
            }

            expect(visibleImage()).toBe(images()[0]);
            expect(screen.queryByText(OFFLINE_TEXT)).not.toBeInTheDocument();
        });

        it("ignores the error fired by clearing a src during teardown", () => {
            const { unmount } = render(<CameraImageFeed url={CAMERA_URL} />);
            const img = images()[0]!;
            fireEvent.load(img);

            // Mirrors what the unmount cleanup does before the browser fires `error`.
            img.removeAttribute("src");
            fireEvent.error(img);

            expect(screen.queryByText(OFFLINE_TEXT)).not.toBeInTheDocument();
            unmount();
        });
    });

    it("clears the src on unmount so the MJPEG connection closes", () => {
        const { unmount } = render(<CameraImageFeed url={CAMERA_URL} />);
        const img = images()[0]!;
        unmount();
        expect(img.getAttribute("src")).toBeNull();
    });

    describe("staleness", () => {
        it("flags the image as possibly stale when no handover lands within the reconnect cycle plus grace period", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);

            // The standby appears at 15s and never loads or errors — the silent-hang case.
            await vi.advanceTimersByTimeAsync(STALE_THRESHOLD_MS);

            expect(await screen.findByText(STALE_TEXT, { selector: "p" })).toBeInTheDocument();
            // The image itself is never pulled — only qualified.
            expect(visibleImage()).toBe(images()[0]);
        });

        it("clears the stale warning once a real handover succeeds", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);
            await vi.advanceTimersByTimeAsync(STALE_THRESHOLD_MS);
            expect(await screen.findByText(STALE_TEXT, { selector: "p" })).toBeInTheDocument();

            fireEvent.load(standbyImage()!);

            await waitFor(() =>
                expect(screen.queryByText(STALE_TEXT, { selector: "p" })).not.toBeInTheDocument(),
            );
        });

        it("does not let a failed standby attempt buy more time before the warning shows", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);

            await vi.advanceTimersByTimeAsync(15_000);
            await waitFor(() => expect(images()).toHaveLength(2));
            fireEvent.error(standbyImage()!);
            await waitFor(() => expect(images()).toHaveLength(1));

            // Still measured from the original load, not from the failed retry.
            await vi.advanceTimersByTimeAsync(STALE_THRESHOLD_MS - 15_000);
            expect(await screen.findByText(STALE_TEXT, { selector: "p" })).toBeInTheDocument();
        });

        it("does not let a repeat load on the active slot reset the staleness clock", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);

            await vi.advanceTimersByTimeAsync(STALE_THRESHOLD_MS - 5_000);
            fireEvent.load(images()[0]!);
            await vi.advanceTimersByTimeAsync(5_000);

            expect(await screen.findByText(STALE_TEXT, { selector: "p" })).toBeInTheDocument();
        });

        it("never shows the warning while handovers keep succeeding on schedule", async () => {
            vi.useFakeTimers({ shouldAdvanceTime: true });
            render(<CameraImageFeed url={CAMERA_URL} />);
            fireEvent.load(images()[0]!);

            for (let cycle = 0; cycle < 2; cycle++) {
                await vi.advanceTimersByTimeAsync(15_000);
                await waitFor(() => expect(images()).toHaveLength(2));
                fireEvent.load(standbyImage()!);
                await waitFor(() => expect(images()).toHaveLength(1));
            }

            expect(screen.queryByText(STALE_TEXT)).not.toBeInTheDocument();
        });
    });

    it("reports mixed content specifically when the page is https and the feed is http", () => {
        const original = window.location.protocol;
        Object.defineProperty(window.location, "protocol", {
            value: "https:",
            configurable: true,
        });
        try {
            render(<CameraImageFeed url={CAMERA_URL} />);
            expect(
                screen.getByText("Can't be shown: this feed uses http:// on a secure page."),
            ).toBeInTheDocument();
            expect(images()).toHaveLength(0);
        } finally {
            Object.defineProperty(window.location, "protocol", {
                value: original,
                configurable: true,
            });
        }
    });
});

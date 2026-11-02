// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, userEvent } from "~/test";
import { describe, expect, it, vi } from "vitest";
import type { UrlAttachment } from "@opencop/api";

// A chunk that fails to load (stale after a deploy, flaky network) makes the lazy import reject.
vi.mock("./MpegTsPlayer", () => {
    throw new Error("Failed to fetch dynamically imported module");
});
vi.mock("./HlsPlayer", () => ({ HlsPlayer: () => null }));
vi.mock("./CameraImageFeed", () => ({ CameraImageFeed: () => null }));

import { UrlAttachmentPlayer } from "./UrlAttachmentPlayer";

const attachment: UrlAttachment = {
    kind: "URL",
    record: {
        id: "att-1",
        url: "https://cam.example.com/feed.ts",
        streamKind: "MPEGTS",
        access: "LOGGED_IN",
        creatorId: "user-1",
        ownerId: null,
        createdAt: "",
        usage: "EVENT_DETAIL",
    },
};

describe("UrlAttachmentPlayer when the MPEG-TS chunk fails to load", () => {
    it("contains the failure to the stream instead of taking the surrounding panel down", async () => {
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
        try {
            render(
                <div>
                    <p data-testid="sibling" />
                    <UrlAttachmentPlayer
                        attachment={attachment}
                        sourceId="src-1"
                        objectId="obj-1"
                    />
                </div>,
            );

            expect(await screen.findByText(/MpegTsPlayer/)).toBeInTheDocument();
            expect(screen.getByTestId("sibling")).toBeInTheDocument();
        } finally {
            consoleError.mockRestore();
        }
    });

    it("reloads the page on Retry, since a rejected lazy import is cached and a stale chunk needs a reload", async () => {
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
        const reload = vi.fn();
        vi.stubGlobal("location", { ...window.location, reload });
        try {
            render(
                <UrlAttachmentPlayer attachment={attachment} sourceId="src-1" objectId="obj-1" />,
            );

            await userEvent.click(await screen.findByRole("button", { name: /retry/i }));

            expect(reload).toHaveBeenCalled();
        } finally {
            vi.unstubAllGlobals();
            consoleError.mockRestore();
        }
    });
});

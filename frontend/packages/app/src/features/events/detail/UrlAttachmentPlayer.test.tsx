// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { describe, expect, it, vi } from "vitest";
import type { StreamKind, UrlAttachment } from "@opencop/api";

// The players are system-boundary-heavy; this test is only about which one is chosen.
vi.mock("./HlsPlayer", () => ({
    HlsPlayer: ({ streamKind, url }: { streamKind: string; url: string }) => (
        <div data-testid="hls-player">{`${streamKind} ${url}`}</div>
    ),
}));
vi.mock("./CameraImageFeed", () => ({
    CameraImageFeed: ({ url }: { url: string }) => <div data-testid="camera-feed">{url}</div>,
}));
vi.mock("./MpegTsPlayer", () => ({
    MpegTsPlayer: ({ url }: { url: string }) => <div data-testid="mpegts-player">{url}</div>,
}));

import { UrlAttachmentPlayer } from "./UrlAttachmentPlayer";

function attachment(streamKind: StreamKind, url: string): UrlAttachment {
    return {
        kind: "URL",
        record: {
            id: "att-1",
            url,
            streamKind,
            access: "LOGGED_IN",
            creatorId: "user-1",
            ownerId: null,
            createdAt: "",
            usage: "EVENT_DETAIL",
        },
    };
}

function renderPlayer(streamKind: StreamKind, url: string) {
    return render(
        <UrlAttachmentPlayer
            attachment={attachment(streamKind, url)}
            sourceId="src-1"
            objectId="obj-1"
        />,
    );
}

describe("UrlAttachmentPlayer", () => {
    it.each<[StreamKind, string]>([
        ["RTSP", "rtsp://cam/stream"],
        ["SRT", "srt://relay:9000?streamid=a"],
        ["HLS", "https://cdn/live.m3u8"],
    ])("sends %s to the HLS player", (kind, url) => {
        renderPlayer(kind, url);

        expect(screen.getByTestId("hls-player")).toHaveTextContent(`${kind} ${url}`);
    });

    it("sends MJPEG to the camera feed", () => {
        renderPlayer("MJPEG", "http://cam/mjpg");

        expect(screen.getByTestId("camera-feed")).toBeInTheDocument();
    });

    it("sends MPEGTS to the lazily loaded MPEG-TS player", async () => {
        renderPlayer("MPEGTS", "https://cam/feed.ts");

        expect(await screen.findByTestId("mpegts-player")).toHaveTextContent("https://cam/feed.ts");
    });

    it("shows an unsupported message instead of throwing for a kind this build doesn't know", () => {
        renderPlayer("QUIC" as StreamKind, "quic://relay");

        expect(
            screen.getByText(
                "This stream type isn't supported by this version of the app. Refresh to update.",
            ),
        ).toBeInTheDocument();
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { StreamKind, UrlAttachmentRecord } from "@opencop/api";
import {
    inferStreamKind,
    isBlockedMixedContent,
    isSourceOwnedAttachment,
    streamKindName,
    streamUrlTitle,
    urlAttachmentTitle,
} from "./attachment.util";

describe("inferStreamKind", () => {
    it.each([
        ["rtsp://camera.local/stream", "RTSP"],
        ["rtsps://camera.local/stream", "RTSP"],
        ["RTSP://camera.local/stream", "RTSP"],
        ["  rtsp://camera.local/stream  ", "RTSP"],
        ["https://cdn.example.com/live.m3u8", "HLS"],
        ["https://cdn.example.com/live.m3u8?token=abc", "HLS"],
        ["srt://relay.local:9000", "SRT"],
        ["SRT://relay.local:9000?streamid=compass&latency=200", "SRT"],
        ["https://cdn.example.com/feed.ts", "MPEGTS"],
        ["https://cdn.example.com/FEED.TS?token=abc", "MPEGTS"],
        ["http://10.0.4.7/mjpg/video.cgi", "MJPEG"],
        ["http://10.0.4.7/axis-cgi/mjpg/video.cgi?resolution=640x480", "MJPEG"],
        ["https://cam.example.com/snapshot.jpg", "MJPEG"],
    ])("maps %s to %s", (url, expected) => {
        expect(inferStreamKind(url)).toBe(expected);
    });

    it.each(["", "   ", "camera.local/stream", "ftp://host/file", "javascript:alert(1)"])(
        "returns null for %s",
        (url) => {
            expect(inferStreamKind(url)).toBeNull();
        },
    );

    it("does not treat an .m3u8 substring outside the path end as HLS", () => {
        expect(inferStreamKind("http://host/m3u8-player/index.html")).toBe("MJPEG");
    });
});

describe("streamUrlTitle", () => {
    it.each([
        ["srt://relay.example.com:9000?streamid=compass&latency=200", "srt://relay.example.com"],
        ["srt://relay.example.com:9000", "srt://relay.example.com"],
        ["rtsp://camera.local:554/stream/main", "rtsp://camera.local"],
        ["rtsp://camera.local/stream/main", "rtsp://camera.local"],
        ["https://cdn.example.com/live.m3u8?token=abc", "https://cdn.example.com"],
        [
            "https://compass.tfn.pro/stream/3115e3165e899a7525dbc3900d5003dfebd06ceafb67af64e7b37bd1",
            "https://compass.tfn.pro",
        ],
        ["http://10.0.4.7:8080/mjpg/video.cgi", "http://10.0.4.7"],
        ["https://cdn.example.com?t=0", "https://cdn.example.com"],
        ["https://cdn.example.com#t=0", "https://cdn.example.com"],
        ["srt://[::1]:9000?streamid=a", "srt://[::1]"],
        ["  rtsp://camera.local:554/s  ", "rtsp://camera.local"],
    ])("reduces %s to protocol and domain", (url, expected) => {
        expect(streamUrlTitle(url)).toBe(expected);
    });

    it("drops embedded credentials", () => {
        expect(streamUrlTitle("rtsp://admin:secret@camera.local:554/s")).toBe(
            "rtsp://camera.local",
        );
        expect(streamUrlTitle("https://admin@cam.example.com/feed.ts?x=1")).toBe(
            "https://cam.example.com",
        );
    });

    it("returns text that is not a URL as it is, minus any query", () => {
        expect(streamUrlTitle("camera-1?x=1")).toBe("camera-1");
    });
});

describe("urlAttachmentTitle", () => {
    const record = (streamKind: UrlAttachmentRecord["streamKind"], url: string) =>
        ({ streamKind, url }) as UrlAttachmentRecord;

    it.each([
        ["SRT", "srt://relay.example.com:9000?streamid=a", "srt://relay.example.com · SRT"],
        ["RTSP", "rtsp://cam.local:554/s", "rtsp://cam.local · RTSP"],
        ["MPEGTS", "https://cam.example.com/feed.ts", "https://cam.example.com · MPEG-TS"],
        ["MJPEG", "http://10.0.4.7/mjpg/video.cgi", "http://10.0.4.7 · MJPEG"],
        ["HLS", "https://cdn.example.com/live.m3u8?t=1", "https://cdn.example.com · HLS"],
    ] as const)("titles a %s attachment", (kind, url, expected) => {
        expect(urlAttachmentTitle(record(kind, url))).toBe(expected);
    });

    it("shows a kind this build doesn't know instead of undefined", () => {
        const record = { streamKind: "WEBRTC", url: "webrtc://future.example.com/x" } as unknown;

        expect(urlAttachmentTitle(record as UrlAttachmentRecord)).toBe(
            "webrtc://future.example.com · WEBRTC",
        );
        expect(streamKindName("WEBRTC" as StreamKind)).toBe("WEBRTC");
    });

    it("names every stream kind", () => {
        for (const kind of ["RTSP", "SRT", "HLS", "MPEGTS", "MJPEG"] as const) {
            expect(streamKindName(kind)).not.toBe("");
        }
    });
});

describe("isBlockedMixedContent", () => {
    // happy-dom serves pages over http:, so nothing is mixed content by default.
    it("is false for an http URL when the page itself is http", () => {
        expect(window.location.protocol).toBe("http:");
        expect(isBlockedMixedContent("http://10.0.4.7/mjpg/video.cgi")).toBe(false);
    });

    it("is false for https URLs", () => {
        expect(isBlockedMixedContent("https://cdn.example.com/live.m3u8")).toBe(false);
    });

    it("is false for rtsp URLs, which the page never fetches", () => {
        expect(isBlockedMixedContent("rtsp://camera.local/stream")).toBe(false);
    });
});

describe("isSourceOwnedAttachment", () => {
    const base: UrlAttachmentRecord = {
        id: "1",
        url: "rtsp://x",
        streamKind: "RTSP",
        access: "LOGGED_IN",
        ownerId: null,
        createdAt: "",
        usage: "EVENT_DETAIL",
        creatorId: null,
    };

    it("is true for a source-attached stream (no creator)", () => {
        expect(isSourceOwnedAttachment({ kind: "URL", record: { ...base, creatorId: null } })).toBe(
            true,
        );
    });

    it("is false for an operator-added stream", () => {
        expect(
            isSourceOwnedAttachment({ kind: "URL", record: { ...base, creatorId: "user-1" } }),
        ).toBe(false);
    });
});

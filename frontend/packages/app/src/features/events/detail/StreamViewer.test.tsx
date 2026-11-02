// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, userEvent, within } from "~/test";
import { describe, expect, it, vi } from "vitest";
import type { StreamKind, UrlAttachment } from "@opencop/api";

// The players are system-boundary-heavy; this test is about selection and layout, not playback.
// The mock reports each mount so a switch can be told apart from a re-render.
const mounted = vi.fn();
vi.mock("./UrlAttachmentPlayer", async () => {
    const { useEffect } = await import("react");
    return {
        UrlAttachmentPlayer: ({
            attachment,
            fill,
        }: {
            attachment: UrlAttachment;
            fill?: boolean;
        }) => {
            useEffect(() => {
                mounted(attachment.record.id);
            }, [attachment.record.id]);
            return (
                <div data-testid="player" data-fill={String(Boolean(fill))}>
                    {attachment.record.id}
                </div>
            );
        },
    };
});

import { StreamViewer } from "./StreamViewer";

function stream(id: string, streamKind: StreamKind, url: string): UrlAttachment {
    return {
        kind: "URL",
        record: {
            id,
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

const SRT = stream("srt-1", "SRT", "srt://relay.example.com:9000?streamid=a");
const RTSP = stream("rtsp-1", "RTSP", "rtsp://camera.local:554/stream");
const MJPEG = stream("mjpeg-1", "MJPEG", "http://10.0.4.7/mjpg/video.cgi");

function renderViewer(attachments: UrlAttachment[]) {
    return render(<StreamViewer attachments={attachments} sourceId="src-1" objectId="obj-1" />);
}

describe("StreamViewer", () => {
    it("shows a single stream in the main frame with its title and no list", () => {
        renderViewer([SRT]);

        expect(screen.getByText("srt://relay.example.com · SRT")).toBeInTheDocument();
        expect(screen.getByTestId("player")).toHaveTextContent("srt-1");
        expect(screen.queryByRole("list")).not.toBeInTheDocument();
    });

    it("plays the stream in a fill frame so it is letterboxed instead of resizing the layout", () => {
        renderViewer([SRT]);

        expect(screen.getByTestId("player")).toHaveAttribute("data-fill", "true");
    });

    it("mounts only the first stream but lists every stream with its domain and protocol", () => {
        renderViewer([SRT, RTSP, MJPEG]);

        expect(screen.getAllByTestId("player")).toHaveLength(1);
        expect(screen.getByTestId("player")).toHaveTextContent("srt-1");
        const list = screen.getByRole("list", { name: "Streams" });
        expect(within(list).getAllByRole("listitem")).toHaveLength(3);
        expect(list).toHaveTextContent("srt://relay.example.com");
        expect(list).toHaveTextContent("rtsp://camera.local");
        expect(list).toHaveTextContent("http://10.0.4.7");
        expect(list).toHaveTextContent("SRT");
        expect(list).toHaveTextContent("RTSP");
        expect(list).toHaveTextContent("MJPEG");
    });

    it("marks the playing stream in the list", () => {
        renderViewer([SRT, RTSP, MJPEG]);

        const current = screen
            .getAllByRole("button")
            .filter((button) => button.getAttribute("aria-current") === "true");
        expect(current).toHaveLength(1);
        expect(current[0]).toHaveAccessibleName("Show srt://relay.example.com · SRT");
    });

    it("moves the highlight when another stream is chosen", async () => {
        renderViewer([SRT, RTSP, MJPEG]);

        await userEvent.click(
            screen.getByRole("button", { name: "Show rtsp://camera.local · RTSP" }),
        );

        expect(screen.getByTestId("player")).toHaveTextContent("rtsp-1");
        expect(screen.getByText("rtsp://camera.local · RTSP")).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Show rtsp://camera.local · RTSP" }),
        ).toHaveAttribute("aria-current", "true");
        expect(
            screen.getByRole("button", { name: "Show srt://relay.example.com · SRT" }),
        ).not.toHaveAttribute("aria-current");
        expect(
            within(screen.getByRole("list", { name: "Streams" })).getAllByRole("listitem"),
        ).toHaveLength(3);
    });

    it("remounts the player on a switch, so the previous stream's connection is dropped", async () => {
        mounted.mockClear();
        renderViewer([SRT, RTSP]);
        expect(mounted).toHaveBeenCalledTimes(1);

        await userEvent.click(
            screen.getByRole("button", { name: "Show rtsp://camera.local · RTSP" }),
        );

        expect(mounted).toHaveBeenCalledTimes(2);
        expect(mounted).toHaveBeenLastCalledWith("rtsp-1");
    });

    it("falls back to the first stream when the selected one is removed", async () => {
        const { rerender } = renderViewer([SRT, RTSP]);
        await userEvent.click(
            screen.getByRole("button", { name: "Show rtsp://camera.local · RTSP" }),
        );
        expect(screen.getByTestId("player")).toHaveTextContent("rtsp-1");

        rerender(<StreamViewer attachments={[SRT, MJPEG]} sourceId="src-1" objectId="obj-1" />);

        expect(screen.getByTestId("player")).toHaveTextContent("srt-1");
    });

    it("numbers streams that share a host so they can be told apart, and only those", async () => {
        const first = stream("ts-1", "MPEGTS", "https://relay.example.com/stream/aaa");
        const second = stream("ts-2", "MPEGTS", "https://relay.example.com/stream/bbb");
        renderViewer([first, second, SRT]);

        expect(screen.getByText("https://relay.example.com · MPEG-TS (1)")).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Show https://relay.example.com · MPEG-TS (2)" }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Show srt://relay.example.com · SRT" }),
        ).toBeInTheDocument();

        await userEvent.click(
            screen.getByRole("button", { name: "Show https://relay.example.com · MPEG-TS (2)" }),
        );

        expect(screen.getByTestId("player")).toHaveTextContent("ts-2");
    });
});

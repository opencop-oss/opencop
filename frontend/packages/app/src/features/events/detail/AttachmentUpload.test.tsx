// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { vi } from "vitest";
import { AttachmentUpload } from "./AttachmentUpload";

// OcSelect is a zag.js widget that doesn't drive in happy-dom. Same double the drawer/imagery
// section tests use: a native select, keyed by its item values/labels.
vi.mock("@opencop/ui", async (importOriginal) => {
    interface SelectProps {
        items: { label: string; value: string }[];
        value?: string;
        onValueChange: (value: string) => void;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        OcSelect: (props: SelectProps) =>
            createElement(
                "select",
                {
                    "aria-label": "Type",
                    value: props.value ?? "",
                    onChange: (e: { target: { value: string } }) =>
                        props.onValueChange(e.target.value),
                },
                ...props.items.map((it) =>
                    createElement("option", { key: it.value, value: it.value }, it.label),
                ),
            ),
    };
});

// POST /events/:sourceId/:objectId/attachments
const registerUrlPath = `${API_BASE}/events/src-1/obj-1/attachments`;

async function openUrlForm() {
    render(<AttachmentUpload sourceId="src-1" objectId="obj-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Add Stream or Camera" }));
}

function typeSelect() {
    return screen.getByRole("combobox", { name: "Type" }) as HTMLSelectElement;
}

describe("AttachmentUpload URL form", () => {
    it("infers HLS from an .m3u8 URL", async () => {
        await openUrlForm();
        await userEvent.type(
            screen.getByRole("textbox", { name: "URL" }),
            "https://cdn.example.com/live.m3u8",
        );
        expect(typeSelect().value).toBe("HLS");
    });

    it("keeps a manually picked type while the URL keeps changing", async () => {
        await openUrlForm();
        await userEvent.selectOptions(typeSelect(), "HLS");
        await userEvent.type(
            screen.getByRole("textbox", { name: "URL" }),
            "http://10.0.4.7/mjpg/video.cgi",
        );
        expect(typeSelect().value).toBe("HLS");
    });

    it("returns to inference after Back and reopening the form", async () => {
        await openUrlForm();
        await userEvent.selectOptions(typeSelect(), "HLS");

        await userEvent.click(screen.getByRole("button", { name: "Back" }));
        await userEvent.click(screen.getByRole("button", { name: "Add Stream or Camera" }));

        await userEvent.type(
            screen.getByRole("textbox", { name: "URL" }),
            "http://10.0.4.7/mjpg/video.cgi",
        );
        expect(typeSelect().value).toBe("MJPEG");
    });

    it("rejects a non-http(s) URL for the HLS/MJPEG kinds", async () => {
        await openUrlForm();
        await userEvent.selectOptions(typeSelect(), "MJPEG");
        await userEvent.type(
            screen.getByRole("textbox", { name: "URL" }),
            "rtsp://camera.local/stream",
        );
        await userEvent.click(screen.getByRole("button", { name: "Add" }));
        expect(
            await screen.findByText("Must be an http:// or https:// address"),
        ).toBeInTheDocument();
    });

    it("rejects a non-rtsp URL for the RTSP kind", async () => {
        await openUrlForm();
        expect(typeSelect().value).toBe("RTSP");
        await userEvent.type(
            screen.getByRole("textbox", { name: "URL" }),
            "https://cdn.example.com/live.m3u8",
        );
        await userEvent.selectOptions(typeSelect(), "RTSP");
        await userEvent.click(screen.getByRole("button", { name: "Add" }));
        expect(
            await screen.findByText("Must be a valid RTSP URL starting with rtsp:// or rtsps://"),
        ).toBeInTheDocument();
    });

    it("infers the SRT kind from an srt:// URL", async () => {
        await openUrlForm();
        await userEvent.type(
            screen.getByRole("textbox", { name: "URL" }),
            "srt://relay.local:9000?streamid=compass",
        );
        expect(typeSelect().value).toBe("SRT");
    });

    it("rejects a non-srt URL for the SRT kind", async () => {
        await openUrlForm();
        await userEvent.selectOptions(typeSelect(), "SRT");
        await userEvent.type(
            screen.getByRole("textbox", { name: "URL" }),
            "rtsp://camera.local/stream",
        );
        await userEvent.click(screen.getByRole("button", { name: "Add" }));
        expect(
            await screen.findByText(
                "Enter an srt:// URL (for example srt://host:9000?streamid=name).",
            ),
        ).toBeInTheDocument();
    });

    it("registers a valid URL with the selected kind and returns to the file form", async () => {
        server.use(
            http.post(registerUrlPath, () =>
                HttpResponse.json({
                    kind: "URL",
                    record: {
                        id: "att-1",
                        url: "https://cdn.example.com/live.m3u8",
                        streamKind: "HLS",
                        access: "LOGGED_IN",
                        creatorId: "user-1",
                        ownerId: null,
                        createdAt: "",
                        usage: "EVENT_DETAIL",
                    },
                }),
            ),
        );
        await openUrlForm();
        await userEvent.type(
            screen.getByRole("textbox", { name: "URL" }),
            "https://cdn.example.com/live.m3u8",
        );
        await userEvent.click(screen.getByRole("button", { name: "Add" }));

        await waitFor(() =>
            expect(
                screen.getByRole("button", { name: "Add Stream or Camera" }),
            ).toBeInTheDocument(),
        );
    });
});

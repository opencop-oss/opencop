// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import {
    createExport,
    downloadExport,
    type CreateExportRequest,
    type ExportResponse,
} from "./exports";

const EXPORT_RESPONSE: ExportResponse = {
    exportId: "export-1",
    filename: "layers.csv",
    format: "CSV",
    generatedAt: "2026-06-23T10:00:00Z",
    expiresAt: "2026-06-23T11:00:00Z",
    downloadPath: "/v1/exports/export-1/download",
};
const EXPORT_URL = `${API_BASE}/exports`;

afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = "";
});

describe("createExport", () => {
    it("posts valid element-id exports", async () => {
        let observedRequest: Request | undefined;
        server.use(
            http.post(EXPORT_URL, ({ request }) => {
                observedRequest = request;
                return HttpResponse.json(EXPORT_RESPONSE);
            }),
        );

        await expect(createExport({ format: "CSV", elementIds: ["element-1"] })).resolves.toEqual(
            EXPORT_RESPONSE,
        );

        expect(observedRequest).toBeDefined();
        expect(observedRequest?.method).toBe("POST");
        expect(observedRequest?.credentials).toBe("include");
        expect(observedRequest?.headers.get("content-type")).toBe("application/json");
        expect(observedRequest?.headers.get("x-xsrf-token")).toBe("test-csrf-token");
        await expect(observedRequest?.json()).resolves.toEqual({
            format: "CSV",
            elementIds: ["element-1"],
        });
    });

    it("posts valid subject exports", async () => {
        let observedBody: unknown;
        server.use(
            http.post(EXPORT_URL, async ({ request }) => {
                observedBody = await request.json();
                return HttpResponse.json(EXPORT_RESPONSE);
            }),
        );

        await expect(
            createExport({
                format: "CSV",
                subjects: [
                    {
                        subjectType: "LAYER",
                        subjectId: "layer-1",
                        includeTransitiveData: true,
                    },
                ],
            }),
        ).resolves.toEqual(EXPORT_RESPONSE);

        expect(observedBody).toEqual({
            format: "CSV",
            subjects: [
                {
                    subjectType: "LAYER",
                    subjectId: "layer-1",
                    includeTransitiveData: true,
                },
            ],
        });
    });

    it("rejects requests that include both element ids and subjects", async () => {
        let exportRequests = 0;
        server.use(
            http.post(EXPORT_URL, () => {
                exportRequests++;
                return new HttpResponse(null, { status: 500 });
            }),
        );

        const invalidRequest = {
            format: "CSV",
            elementIds: ["element-1"],
            subjects: [{ subjectType: "LAYER", subjectId: "layer-1" }],
        } as unknown as CreateExportRequest;

        await expect(createExport(invalidRequest)).rejects.toThrow(
            "Export request cannot include both elementIds and subjects.",
        );
        expect(exportRequests).toBe(0);
    });

    it("rejects requests that include neither element ids nor subjects", async () => {
        let exportRequests = 0;
        server.use(
            http.post(EXPORT_URL, () => {
                exportRequests++;
                return new HttpResponse(null, { status: 500 });
            }),
        );

        const invalidRequest = {
            format: "CSV",
        } as unknown as CreateExportRequest;

        await expect(createExport(invalidRequest)).rejects.toThrow(
            "Export request must include either elementIds or subjects.",
        );
        expect(exportRequests).toBe(0);
    });
});

describe("downloadExport", () => {
    it("dispatches the returned download path through a hidden iframe", () => {
        const clickSpy = vi
            .spyOn(HTMLAnchorElement.prototype, "click")
            .mockImplementation(() => {});
        const openSpy = vi.spyOn(window, "open");

        downloadExport(EXPORT_RESPONSE);

        const dispatchedLink = clickSpy.mock.instances[0] as HTMLAnchorElement;
        const frame = document.getElementById("opencop-export-download-frame");

        expect(openSpy).not.toHaveBeenCalled();
        expect(clickSpy).toHaveBeenCalledTimes(1);
        expect(dispatchedLink.href).toBe("http://localhost/api/v1/exports/export-1/download");
        expect(dispatchedLink.target).toBe("opencop-export-download-frame");
        expect(frame).toBeInstanceOf(HTMLIFrameElement);
        expect((frame as HTMLIFrameElement).name).toBe("opencop-export-download-frame");
        expect(document.body.contains(dispatchedLink)).toBe(false);
    });

    it("reuses the hidden iframe across repeated downloads", () => {
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});

        downloadExport(EXPORT_RESPONSE);
        downloadExport({ ...EXPORT_RESPONSE, exportId: "export-2" });

        expect(document.querySelectorAll("#opencop-export-download-frame")).toHaveLength(1);
    });
});

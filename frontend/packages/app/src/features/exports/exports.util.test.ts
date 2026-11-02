// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ApiError, type CreateExportBySubjects, type ExportResponse } from "@opencop/api";
import { describe, expect, it, vi } from "vitest";
import {
    buildElementExportRequest,
    buildSubjectExportRequest,
    exportSubjectsWithFallback,
    formatNamedExportSubjects,
    isNoDataExportError,
} from "./exports.util";

const EXPORT_RESPONSE: ExportResponse = {
    exportId: "export-1",
    filename: "events.csv",
    format: "CSV",
    generatedAt: "2026-06-23T00:00:00Z",
    expiresAt: "2026-06-24T00:00:00Z",
    downloadPath: "/v1/exports/export-1/download",
};

describe("buildElementExportRequest", () => {
    it("deduplicates element ids", () => {
        expect(buildElementExportRequest(["element-1", "element-1", "element-2"])).toEqual({
            format: "CSV",
            elementIds: ["element-1", "element-2"],
        });
    });
});

describe("buildSubjectExportRequest", () => {
    it("builds layer subjects and includes transitive data when requested", () => {
        expect(
            buildSubjectExportRequest("LAYER", ["layer-1", "layer-2"], {
                includeTransitiveData: true,
            }),
        ).toEqual({
            format: "CSV",
            subjects: [
                {
                    subjectType: "LAYER",
                    subjectId: "layer-1",
                    includeTransitiveData: true,
                },
                {
                    subjectType: "LAYER",
                    subjectId: "layer-2",
                    includeTransitiveData: true,
                },
            ],
        });
    });

    it("omits includeTransitiveData for layers when false or omitted", () => {
        const withFalse = buildSubjectExportRequest("LAYER", ["layer-1"], {
            includeTransitiveData: false,
        });
        const omitted = buildSubjectExportRequest("LAYER", ["layer-2"]);

        expect(withFalse.subjects[0]).not.toHaveProperty("includeTransitiveData");
        expect(omitted.subjects[0]).not.toHaveProperty("includeTransitiveData");
    });

    it("deduplicates subject ids and omits transitive data for sources", () => {
        expect(
            buildSubjectExportRequest("SOURCE", ["source-1", "source-1"], {
                includeTransitiveData: true,
            }),
        ).toEqual({
            format: "CSV",
            subjects: [
                {
                    subjectType: "SOURCE",
                    subjectId: "source-1",
                },
            ],
        });
    });
});

describe("isNoDataExportError", () => {
    it("recognizes the canonical backend no-data code", () => {
        expect(
            isNoDataExportError(
                new ApiError(400, {
                    code: "EXPORT_NO_DATA",
                    message: "Request failed.",
                }),
            ),
        ).toBe(true);
    });

    it("recognizes the legacy backend no-data code", () => {
        expect(
            isNoDataExportError(
                new ApiError(400, {
                    code: "EXPORT_SUBJECT_NO_DATA",
                    message: "Request failed.",
                }),
            ),
        ).toBe(true);
    });

    it("falls back to matching backend no-data messages when no code is present", () => {
        expect(
            isNoDataExportError(
                new ApiError(400, {
                    message: "Layer 123 does not contain data.",
                }),
            ),
        ).toBe(true);
    });

    it("does not override an unexpected backend code with message matching", () => {
        expect(
            isNoDataExportError(
                new ApiError(400, {
                    code: "EXPORT_VALIDATION_FAILED",
                    message: "Layer 123 does not contain data.",
                }),
            ),
        ).toBe(false);
    });
});

describe("formatNamedExportSubjects", () => {
    it("formats subject names with labels and a capped overflow summary", () => {
        expect(
            formatNamedExportSubjects(
                [
                    { id: "1", name: "Alpha" },
                    { id: "2", name: "Bravo" },
                    { id: "3", name: "Charlie" },
                    { id: "4", name: "Delta" },
                ],
                "Layer",
                (count) => `${count} more`,
            ),
        ).toBe("Alpha Layer, Bravo Layer, Charlie Layer, and 1 more");
    });
});

describe("exportSubjectsWithFallback", () => {
    it("uses one batch export when all selected subjects can export together", async () => {
        const createExport = vi
            .fn<
                (
                    request: CreateExportBySubjects,
                    callbacks: {
                        onError: (error: unknown) => void;
                        onSuccess: (response: ExportResponse) => void;
                    },
                ) => void
            >()
            .mockImplementationOnce((_request, callbacks) => callbacks.onSuccess(EXPORT_RESPONSE));
        const downloadExport = vi.fn();

        const result = await exportSubjectsWithFallback({
            createExport,
            downloadExport,
            includeTransitiveData: true,
            subjectType: "LAYER",
            subjects: [
                { id: "layer-alpha", name: "Alpha" },
                { id: "layer-bravo", name: "Bravo" },
            ],
        });

        expect(createExport).toHaveBeenCalledTimes(1);
        expect(createExport).toHaveBeenCalledWith(
            {
                format: "CSV",
                subjects: [
                    {
                        subjectType: "LAYER",
                        subjectId: "layer-alpha",
                        includeTransitiveData: true,
                    },
                    {
                        subjectType: "LAYER",
                        subjectId: "layer-bravo",
                        includeTransitiveData: true,
                    },
                ],
            },
            expect.objectContaining({
                onError: expect.any(Function),
                onSuccess: expect.any(Function),
            }),
        );
        expect(downloadExport).toHaveBeenCalledTimes(1);
        expect(downloadExport).toHaveBeenCalledWith(EXPORT_RESPONSE);
        expect(result).toEqual({
            successCount: 2,
            noDataFailures: [],
            downloadFailures: [],
            genericFailures: [],
        });
    });

    it("continues exporting remaining subjects when a batch export reports no data", async () => {
        const createExport = vi
            .fn<
                (
                    request: CreateExportBySubjects,
                    callbacks: {
                        onError: (error: unknown) => void;
                        onSuccess: (response: ExportResponse) => void;
                    },
                ) => void
            >()
            .mockImplementationOnce((_request, callbacks) =>
                callbacks.onError(
                    new ApiError(400, {
                        code: "EXPORT_NO_DATA",
                        message: "One or more selected layers do not contain data.",
                    }),
                ),
            )
            .mockImplementationOnce((_request, callbacks) =>
                callbacks.onError(
                    new ApiError(400, {
                        code: "EXPORT_NO_DATA",
                        message: "Layer alpha does not contain data.",
                    }),
                ),
            )
            .mockImplementationOnce((_request, callbacks) => callbacks.onSuccess(EXPORT_RESPONSE));
        const downloadExport = vi.fn();

        const result = await exportSubjectsWithFallback({
            createExport,
            downloadExport,
            includeTransitiveData: true,
            subjectType: "LAYER",
            subjects: [
                { id: "layer-alpha", name: "Alpha" },
                { id: "layer-bravo", name: "Bravo" },
            ],
        });

        expect(createExport).toHaveBeenCalledTimes(3);
        expect(createExport).toHaveBeenNthCalledWith(
            1,
            {
                format: "CSV",
                subjects: [
                    {
                        subjectType: "LAYER",
                        subjectId: "layer-alpha",
                        includeTransitiveData: true,
                    },
                    {
                        subjectType: "LAYER",
                        subjectId: "layer-bravo",
                        includeTransitiveData: true,
                    },
                ],
            },
            expect.objectContaining({
                onError: expect.any(Function),
                onSuccess: expect.any(Function),
            }),
        );
        expect(createExport).toHaveBeenNthCalledWith(
            2,
            {
                format: "CSV",
                subjects: [
                    {
                        subjectType: "LAYER",
                        subjectId: "layer-alpha",
                        includeTransitiveData: true,
                    },
                ],
            },
            expect.objectContaining({
                onError: expect.any(Function),
                onSuccess: expect.any(Function),
            }),
        );
        expect(createExport).toHaveBeenNthCalledWith(
            3,
            {
                format: "CSV",
                subjects: [
                    {
                        subjectType: "LAYER",
                        subjectId: "layer-bravo",
                        includeTransitiveData: true,
                    },
                ],
            },
            expect.objectContaining({
                onError: expect.any(Function),
                onSuccess: expect.any(Function),
            }),
        );
        expect(downloadExport).toHaveBeenCalledTimes(1);
        expect(downloadExport).toHaveBeenCalledWith(EXPORT_RESPONSE);
        expect(result).toEqual({
            successCount: 1,
            noDataFailures: [
                {
                    subject: { id: "layer-alpha", name: "Alpha" },
                    kind: "noData",
                    message: "Layer alpha does not contain data.",
                },
            ],
            downloadFailures: [],
            genericFailures: [],
        });
    });

    it("tracks download dispatch failures separately and keeps later subjects going", async () => {
        const createExport = vi
            .fn<
                (
                    request: CreateExportBySubjects,
                    callbacks: {
                        onError: (error: unknown) => void;
                        onSuccess: (response: ExportResponse) => void;
                    },
                ) => void
            >()
            .mockImplementationOnce((_request, callbacks) =>
                callbacks.onError(
                    new ApiError(400, {
                        code: "EXPORT_NO_DATA",
                        message: "One or more selected sources do not contain data.",
                    }),
                ),
            )
            .mockImplementationOnce((_request, callbacks) => callbacks.onSuccess(EXPORT_RESPONSE))
            .mockImplementationOnce((_request, callbacks) =>
                callbacks.onSuccess({
                    ...EXPORT_RESPONSE,
                    exportId: "export-2",
                    downloadPath: "/v1/exports/export-2/download",
                }),
            );
        const downloadExport = vi
            .fn()
            .mockImplementationOnce(() => {
                throw new Error("Synthetic download dispatch failure.");
            })
            .mockImplementationOnce(() => undefined);

        const result = await exportSubjectsWithFallback({
            createExport,
            downloadExport,
            subjectType: "SOURCE",
            subjects: [
                { id: "source-alpha", name: "Alpha" },
                { id: "source-bravo", name: "Bravo" },
            ],
        });

        expect(result).toEqual({
            successCount: 1,
            noDataFailures: [],
            downloadFailures: [
                {
                    subject: { id: "source-alpha", name: "Alpha" },
                    kind: "download",
                    message: "Synthetic download dispatch failure.",
                },
            ],
            genericFailures: [],
        });
    });

    it("tracks a combined download dispatch failure separately for the whole selection", async () => {
        const createExport = vi
            .fn<
                (
                    request: CreateExportBySubjects,
                    callbacks: {
                        onError: (error: unknown) => void;
                        onSuccess: (response: ExportResponse) => void;
                    },
                ) => void
            >()
            .mockImplementationOnce((_request, callbacks) => callbacks.onSuccess(EXPORT_RESPONSE));
        const downloadExport = vi.fn().mockImplementationOnce(() => {
            throw new Error("Synthetic download dispatch failure.");
        });

        const result = await exportSubjectsWithFallback({
            createExport,
            downloadExport,
            subjectType: "SOURCE",
            subjects: [
                { id: "source-alpha", name: "Alpha" },
                { id: "source-bravo", name: "Bravo" },
            ],
        });

        expect(createExport).toHaveBeenCalledTimes(1);
        expect(result).toEqual({
            successCount: 0,
            noDataFailures: [],
            downloadFailures: [
                {
                    subject: { id: "source-alpha", name: "Alpha" },
                    kind: "download",
                    message: "Synthetic download dispatch failure.",
                },
                {
                    subject: { id: "source-bravo", name: "Bravo" },
                    kind: "download",
                    message: "Synthetic download dispatch failure.",
                },
            ],
            genericFailures: [],
        });
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    ApiError,
    type CreateExportByElementIds,
    type CreateExportBySubjects,
    type ExportResponse,
    type ExportSubject,
} from "@opencop/api";
import { uniq, uniqBy } from "lodash";

export interface NamedExportSubject {
    id: string;
    name: string;
}

export interface ExportSubjectFailure {
    subject: NamedExportSubject;
    kind: "noData" | "generic" | "download";
    message: string;
}

export interface SubjectExportBatchResult {
    successCount: number;
    noDataFailures: ExportSubjectFailure[];
    downloadFailures: ExportSubjectFailure[];
    genericFailures: ExportSubjectFailure[];
}

interface CreateExportCallbacks {
    onError: (error: unknown) => void;
    onSuccess: (response: ExportResponse) => void;
}

interface ExportSubjectsOptions {
    createExport: (request: CreateExportBySubjects, callbacks: CreateExportCallbacks) => void;
    downloadExport: (response: ExportResponse) => void;
    includeTransitiveData?: boolean;
    subjectType: ExportSubject["subjectType"];
    subjects: NamedExportSubject[];
}

// Keep this list aligned with the backend export no-data contract. Prefer the
// canonical EXPORT_NO_DATA code, while still accepting the legacy subject-scoped
// variant until the backend converges on a single value everywhere.
const NO_DATA_EXPORT_CODES = new Set(["EXPORT_NO_DATA", "EXPORT_SUBJECT_NO_DATA"]);
const NO_DATA_EXPORT_MESSAGE_PATTERNS = [
    /does not contain data/i,
    /contains no data/i,
    /no data to export/i,
    /no exportable data/i,
];

export function buildElementExportRequest(elementIds: string[]): CreateExportByElementIds {
    return {
        format: "CSV",
        elementIds: uniq(elementIds),
    };
}

export function buildSubjectExportRequest(
    subjectType: ExportSubject["subjectType"],
    subjectIds: string[],
    options?: { includeTransitiveData?: boolean },
): CreateExportBySubjects {
    const subjects: ExportSubject[] = uniq(subjectIds).map((subjectId) => ({
        subjectType,
        subjectId,
        ...(subjectType === "LAYER" && options?.includeTransitiveData
            ? { includeTransitiveData: true }
            : {}),
    }));

    return { format: "CSV", subjects };
}

export function isNoDataExportError(error: unknown): boolean {
    if (!(error instanceof ApiError)) return false;

    const code = typeof error.body.code === "string" ? error.body.code : "";
    if (code.length > 0) {
        return NO_DATA_EXPORT_CODES.has(code);
    }

    const message =
        typeof error.body.message === "string" && error.body.message.length > 0
            ? error.body.message
            : error.message;

    // Older backend responses may omit body.code entirely. Keep a narrow message-based
    // fallback so the per-subject export retry still works until the code contract is stable.
    return NO_DATA_EXPORT_MESSAGE_PATTERNS.some((pattern) => pattern.test(message));
}

export function formatNamedExportSubjects(
    subjects: NamedExportSubject[],
    subjectLabel: string,
    andMoreLabel: (count: number) => string,
    maxVisible = 3,
): string {
    const dedupedSubjects = dedupeNamedExportSubjects(subjects);
    const visibleSubjects = dedupedSubjects
        .slice(0, maxVisible)
        .map((subject) => `${subject.name} ${subjectLabel}`);

    const remainingCount = dedupedSubjects.length - visibleSubjects.length;
    if (remainingCount > 0) {
        visibleSubjects.push(andMoreLabel(remainingCount));
    }

    return new Intl.ListFormat(undefined, { style: "long", type: "conjunction" }).format(
        visibleSubjects,
    );
}

export async function exportSubjectsWithFallback({
    createExport,
    downloadExport,
    includeTransitiveData = false,
    subjectType,
    subjects,
}: ExportSubjectsOptions): Promise<SubjectExportBatchResult> {
    const dedupedSubjects = dedupeNamedExportSubjects(subjects);

    if (dedupedSubjects.length === 0) {
        return {
            successCount: 0,
            noDataFailures: [],
            downloadFailures: [],
            genericFailures: [],
        };
    }

    if (dedupedSubjects.length > 1) {
        const batchAttempt = await attemptSubjectExport({
            createExport,
            downloadExport,
            request: buildSubjectExportRequest(
                subjectType,
                dedupedSubjects.map((subject) => subject.id),
                { includeTransitiveData },
            ),
        });

        if (batchAttempt.ok) {
            return {
                successCount: dedupedSubjects.length,
                noDataFailures: [],
                downloadFailures: [],
                genericFailures: [],
            };
        }

        if (batchAttempt.kind === "download") {
            return {
                successCount: 0,
                noDataFailures: [],
                downloadFailures: dedupedSubjects.map((subject) => ({
                    subject,
                    kind: "download" as const,
                    message: getExportFailureMessage(batchAttempt.error),
                })),
                genericFailures: [],
            };
        }

        if (!isNoDataExportError(batchAttempt.error)) {
            return {
                successCount: 0,
                noDataFailures: [],
                downloadFailures: [],
                genericFailures: dedupedSubjects.map((subject) => ({
                    subject,
                    kind: "generic" as const,
                    message: getExportFailureMessage(batchAttempt.error),
                })),
            };
        }
    }

    return exportSubjectsIndividually({
        createExport,
        downloadExport,
        includeTransitiveData,
        subjectType,
        subjects: dedupedSubjects,
    });
}

async function exportSubjectsIndividually({
    createExport,
    downloadExport,
    includeTransitiveData = false,
    subjectType,
    subjects,
}: ExportSubjectsOptions): Promise<SubjectExportBatchResult> {
    const result: SubjectExportBatchResult = {
        successCount: 0,
        noDataFailures: [],
        downloadFailures: [],
        genericFailures: [],
    };

    // Retry per subject only when a combined export can't safely proceed, so a
    // single empty layer/source does not abort the rest of the user's selection.
    for (const subject of subjects) {
        const request = buildSubjectExportRequest(subjectType, [subject.id], {
            includeTransitiveData,
        });
        const attempt = await attemptSubjectExport({ createExport, downloadExport, request });

        if (attempt.ok) {
            result.successCount += 1;
            continue;
        }

        if (attempt.kind === "download") {
            result.downloadFailures.push({
                subject,
                kind: "download",
                message: getExportFailureMessage(attempt.error),
            });
            continue;
        }

        const failure: ExportSubjectFailure = {
            subject,
            kind: isNoDataExportError(attempt.error) ? "noData" : "generic",
            message: getExportFailureMessage(attempt.error),
        };

        if (failure.kind === "noData") {
            result.noDataFailures.push(failure);
        } else {
            result.genericFailures.push(failure);
        }
    }

    return result;
}

async function attemptSubjectExport({
    createExport,
    downloadExport,
    request,
}: {
    createExport: (request: CreateExportBySubjects, callbacks: CreateExportCallbacks) => void;
    downloadExport: (response: ExportResponse) => void;
    request: CreateExportBySubjects;
}): Promise<{ ok: true } | { ok: false; kind: "create" | "download"; error: unknown }> {
    try {
        const response = await createExportRequest(createExport, request);

        try {
            downloadExport(response);
            return { ok: true };
        } catch (error) {
            return { ok: false, kind: "download", error };
        }
    } catch (error) {
        return { ok: false, kind: "create", error };
    }
}

function createExportRequest(
    createExport: (request: CreateExportBySubjects, callbacks: CreateExportCallbacks) => void,
    request: CreateExportBySubjects,
): Promise<ExportResponse> {
    return new Promise<ExportResponse>((resolve, reject) => {
        try {
            createExport(request, {
                onSuccess: resolve,
                onError: reject,
            });
        } catch (error) {
            reject(error);
        }
    });
}

function getExportFailureMessage(error: unknown): string {
    return error instanceof Error ? error.message : "An unexpected error occurred.";
}

function dedupeNamedExportSubjects(subjects: NamedExportSubject[]): NamedExportSubject[] {
    return uniqBy(subjects, "id");
}

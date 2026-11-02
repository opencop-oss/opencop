// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, OPENCOP_CORE_API_URL } from "../client.ts";

const EXPORT_DOWNLOAD_FRAME_ID = "opencop-export-download-frame";

export type ExportFormat = "CSV";

export interface ExportSubject {
    subjectType: "LAYER" | "SOURCE";
    subjectId: string;
    includeTransitiveData?: boolean;
}

export interface CreateExportBySubjects {
    format: ExportFormat;
    subjects: ExportSubject[];
    elementIds?: never;
}

export interface CreateExportByElementIds {
    format: ExportFormat;
    elementIds: string[];
    subjects?: never;
}

export type CreateExportRequest = CreateExportBySubjects | CreateExportByElementIds;

export interface ExportResponse {
    exportId: string;
    filename: string;
    format: ExportFormat;
    generatedAt: string;
    expiresAt: string;
    downloadPath: string;
}

function assertValidCreateExportRequest(body: CreateExportRequest): void {
    const hasElementIds = "elementIds" in body;
    const hasSubjects = "subjects" in body;

    // Defense in depth for untyped callers; the TS union already enforces this at compile time.
    if (hasElementIds && hasSubjects) {
        throw new Error("Export request cannot include both elementIds and subjects.");
    }

    if (!hasElementIds && !hasSubjects) {
        throw new Error("Export request must include either elementIds or subjects.");
    }
}

export async function createExport(body: CreateExportRequest): Promise<ExportResponse> {
    assertValidCreateExportRequest(body);
    return apiFetch<ExportResponse>("/exports", { method: "POST", body: JSON.stringify(body) });
}

export function downloadExport(response: ExportResponse): void {
    if (typeof window === "undefined" || typeof document === "undefined") {
        throw new Error("Export download requires a browser window.");
    }

    // The backend returns a 302 to a signed blob URL. Using fetch() would follow the
    // redirect cross-origin and fail CORS preflight. Browser navigation handles it natively.
    // downloadPath is /v1/exports/:id/download; prepend OPENCOP_CORE_API_URL (/api) to
    // route through the Vite proxy without doubling the /v1 segment.
    // Route the navigation through a hidden iframe so exports behave like a normal
    // download and do not depend on popup permissions or navigate away from the SPA.
    const frame = getOrCreateExportDownloadFrame(document);
    const link = document.createElement("a");
    link.href = OPENCOP_CORE_API_URL + response.downloadPath;
    link.target = frame.name;
    link.style.display = "none";
    document.body.appendChild(link);
    // Navigation outcome is undetectable from JS. click() returns after queueing the
    // browser navigation, so failed downloads here will not throw synchronously.
    link.click();
    // Safe to remove the transient anchor immediately: the navigation task is already
    // queued, and only the named iframe target needs to stay alive for the download.
    link.remove();
}

function getOrCreateExportDownloadFrame(doc: Document): HTMLIFrameElement {
    const existing = doc.getElementById(EXPORT_DOWNLOAD_FRAME_ID);
    if (existing instanceof HTMLIFrameElement) {
        return existing;
    }

    const frame = doc.createElement("iframe");
    // Intentionally omit sandbox: Chromium can silently block Content-Disposition
    // downloads from sandboxed iframes unless allow-downloads is explicitly granted.
    frame.id = EXPORT_DOWNLOAD_FRAME_ID;
    frame.name = EXPORT_DOWNLOAD_FRAME_ID;
    frame.hidden = true;
    frame.setAttribute("aria-hidden", "true");
    frame.style.display = "none";
    doc.body.appendChild(frame);
    return frame;
}

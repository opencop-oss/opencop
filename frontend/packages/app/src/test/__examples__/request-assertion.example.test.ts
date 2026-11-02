// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * EXEMPLAR — asserting the HTTP contract. When a test cares about *how* the
 * request went out (URL, body, headers, CSRF), read the intercepted request in
 * the handler resolver. This is why network is mocked at the HTTP boundary
 * (MSW) rather than by stubbing the API-client module: the real `apiFetch`
 * bootstraps and injects `X-XSRF-TOKEN`, and only MSW sees the complete request.
 * That CSRF proof is not an OAuth or BFF-session credential; those remain
 * server-side or HTTP-only and are never supplied by the test.
 */
import { server, http, HttpResponse, API_BASE } from "~/test";
import { describe, expect, it } from "vitest";
import { createExport, type CreateExportRequest } from "@opencop/api";

describe("createExport request contract (harness exemplar)", () => {
    it("sends the JSON body and the bootstrapped CSRF token as a header", async () => {
        let seenBody: unknown;
        let seenXsrf: string | null = null;
        server.use(
            http.get(`${API_BASE}/csrf`, () => HttpResponse.json({ token: "csrf-abc-123" })),
            http.post(`${API_BASE}/exports`, async ({ request }) => {
                seenBody = await request.json();
                seenXsrf = request.headers.get("x-xsrf-token");
                return HttpResponse.json({
                    exportId: "e1",
                    filename: "export.csv",
                    format: "CSV",
                    generatedAt: "2026-01-01T00:00:00Z",
                    expiresAt: "2026-01-02T00:00:00Z",
                    downloadPath: "/v1/exports/e1/download",
                });
            }),
        );

        const body: CreateExportRequest = { format: "CSV", elementIds: ["alpha-id"] };
        const res = await createExport(body);

        expect(seenBody).toEqual(body);
        expect(seenXsrf).toBe("csrf-abc-123");
        expect(res.exportId).toBe("e1");
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, expectTypeOf, it } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import {
    createConnectionV2,
    patchDataSource,
    type CreateTcpConnectionRequest,
} from "./data-sources";

describe("patchDataSource", () => {
    it("sends access metadata and ignores a successful response body", async () => {
        let requestBody: unknown;
        server.use(
            http.patch(`${API_BASE}/connections/source-1`, async ({ request }) => {
                requestBody = await request.json();
                return HttpResponse.text("not-json");
            }),
        );

        const operation = patchDataSource("source-1", { access: "RW" });

        expectTypeOf(operation).toEqualTypeOf<Promise<void>>();
        await expect(operation).resolves.toBeUndefined();
        expect(requestBody).toEqual({ access: "RW" });
    });

    it("preserves status and stable error code for failed metadata patches", async () => {
        server.use(
            http.patch(`${API_BASE}/connections/source-1`, () =>
                HttpResponse.json(
                    { code: "CORE-CONNECTION-1006", message: "protected backend detail" },
                    { status: 400 },
                ),
            ),
        );

        await expect(patchDataSource("source-1", { access: "W" })).rejects.toMatchObject({
            name: "ApiError",
            status: 400,
            body: { code: "CORE-CONNECTION-1006" },
        });
    });
});

describe("createConnectionV2", () => {
    const V2_CONNECTIONS = `${API_BASE.replace(/\/v1$/, "/v2")}/connections`;
    const REQUEST: CreateTcpConnectionRequest = {
        kind: "TCP",
        name: "TCP source",
        access: "R",
        integration: { id: "integration-1", schemaVersion: 1 },
        extractorId: "extractor-1",
        values: { host: "tcp.example.test", port: "8089" },
        mapperId: "mapper-1",
        framing: { type: "CUSTOM", mode: "delimiterSplit", delimiter: "\n" },
    };
    const CREATED = {
        kind: "TCP",
        id: "source-1",
        name: "TCP source",
        state: "CONNECTED",
        access: "R",
        transportType: "TCP",
        defaultVisible: true,
    };

    it("posts JSON to the v2 endpoint when no files are attached", async () => {
        let contentType: string | null = null;
        let body: unknown;
        server.use(
            http.post(V2_CONNECTIONS, async ({ request }) => {
                contentType = request.headers.get("Content-Type");
                body = await request.json();
                return HttpResponse.json(CREATED, { status: 201 });
            }),
        );

        await expect(createConnectionV2(REQUEST)).resolves.toEqual(CREATED);
        expect(contentType).toBe("application/json");
        expect(body).toEqual(REQUEST);
    });

    it("posts multipart with the JSON body and one part per declared file field", async () => {
        let bodyPart: unknown;
        let filePartNames: string[] = [];
        server.use(
            http.post(V2_CONNECTIONS, async ({ request }) => {
                const formData = await request.formData();
                const part = formData.get("body");
                if (!(part instanceof File)) throw new Error("Expected a JSON body part");
                bodyPart = JSON.parse(await part.text());
                filePartNames = formData
                    .getAll("credentialsZip")
                    .map((entry) => (entry instanceof File ? entry.name : String(entry)));
                return HttpResponse.json(CREATED, { status: 201 });
            }),
        );

        await createConnectionV2(REQUEST, {
            credentialsZip: [new File(["zip"], "creds.zip", { type: "application/zip" })],
            emptyList: [],
        });

        expect(bodyPart).toEqual(REQUEST);
        expect(filePartNames).toEqual(["creds.zip"]);
    });
});

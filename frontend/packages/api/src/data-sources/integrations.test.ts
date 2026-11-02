// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import { getIntegrationConnectionSetup, getIntegrations } from "./integrations";

const NATIVE_ID = "5b96caf4-20dd-4a8a-8f1b-09fb594265be";

describe("getIntegrations", () => {
    it("asks the catalog for one transport and returns its entries", async () => {
        let protocol: string | null = null;
        server.use(
            http.get(`${API_BASE}/integrations`, ({ request }) => {
                protocol = new URL(request.url).searchParams.get("protocol");
                return HttpResponse.json([
                    {
                        integrationId: NATIVE_ID,
                        schemaVersion: 1,
                        name: "Native",
                        description: "Built-in transports",
                        available: true,
                    },
                ]);
            }),
        );

        const entries = await getIntegrations("TCP");

        expect(protocol).toBe("TCP");
        expect(entries).toEqual([expect.objectContaining({ name: "Native", available: true })]);
    });

    it("asks for the whole catalog when no transport is given", async () => {
        let search: string | null = null;
        server.use(
            http.get(`${API_BASE}/integrations`, ({ request }) => {
                search = new URL(request.url).search;
                return HttpResponse.json([]);
            }),
        );

        await getIntegrations();

        expect(search).toBe("");
    });
});

describe("getIntegrationConnectionSetup", () => {
    it("reads one schema version's setup", async () => {
        server.use(
            http.get(`${API_BASE}/integrations/${NATIVE_ID}/versions/1/connection/setup`, () =>
                HttpResponse.json({
                    integrationId: NATIVE_ID,
                    schemaVersion: 1,
                    protocols: [
                        {
                            kind: "TCP",
                            accessModes: ["R"],
                            extractors: [],
                            ingress: [{ type: "ANY_MAPPER_CUSTOM_FRAMING" }],
                            egress: null,
                            framings: [],
                        },
                    ],
                }),
            ),
        );

        const setup = await getIntegrationConnectionSetup(NATIVE_ID, 1);

        expect(setup.protocols[0]).toMatchObject({ kind: "TCP", egress: null });
    });

    it("surfaces an unavailable integration as an ApiError with its code", async () => {
        server.use(
            http.get(`${API_BASE}/integrations/${NATIVE_ID}/versions/2/connection/setup`, () =>
                HttpResponse.json(
                    {
                        code: "INTEGRATION_UNAVAILABLE",
                        message: "Integration is not available in this runtime",
                    },
                    { status: 409 },
                ),
            ),
        );

        await expect(getIntegrationConnectionSetup(NATIVE_ID, 2)).rejects.toMatchObject({
            name: "ApiError",
            status: 409,
            body: { code: "INTEGRATION_UNAVAILABLE" },
        });
    });
});

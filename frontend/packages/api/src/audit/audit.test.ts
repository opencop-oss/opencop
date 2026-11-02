// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, expectTypeOf, it } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import type { Page } from "../common";
import { getAuditHistory, type AuditHistoryItem } from "./audit";

const SOURCE_ID = "550e8400-e29b-41d4-a716-446655440030";
const ACTOR_ID = "550e8400-e29b-41d4-a716-446655440031";

describe("getAuditHistory", () => {
    it("filters by resource and returns the ledger contract with change as an object", async () => {
        let requestUrl: URL | undefined;
        const historyPage = {
            content: [
                {
                    id: 404,
                    entityType: "DATA_SOURCE",
                    entityId: SOURCE_ID,
                    entityName: "BYOD Feed",
                    operation: "SETUP_REPLACED",
                    occurredAt: "2026-10-15T14:10:00Z",
                    actor: {
                        userId: ACTOR_ID,
                        fullName: "Operator One",
                        globalAdmin: true,
                    },
                    change: {
                        changed: [],
                        fieldsSupplied: {
                            count: 2,
                            names: ["access", "host"],
                            truncated: false,
                        },
                        settingsSupplied: {
                            count: 1,
                            names: ["presence"],
                            truncated: false,
                        },
                        valuesOmitted: true,
                    },
                    changeVersion: 1,
                },
            ],
            page: 0,
            size: 25,
            totalElements: 1,
            totalPages: 1,
        } satisfies Page<AuditHistoryItem>;

        server.use(
            http.get(`${API_BASE}/admin/audit-history`, ({ request }) => {
                requestUrl = new URL(request.url);
                return HttpResponse.json(historyPage);
            }),
        );

        const operation = getAuditHistory({
            entityType: "DATA_SOURCE",
            entityId: SOURCE_ID,
            page: 0,
            size: 25,
        });

        expectTypeOf(operation).toEqualTypeOf<Promise<Page<AuditHistoryItem>>>();
        await expect(operation).resolves.toEqual(historyPage);
        expect(requestUrl?.searchParams.get("entityType")).toBe("DATA_SOURCE");
        expect(requestUrl?.searchParams.get("entityId")).toBe(SOURCE_ID);
        expect(requestUrl?.searchParams.get("page")).toBe("0");
        expect(requestUrl?.searchParams.get("size")).toBe("25");
    });

    it("omits the filters it was not given rather than sending empty ones", async () => {
        let requestUrl: URL | undefined;
        server.use(
            http.get(`${API_BASE}/admin/audit-history`, ({ request }) => {
                requestUrl = new URL(request.url);
                return HttpResponse.json({
                    content: [],
                    page: 0,
                    size: 25,
                    totalElements: 0,
                    totalPages: 0,
                });
            }),
        );

        await getAuditHistory({ entityType: "LAYER" });

        expect(requestUrl?.searchParams.get("entityType")).toBe("LAYER");
        expect(requestUrl?.searchParams.has("entityId")).toBe(false);
        expect(requestUrl?.searchParams.has("operation")).toBe(false);
        expect(requestUrl?.searchParams.has("occurredFrom")).toBe(false);
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, expectTypeOf, it } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import type { Page } from "../common";
import { getRuntimeFeatureHistory, type RuntimeFeatureHistoryItem } from "./runtime-features";

describe("getRuntimeFeatureHistory", () => {
    it("requests the selected feature page and returns the final audit contract", async () => {
        let requestUrl: URL | undefined;
        const historyPage = {
            content: [
                {
                    id: 202,
                    featureName: "watchbox",
                    consumerClass: "FRONTEND_ONLY",
                    operation: "CREATE",
                    beforeAudience: null,
                    afterAudience: { mode: "NONE" },
                    revisionBefore: null,
                    revisionAfter: 0,
                    occurredAt: "2026-08-20T14:10:00Z",
                    actor: {
                        userId: "550e8400-e29b-41d4-a716-446655440020",
                        globalAdmin: true,
                    },
                },
            ],
            page: 0,
            size: 25,
            totalElements: 1,
            totalPages: 1,
        } satisfies Page<RuntimeFeatureHistoryItem>;

        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, ({ request }) => {
                requestUrl = new URL(request.url);
                return HttpResponse.json(historyPage);
            }),
        );

        const operation = getRuntimeFeatureHistory({
            featureName: "watchbox",
            page: 0,
            size: 25,
        });

        expectTypeOf(operation).toEqualTypeOf<Promise<Page<RuntimeFeatureHistoryItem>>>();
        await expect(operation).resolves.toEqual(historyPage);
        expect(requestUrl?.searchParams.get("featureName")).toBe("watchbox");
        expect(requestUrl?.searchParams.get("page")).toBe("0");
        expect(requestUrl?.searchParams.get("size")).toBe("25");
    });
});

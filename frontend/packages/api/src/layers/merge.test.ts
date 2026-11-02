// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import { ApiError } from "../error";
import {
    isEmptyLayerMergeValidationError,
    validateMerge,
    type MergeValidateResponse,
} from "./merge";

const VALIDATE_RESPONSE: MergeValidateResponse = {
    layerIds: ["layer-1", "layer-2"],
    activeFeatureCounts: { "layer-1": 5, "layer-2": 3 },
    collisionSummary: {
        totalCollisions: 0,
        pairwiseCounts: [],
    },
};
const VALIDATE_URL = `${API_BASE}/layers/merge/validate`;

describe("validateMerge", () => {
    it("posts the validate request", async () => {
        let observedBody: unknown;
        let observedToken: string | null = null;
        server.use(
            http.post(VALIDATE_URL, async ({ request }) => {
                observedBody = await request.json();
                observedToken = request.headers.get("x-xsrf-token");
                return HttpResponse.json(VALIDATE_RESPONSE);
            }),
        );

        await expect(validateMerge({ layerIds: ["layer-1", "layer-2"] })).resolves.toEqual(
            VALIDATE_RESPONSE,
        );

        expect(observedBody).toEqual({ layerIds: ["layer-1", "layer-2"] });
        expect(observedToken).toBe("test-csrf-token");
    });

    it("re-throws empty-layer conflicts from the backend", async () => {
        server.use(
            http.post(VALIDATE_URL, () =>
                HttpResponse.json(
                    {
                        code: null,
                        message: "All merge source layers must contain active features",
                    },
                    { status: 409 },
                ),
            ),
        );

        await expect(validateMerge({ layerIds: ["layer-1", "layer-2"] })).rejects.toMatchObject({
            name: "ApiError",
            status: 409,
            body: {
                code: null,
                message: "All merge source layers must contain active features",
            },
        });
    });
});

describe("isEmptyLayerMergeValidationError", () => {
    it("recognizes the live backend empty-layer 409 payload", () => {
        const err = new ApiError(409, {
            code: null,
            message: "All merge source layers must contain active features",
        });

        expect(isEmptyLayerMergeValidationError(err)).toBe(true);
    });

    it("ignores unrelated 409s", () => {
        const err = new ApiError(409, { message: "Layer name already exists" });

        expect(isEmptyLayerMergeValidationError(err)).toBe(false);
    });
});

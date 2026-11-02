// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { ApiError, getErrorMessage, isNonRetryableAuthError } from "./error";

describe("ApiError", () => {
    it.each([
        { body: { error: "forbidden" } },
        { body: { error: "forbidden", message: "Internal authorization detail" } },
        { body: { error: "forbidden", code: "OTHER_DENIAL", message: "Internal detail" } },
    ])("uses bounded Web copy for an ordinary authorization 403", ({ body }) => {
        const error = new ApiError(403, body);

        expect(error.message).toBe("You don't have permission to do that.");
        expect(getErrorMessage(error)).toBe("You don't have permission to do that.");
        expect(error.body).toEqual(body);
        expect(error.rawBody).toBe(body);
    });

    it("keeps CSRF rejection distinct from authorization-denial copy", () => {
        const body = {
            error: "forbidden",
            code: "CSRF_REJECTED",
            message: "Internal CSRF detail",
        };
        const error = new ApiError(403, body);

        expect(error.message).toBe("Request failed with status 403");
        expect(getErrorMessage(error)).toBe("Request failed with status 403");
        expect(error.body).toEqual(body);
        expect(error.rawBody).toBe(body);
    });

    it("preserves established messaging for non-403 errors", () => {
        const explicit = new ApiError(409, { message: "Layer name already exists" });
        const fallback = new ApiError(503, { error: "unavailable" });

        expect(explicit.message).toBe("Layer name already exists");
        expect(getErrorMessage(explicit)).toBe("Layer name already exists");
        expect(fallback.message).toBe("Request failed with status 503");
        expect(getErrorMessage(fallback)).toBe("Request failed with status 503");
    });
});

describe("isNonRetryableAuthError", () => {
    it.each([401, 403, 404])("excludes status %i from retry", (status) => {
        expect(isNonRetryableAuthError(new ApiError(status, {}))).toBe(true);
    });

    it.each([400, 409, 422, 500, 502, 503])(
        "leaves status %i retryable — only the session/permission/not-found trio is excluded",
        (status) => {
            expect(isNonRetryableAuthError(new ApiError(status, {}))).toBe(false);
        },
    );

    it("is false for non-ApiError failures — network errors and decode failures stay retryable", () => {
        expect(isNonRetryableAuthError(new TypeError("Failed to fetch"))).toBe(false);
        expect(isNonRetryableAuthError(new SyntaxError("Unexpected token in JSON"))).toBe(false);
        expect(
            isNonRetryableAuthError(new DOMException("The operation was aborted", "AbortError")),
        ).toBe(false);
        expect(isNonRetryableAuthError("not an error")).toBe(false);
        expect(isNonRetryableAuthError(undefined)).toBe(false);
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, ApiError, fetchMe, globalSignOut } from "@opencop/api";
import { describe, expect, expectTypeOf, it } from "vitest";
import { HttpResponse, http, server } from "~/test";

const LOGOUT_URL = "http://localhost/api/v1/bff/logout";

describe("globalSignOut", () => {
    it("has a void-returning public contract", () => {
        const requestIgnoringSuccessfulBody = () =>
            apiFetch("/bff/logout", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            });

        expectTypeOf(requestIgnoringSuccessfulBody).returns.toEqualTypeOf<Promise<void>>();
        expectTypeOf(globalSignOut).returns.toEqualTypeOf<Promise<void>>();
    });

    it("accepts a successful non-JSON response without parsing its body", async () => {
        server.use(
            http.post(
                LOGOUT_URL,
                () => new HttpResponse("<html>signed out</html>", { status: 200 }),
            ),
        );

        await expect(globalSignOut()).resolves.toBeUndefined();
    });

    it("accepts the established empty 204 response", async () => {
        server.use(http.post(LOGOUT_URL, () => new HttpResponse(null, { status: 204 })));

        await expect(globalSignOut()).resolves.toBeUndefined();
    });

    it("preserves ApiError status, body, and rawBody for non-success responses", async () => {
        const responseBody = {
            code: "GLOBAL_SIGN_OUT_UNAVAILABLE",
            message: "Global sign-out is unavailable",
        };
        server.use(http.post(LOGOUT_URL, () => HttpResponse.json(responseBody, { status: 503 })));

        const error = await globalSignOut().catch((caught: unknown) => caught);

        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({ status: 503, body: responseBody, rawBody: responseBody });
    });

    it("keeps JSON parsing as the default successful-response behavior", async () => {
        const responseBody = {
            authenticated: true,
            user: {
                id: "user-1",
                subject: "subject-1",
                username: "tester",
                displayName: "Test User",
                email: "tester@example.com",
            },
            idp: { issuer: "https://idp.example.com" },
            authorities: [],
            claims: {},
        };
        server.use(http.get("http://localhost/api/v1/me", () => HttpResponse.json(responseBody)));

        await expect(fetchMe()).resolves.toEqual(responseBody);
    });
});

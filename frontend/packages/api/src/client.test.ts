// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as PropertySymbol from "happy-dom/lib/PropertySymbol.js";
import Logger from "js-logger";
import { Readable } from "node:stream";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import { apiFetch, clearCsrfToken, withRequestTimeout } from "./client";

beforeEach(() => {
    clearCsrfToken();
});

afterEach(() => {
    vi.restoreAllMocks();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
});

describe("apiFetch CSRF lifecycle", () => {
    it("does not bootstrap or attach CSRF state for safe methods", async () => {
        let bootstrapRequests = 0;
        const observedHeaders: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: "server-token" });
            }),
            http.all(`${API_BASE}/safe`, ({ request }) => {
                observedHeaders.push(request.headers.get("x-xsrf-token"));
                return new HttpResponse(null, { status: 204 });
            }),
        );
        document.cookie = "XSRF-TOKEN=legacy-cookie-token; path=/";

        await apiFetch("/safe");
        await apiFetch("/safe", { method: "head" });
        await apiFetch("/safe", { method: "oPtIoNs" });
        await expect(apiFetch("/safe", { method: "TrAcE" })).rejects.toThrow();

        expect(bootstrapRequests).toBe(0);
        expect(observedHeaders).toEqual([null, null, null]);
    });

    it("bootstraps before the first unsafe request and owns the CSRF header", async () => {
        let bootstrapRequests = 0;
        let bootstrapCredentials: RequestCredentials | undefined;
        let businessRequest:
            | {
                  body: unknown;
                  contentType: string | null;
                  customHeader: string | null;
                  token: string | null;
              }
            | undefined;
        server.use(
            http.get(`${API_BASE}/csrf`, ({ request }) => {
                bootstrapRequests++;
                bootstrapCredentials = request.credentials;
                return HttpResponse.json({ token: "server-token" });
            }),
            http.post(`${API_BASE}/unsafe`, async ({ request }) => {
                businessRequest = {
                    body: await request.json(),
                    contentType: request.headers.get("content-type"),
                    customHeader: request.headers.get("x-custom"),
                    token: request.headers.get("x-xsrf-token"),
                };
                return HttpResponse.json({ accepted: true });
            }),
        );

        const response = await apiFetch<{ accepted: boolean }>("/unsafe", {
            method: "post",
            headers: new Headers({
                "X-Custom": "kept",
                "X-XSRF-TOKEN": "caller-stale-token",
            }),
            body: JSON.stringify({ value: 42 }),
        });

        expect(bootstrapRequests).toBe(1);
        expect(bootstrapCredentials).toBe("include");
        expect(businessRequest).toEqual({
            body: { value: 42 },
            contentType: "application/json",
            customHeader: "kept",
            token: "server-token",
        });
        expect(response).toEqual({ accepted: true });
    });

    it("shares one bootstrap across parallel first unsafe requests", async () => {
        let bootstrapRequests = 0;
        const businessTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: "shared-token" });
            }),
            http.post(`${API_BASE}/parallel/:requestId`, ({ request }) => {
                businessTokens.push(request.headers.get("x-xsrf-token"));
                return new HttpResponse(null, { status: 204 });
            }),
        );

        await Promise.all([
            apiFetch("/parallel/one", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            }),
            apiFetch("/parallel/two", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            }),
        ]);

        expect(bootstrapRequests).toBe(1);
        expect(businessTokens).toEqual(["shared-token", "shared-token"]);
    });

    it("refreshes and replays once after an authenticated CSRF rejection", async () => {
        const apiLogger = Logger.get("api");
        const infoSpy = vi.spyOn(apiLogger, "info");
        const errorSpy = vi.spyOn(apiLogger, "error");
        let bootstrapRequests = 0;
        let businessRequests = 0;
        const businessTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({
                    token: bootstrapRequests === 1 ? "rejected-token" : "replacement-token",
                });
            }),
            http.post(`${API_BASE}/recoverable`, ({ request }) => {
                businessRequests++;
                businessTokens.push(request.headers.get("x-xsrf-token"));
                return businessRequests === 1
                    ? HttpResponse.json(
                          { error: "forbidden", code: "CSRF_REJECTED" },
                          { status: 403 },
                      )
                    : HttpResponse.json({ accepted: true });
            }),
        );

        const response = await apiFetch<{ accepted: boolean }>("/recoverable", {
            method: "POST",
        });

        expect(response).toEqual({ accepted: true });
        expect(bootstrapRequests).toBe(2);
        expect(businessRequests).toBe(2);
        expect(businessTokens).toEqual(["rejected-token", "replacement-token"]);
        expect(infoSpy).toHaveBeenCalledWith("POST /recoverable failed with status 403");
        expect(errorSpy).not.toHaveBeenCalled();
    });

    it.each([401, 403])("redacts a protected %i response body from API logs", async (status) => {
        const apiLogger = Logger.get("api");
        const infoSpy = vi.spyOn(apiLogger, "info");
        const errorSpy = vi.spyOn(apiLogger, "error");
        const protectedBody = {
            code: "CORE-CONNECTION-1006",
            message: "protected backend validation detail",
        };
        server.use(
            http.get(`${API_BASE}/protected-failure`, () =>
                HttpResponse.json(protectedBody, { status }),
            ),
        );

        const outcome = apiFetch("/protected-failure").catch((error: unknown) => error);

        await expect(outcome).resolves.toMatchObject({
            name: "ApiError",
            status,
            body: protectedBody,
            rawBody: protectedBody,
        });
        expect(errorSpy).toHaveBeenCalledWith(
            `GET /protected-failure failed with status ${status}`,
        );
        expect(infoSpy).not.toHaveBeenCalled();
        expect(JSON.stringify([...infoSpy.mock.calls, ...errorSpy.mock.calls])).not.toContain(
            protectedBody.message,
        );
    });

    it("keeps ordinary HTTP failures at error level", async () => {
        const apiLogger = Logger.get("api");
        const infoSpy = vi.spyOn(apiLogger, "info");
        const errorSpy = vi.spyOn(apiLogger, "error");
        server.use(
            http.get(`${API_BASE}/ordinary-failure`, () =>
                HttpResponse.json({ message: "Unavailable" }, { status: 503 }),
            ),
        );

        await expect(apiFetch("/ordinary-failure")).rejects.toMatchObject({ status: 503 });

        expect(errorSpy).toHaveBeenCalledWith("GET /ordinary-failure failed with status 503", {
            message: "Unavailable",
        });
        expect(infoSpy).not.toHaveBeenCalled();
    });

    it("shares one replacement acquisition across concurrent CSRF rejections", async () => {
        let bootstrapRequests = 0;
        let rejectedRequests = 0;
        let releaseRejections!: () => void;
        const rejectionGate = new Promise<void>((resolve) => {
            releaseRejections = resolve;
        });
        const businessTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({
                    token:
                        bootstrapRequests === 1
                            ? "shared-rejected-token"
                            : "shared-replacement-token",
                });
            }),
            http.post(`${API_BASE}/concurrent-recovery/:requestId`, async ({ request }) => {
                const token = request.headers.get("x-xsrf-token");
                businessTokens.push(token);
                if (token === "shared-rejected-token") {
                    rejectedRequests++;
                    if (rejectedRequests === 2) releaseRejections();
                    await rejectionGate;
                    return HttpResponse.json(
                        { error: "forbidden", code: "CSRF_REJECTED" },
                        { status: 403 },
                    );
                }
                return new HttpResponse(null, { status: 204 });
            }),
        );

        await Promise.all([
            apiFetch("/concurrent-recovery/one", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            }),
            apiFetch("/concurrent-recovery/two", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            }),
        ]);

        expect(bootstrapRequests).toBe(2);
        expect(rejectedRequests).toBe(2);
        expect(businessTokens).toEqual([
            "shared-rejected-token",
            "shared-rejected-token",
            "shared-replacement-token",
            "shared-replacement-token",
        ]);
    });

    it("lets a late concurrent rejection recover after the first replacement caller aborts", async () => {
        let bootstrapRequests = 0;
        let markSecondOriginalStarted!: () => void;
        let releaseSecondRejection!: () => void;
        let markReplacementStarted!: () => void;
        let markReplacementAborted!: () => void;
        const secondOriginalStarted = new Promise<void>((resolve) => {
            markSecondOriginalStarted = resolve;
        });
        const secondRejectionGate = new Promise<void>((resolve) => {
            releaseSecondRejection = resolve;
        });
        const replacementStarted = new Promise<void>((resolve) => {
            markReplacementStarted = resolve;
        });
        const replacementAborted = new Promise<void>((resolve) => {
            markReplacementAborted = resolve;
        });
        const businessTokens: Record<"one" | "two", Array<string | null>> = {
            one: [],
            two: [],
        };
        server.use(
            http.get(`${API_BASE}/csrf`, async ({ request }) => {
                bootstrapRequests++;
                if (bootstrapRequests === 1) {
                    return HttpResponse.json({ token: "late-rejection-initial-token" });
                }
                if (bootstrapRequests === 2) {
                    markReplacementStarted();
                    await new Promise<void>((resolve) => {
                        const onAbort = () => {
                            markReplacementAborted();
                            resolve();
                        };
                        if (request.signal.aborted) onAbort();
                        else request.signal.addEventListener("abort", onAbort, { once: true });
                    });
                    return HttpResponse.json({ token: "aborted-replacement-token" });
                }
                return HttpResponse.json({ token: "late-rejection-replacement-token" });
            }),
            http.post(
                `${API_BASE}/late-concurrent-recovery/:requestId`,
                async ({ params, request }) => {
                    const requestId = params.requestId as "one" | "two";
                    const token = request.headers.get("x-xsrf-token");
                    businessTokens[requestId].push(token);
                    if (token === "late-rejection-initial-token") {
                        if (requestId === "two") {
                            markSecondOriginalStarted();
                            await secondRejectionGate;
                        }
                        return HttpResponse.json(
                            { error: "forbidden", code: "CSRF_REJECTED" },
                            { status: 403 },
                        );
                    }
                    return new HttpResponse(null, { status: 204 });
                },
            ),
        );
        const controller = new AbortController();

        const abortedOutcome = apiFetch("/late-concurrent-recovery/one", {
            method: "POST",
            signal: controller.signal,
            ignoreSuccessfulResponseBody: true,
        }).catch((error: unknown) => error);
        const survivingOutcome = apiFetch("/late-concurrent-recovery/two", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        }).then(
            () => "fulfilled" as const,
            (error: unknown) => error,
        );
        await Promise.all([secondOriginalStarted, replacementStarted]);
        controller.abort();
        await replacementAborted;
        releaseSecondRejection();

        await expect(abortedOutcome).resolves.toMatchObject({ name: "AbortError" });
        await expect(survivingOutcome).resolves.toBe("fulfilled");
        expect(bootstrapRequests).toBe(3);
        expect(businessTokens).toEqual({
            one: ["late-rejection-initial-token"],
            two: ["late-rejection-initial-token", "late-rejection-replacement-token"],
        });
    });

    it("does not retry a failed replacement for a later concurrent rejection", async () => {
        let bootstrapRequests = 0;
        let markSecondOriginalStarted!: () => void;
        let releaseSecondRejection!: () => void;
        const secondOriginalStarted = new Promise<void>((resolve) => {
            markSecondOriginalStarted = resolve;
        });
        const secondRejectionGate = new Promise<void>((resolve) => {
            releaseSecondRejection = resolve;
        });
        const businessTokens: Record<"one" | "two", Array<string | null>> = {
            one: [],
            two: [],
        };
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return bootstrapRequests === 1
                    ? HttpResponse.json({ token: "replacement-failure-initial-token" })
                    : HttpResponse.json({ message: "Unavailable" }, { status: 503 });
            }),
            http.post(
                `${API_BASE}/late-rejection-after-failed-replacement/:requestId`,
                async ({ params, request }) => {
                    const requestId = params.requestId as "one" | "two";
                    const token = request.headers.get("x-xsrf-token");
                    businessTokens[requestId].push(token);
                    if (requestId === "two") {
                        markSecondOriginalStarted();
                        await secondRejectionGate;
                    }
                    return HttpResponse.json(
                        { error: "forbidden", code: "CSRF_REJECTED" },
                        { status: 403 },
                    );
                },
            ),
        );

        const firstOutcome = apiFetch("/late-rejection-after-failed-replacement/one", {
            method: "POST",
        }).catch((error: unknown) => error);
        const secondOutcome = apiFetch("/late-rejection-after-failed-replacement/two", {
            method: "POST",
        }).catch((error: unknown) => error);
        await secondOriginalStarted;

        await expect(firstOutcome).resolves.toMatchObject({ name: "ApiError", status: 503 });
        releaseSecondRejection();

        await expect(secondOutcome).resolves.toMatchObject({ name: "ApiError", status: 403 });
        expect(bootstrapRequests).toBe(2);
        expect(businessTokens).toEqual({
            one: ["replacement-failure-initial-token"],
            two: ["replacement-failure-initial-token"],
        });
    });

    it("does not refresh or replay a one-shot streamed body", async () => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        const laterBusinessTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: `stream-token-${bootstrapRequests}` });
            }),
            http.post(`${API_BASE}/streamed`, () => {
                businessRequests++;
                return HttpResponse.json(
                    { error: "forbidden", code: "CSRF_REJECTED" },
                    { status: 403 },
                );
            }),
            http.post(`${API_BASE}/after-streamed`, ({ request }) => {
                laterBusinessTokens.push(request.headers.get("x-xsrf-token"));
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const body = new ReadableStream({
            start(controller) {
                controller.enqueue(new TextEncoder().encode("one-shot"));
                controller.close();
            },
        });
        // Happy DOM's MSW bridge clones upload streams through this supported Node backing stream.
        Object.assign(body, {
            [PropertySymbol.nodeStream]: Readable.from([Buffer.from("one-shot")]),
        });
        const options = {
            method: "POST",
            body,
            duplex: "half",
        } as RequestInit & { duplex: "half" };

        const outcome = apiFetch("/streamed", options).catch((error: unknown) => error);

        await expect(outcome).resolves.toMatchObject({
            name: "ApiError",
            status: 403,
            body: { code: "CSRF_REJECTED" },
        });
        expect(bootstrapRequests).toBe(1);
        expect(businessRequests).toBe(1);

        await apiFetch("/after-streamed", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });
        expect(bootstrapRequests).toBe(2);
        expect(laterBusinessTokens).toEqual(["stream-token-2"]);
    });

    it("lets a late replayable rejection recover after a streamed request advances its generation", async () => {
        let bootstrapRequests = 0;
        let markReplayableOriginalStarted!: () => void;
        let releaseReplayableRejection!: () => void;
        const replayableOriginalStarted = new Promise<void>((resolve) => {
            markReplayableOriginalStarted = resolve;
        });
        const replayableRejectionGate = new Promise<void>((resolve) => {
            releaseReplayableRejection = resolve;
        });
        const businessTokens: Record<"stream" | "json", Array<string | null>> = {
            stream: [],
            json: [],
        };
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: `mixed-body-token-${bootstrapRequests}` });
            }),
            http.post(`${API_BASE}/mixed-body-recovery/:requestId`, async ({ params, request }) => {
                const requestId = params.requestId as "stream" | "json";
                const token = request.headers.get("x-xsrf-token");
                businessTokens[requestId].push(token);
                if (token === "mixed-body-token-1") {
                    if (requestId === "stream") {
                        await replayableOriginalStarted;
                    } else {
                        markReplayableOriginalStarted();
                        await replayableRejectionGate;
                    }
                    return HttpResponse.json(
                        { error: "forbidden", code: "CSRF_REJECTED" },
                        { status: 403 },
                    );
                }
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const body = new ReadableStream({
            start(controller) {
                controller.enqueue(new TextEncoder().encode("one-shot"));
                controller.close();
            },
        });
        // Happy DOM's MSW bridge clones upload streams through this supported Node backing stream.
        Object.assign(body, {
            [PropertySymbol.nodeStream]: Readable.from([Buffer.from("one-shot")]),
        });

        const streamedOutcome = apiFetch("/mixed-body-recovery/stream", {
            method: "POST",
            body,
            duplex: "half",
        } as RequestInit & { duplex: "half" }).catch((error: unknown) => error);
        const replayableOutcome = apiFetch("/mixed-body-recovery/json", {
            method: "POST",
            body: JSON.stringify({ value: 42 }),
            ignoreSuccessfulResponseBody: true,
        }).then(
            () => "fulfilled" as const,
            (error: unknown) => error,
        );

        await replayableOriginalStarted;
        await expect(streamedOutcome).resolves.toMatchObject({
            name: "ApiError",
            status: 403,
            body: { code: "CSRF_REJECTED" },
        });
        releaseReplayableRejection();

        await expect(replayableOutcome).resolves.toBe("fulfilled");
        expect(bootstrapRequests).toBe(2);
        expect(businessTokens).toEqual({
            stream: ["mixed-body-token-1"],
            json: ["mixed-body-token-1", "mixed-body-token-2"],
        });
    });

    it("propagates a second CSRF rejection without another immediate retry and retires its token", async () => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        const businessTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: `bounded-token-${bootstrapRequests}` });
            }),
            http.post(`${API_BASE}/bounded-recovery`, ({ request }) => {
                businessRequests++;
                const token = request.headers.get("x-xsrf-token");
                businessTokens.push(token);
                if (token === "bounded-token-3") {
                    return new HttpResponse(null, { status: 204 });
                }
                return HttpResponse.json(
                    {
                        error: "forbidden",
                        code: "CSRF_REJECTED",
                        attempt: businessRequests,
                    },
                    { status: 403 },
                );
            }),
        );

        const outcome = apiFetch("/bounded-recovery", { method: "POST" }).catch(
            (error: unknown) => error,
        );

        await expect(outcome).resolves.toMatchObject({
            name: "ApiError",
            status: 403,
            rawBody: { code: "CSRF_REJECTED", attempt: 2 },
        });
        expect(bootstrapRequests).toBe(2);
        expect(businessRequests).toBe(2);
        expect(businessTokens).toEqual(["bounded-token-1", "bounded-token-2"]);

        await apiFetch("/bounded-recovery", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });

        expect(bootstrapRequests).toBe(3);
        expect(businessRequests).toBe(3);
        expect(businessTokens).toEqual(["bounded-token-1", "bounded-token-2", "bounded-token-3"]);
    });

    it.each([
        {
            name: "generic authorization denial",
            status: 403,
            body: { error: "forbidden" },
        },
        {
            name: "another 403 code",
            status: 403,
            body: { error: "forbidden", code: "OTHER_DENIAL" },
        },
        {
            name: "authentication failure carrying the CSRF code",
            status: 401,
            body: { error: "unauthorized", code: "CSRF_REJECTED" },
        },
        {
            name: "server failure carrying the CSRF code",
            status: 500,
            body: { error: "server_error", code: "CSRF_REJECTED" },
        },
    ])("does not recover from $name", async ({ status, body }) => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: "terminal-token" });
            }),
            http.post(`${API_BASE}/terminal-response`, () => {
                businessRequests++;
                return HttpResponse.json(body, { status });
            }),
        );

        const outcome = apiFetch("/terminal-response", { method: "POST" }).catch(
            (error: unknown) => error,
        );

        await expect(outcome).resolves.toMatchObject({ name: "ApiError", status, rawBody: body });
        expect(bootstrapRequests).toBe(1);
        expect(businessRequests).toBe(1);
    });

    it("does not recover after an uncertain transport failure", async () => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: "transport-token" });
            }),
            http.post(`${API_BASE}/uncertain`, () => {
                businessRequests++;
                return HttpResponse.error();
            }),
        );

        await expect(apiFetch("/uncertain", { method: "POST" })).rejects.toBeDefined();
        expect(bootstrapRequests).toBe(1);
        expect(businessRequests).toBe(1);
    });

    it("does not recover after the caller aborts the business request", async () => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        let markBusinessStarted!: () => void;
        const businessStarted = new Promise<void>((resolve) => {
            markBusinessStarted = resolve;
        });
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: "business-abort-token" });
            }),
            http.post(`${API_BASE}/business-abort`, async ({ request }) => {
                businessRequests++;
                markBusinessStarted();
                await new Promise<void>((resolve) => {
                    request.signal.addEventListener("abort", () => resolve(), { once: true });
                });
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const controller = new AbortController();

        const operation = apiFetch("/business-abort", {
            method: "POST",
            signal: controller.signal,
        });
        await businessStarted;
        controller.abort();

        await expect(operation).rejects.toMatchObject({ name: "AbortError" });
        expect(bootstrapRequests).toBe(1);
        expect(businessRequests).toBe(1);
    });

    it.each(["empty", "string", "FormData"] as const)(
        "preserves a %s body and request semantics on replay",
        async (bodyKind) => {
            let bootstrapRequests = 0;
            let businessRequests = 0;
            const path = `/preserved?body=${bodyKind}`;
            const observedRequests: Array<{
                method: string;
                url: string;
                body: string | null;
                customHeader: string | null;
                token: string | null;
            }> = [];
            server.use(
                http.get(`${API_BASE}/csrf`, () => {
                    bootstrapRequests++;
                    return HttpResponse.json({ token: `preserved-token-${bootstrapRequests}` });
                }),
                http.patch(`${API_BASE}/preserved`, async ({ request }) => {
                    const body =
                        bodyKind === "FormData"
                            ? String((await request.formData()).get("name"))
                            : (await request.text()) || null;
                    observedRequests.push({
                        method: request.method,
                        url: request.url,
                        body,
                        customHeader: request.headers.get("x-custom"),
                        token: request.headers.get("x-xsrf-token"),
                    });
                    businessRequests++;
                    return businessRequests === 1
                        ? HttpResponse.json(
                              { error: "forbidden", code: "CSRF_REJECTED" },
                              { status: 403 },
                          )
                        : HttpResponse.json({ bodyKind });
                }),
            );
            let body: BodyInit | undefined;
            let expectedBody: string | null = null;
            if (bodyKind === "string") {
                body = JSON.stringify({ value: 42 });
                expectedBody = body;
            } else if (bodyKind === "FormData") {
                const formData = new FormData();
                formData.set("name", "replayable-form");
                body = formData;
                expectedBody = "replayable-form";
            }

            const response = await apiFetch<{ bodyKind: string }>(path, {
                method: "PATCH",
                headers: { "X-Custom": "preserved" },
                body,
            });

            expect(response).toEqual({ bodyKind });
            expect(bootstrapRequests).toBe(2);
            expect(observedRequests).toEqual([
                {
                    method: "PATCH",
                    url: `${API_BASE}${path}`,
                    body: expectedBody,
                    customHeader: "preserved",
                    token: "preserved-token-1",
                },
                {
                    method: "PATCH",
                    url: `${API_BASE}${path}`,
                    body: expectedBody,
                    customHeader: "preserved",
                    token: "preserved-token-2",
                },
            ]);
        },
    );

    it.each([
        {
            name: "FormData mutation",
            createBody: () => {
                const body = new FormData();
                body.set("value", "original-form");
                return {
                    body,
                    changeCallerBody: () => body.set("value", "mutated-form"),
                };
            },
            readBody: async (request: Request) => String((await request.formData()).get("value")),
            expectedBody: "original-form",
        },
        {
            name: "URLSearchParams mutation",
            createBody: () => {
                const body = new URLSearchParams({ value: "original-params" });
                return {
                    body,
                    changeCallerBody: () => body.set("value", "mutated-params"),
                };
            },
            readBody: (request: Request) => request.text(),
            expectedBody: "value=original-params",
        },
        {
            name: "typed-array mutation",
            createBody: () => {
                const body = new Uint8Array([1, 2, 3]);
                return {
                    body,
                    changeCallerBody: () => body.fill(9),
                };
            },
            readBody: async (request: Request) =>
                Array.from(new Uint8Array(await request.arrayBuffer())).join(","),
            expectedBody: "1,2,3",
        },
        {
            name: "ArrayBuffer detachment",
            createBody: () => {
                const body = new Uint8Array([4, 5, 6]).buffer;
                return {
                    body,
                    changeCallerBody: () => {
                        void structuredClone(body, { transfer: [body] });
                    },
                };
            },
            readBody: async (request: Request) =>
                Array.from(new Uint8Array(await request.arrayBuffer())).join(","),
            expectedBody: "4,5,6",
        },
    ] satisfies Array<{
        name: string;
        createBody: () => { body: BodyInit; changeCallerBody: () => void };
        readBody: (request: Request) => Promise<string>;
        expectedBody: string;
    }>)(
        "snapshots the request body before $name",
        async ({ createBody, readBody, expectedBody }) => {
            let bootstrapRequests = 0;
            let businessRequests = 0;
            let markInitialBodyRead!: () => void;
            let releaseInitialRejection!: () => void;
            const initialBodyRead = new Promise<void>((resolve) => {
                markInitialBodyRead = resolve;
            });
            const initialRejectionGate = new Promise<void>((resolve) => {
                releaseInitialRejection = resolve;
            });
            const observedBodies: string[] = [];
            server.use(
                http.get(`${API_BASE}/csrf`, () => {
                    bootstrapRequests++;
                    return HttpResponse.json({ token: `body-snapshot-token-${bootstrapRequests}` });
                }),
                http.post(`${API_BASE}/body-snapshot`, async ({ request }) => {
                    observedBodies.push(await readBody(request));
                    businessRequests++;
                    if (businessRequests === 1) {
                        markInitialBodyRead();
                        await initialRejectionGate;
                        return HttpResponse.json(
                            { error: "forbidden", code: "CSRF_REJECTED" },
                            { status: 403 },
                        );
                    }
                    return new HttpResponse(null, { status: 204 });
                }),
            );
            const { body, changeCallerBody } = createBody();

            const outcome = apiFetch("/body-snapshot", {
                method: "POST",
                body,
                ignoreSuccessfulResponseBody: true,
            });
            await initialBodyRead;
            changeCallerBody();
            releaseInitialRejection();

            await expect(outcome).resolves.toBeUndefined();
            expect(bootstrapRequests).toBe(2);
            expect(observedBodies).toEqual([expectedBody, expectedBody]);
        },
    );

    it("does not let a late rejection erase a newer token generation", async () => {
        let bootstrapRequests = 0;
        let markSlowRequestStarted!: () => void;
        const slowRequestStarted = new Promise<void>((resolve) => {
            markSlowRequestStarted = resolve;
        });
        let markReplacementUsed!: () => void;
        const replacementUsed = new Promise<void>((resolve) => {
            markReplacementUsed = resolve;
        });
        const observedTokens: Record<string, Array<string | null>> = {
            fast: [],
            slow: [],
        };
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({
                    token: bootstrapRequests === 1 ? "old-generation" : "new-generation",
                });
            }),
            http.post(`${API_BASE}/late-rejection/:speed`, async ({ params, request }) => {
                const speed = String(params.speed);
                const token = request.headers.get("x-xsrf-token");
                observedTokens[speed]?.push(token);
                if (speed === "slow" && token === "old-generation") {
                    markSlowRequestStarted();
                    await replacementUsed;
                    return HttpResponse.json(
                        { error: "forbidden", code: "CSRF_REJECTED" },
                        { status: 403 },
                    );
                }
                if (speed === "fast" && token === "old-generation") {
                    await slowRequestStarted;
                    return HttpResponse.json(
                        { error: "forbidden", code: "CSRF_REJECTED" },
                        { status: 403 },
                    );
                }
                if (speed === "fast") markReplacementUsed();
                return new HttpResponse(null, { status: 204 });
            }),
        );

        await Promise.all([
            apiFetch("/late-rejection/slow", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            }),
            apiFetch("/late-rejection/fast", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            }),
        ]);

        expect(bootstrapRequests).toBe(2);
        expect(observedTokens).toEqual({
            fast: ["old-generation", "new-generation"],
            slow: ["old-generation", "new-generation"],
        });
    });

    it.each([
        {
            name: "an authentication failure",
            response: () => HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
        },
        {
            name: "a network failure",
            response: () => HttpResponse.error(),
        },
        {
            name: "a malformed success",
            response: () => HttpResponse.json({ token: "" }),
        },
        {
            name: "a server failure",
            response: () => HttpResponse.json({ message: "Unavailable" }, { status: 503 }),
        },
    ])("does not replay when replacement bootstrap returns $name", async ({ response }) => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return bootstrapRequests === 1
                    ? HttpResponse.json({ token: "rejected-before-bootstrap-failure" })
                    : response();
            }),
            http.post(`${API_BASE}/replacement-failed`, () => {
                businessRequests++;
                return HttpResponse.json(
                    { error: "forbidden", code: "CSRF_REJECTED" },
                    { status: 403 },
                );
            }),
        );

        await expect(apiFetch("/replacement-failed", { method: "POST" })).rejects.toBeDefined();
        expect(bootstrapRequests).toBe(2);
        expect(businessRequests).toBe(1);
    });

    it("does not recover a late rejection after terminal CSRF cleanup", async () => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        let markBusinessStarted!: () => void;
        const businessStarted = new Promise<void>((resolve) => {
            markBusinessStarted = resolve;
        });
        let releaseRejection!: () => void;
        const rejectionGate = new Promise<void>((resolve) => {
            releaseRejection = resolve;
        });
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: `terminal-cleanup-token-${bootstrapRequests}` });
            }),
            http.post(`${API_BASE}/terminal-cleanup-race`, async () => {
                businessRequests++;
                if (businessRequests === 1) {
                    markBusinessStarted();
                    await rejectionGate;
                    return HttpResponse.json(
                        { error: "forbidden", code: "CSRF_REJECTED" },
                        { status: 403 },
                    );
                }
                return new HttpResponse(null, { status: 204 });
            }),
        );

        const outcome = apiFetch("/terminal-cleanup-race", { method: "POST" }).catch(
            (error: unknown) => error,
        );
        await businessStarted;
        clearCsrfToken();
        releaseRejection();

        await expect(outcome).resolves.toMatchObject({
            name: "ApiError",
            status: 403,
            body: { code: "CSRF_REJECTED" },
        });
        expect(bootstrapRequests).toBe(1);
        expect(businessRequests).toBe(1);
    });

    it("preserves caller cancellation during replacement acquisition", async () => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        let markReplacementStarted!: () => void;
        const replacementStarted = new Promise<void>((resolve) => {
            markReplacementStarted = resolve;
        });
        server.use(
            http.get(`${API_BASE}/csrf`, async ({ request }) => {
                bootstrapRequests++;
                if (bootstrapRequests === 1) {
                    return HttpResponse.json({ token: "token-before-replacement-abort" });
                }
                markReplacementStarted();
                await new Promise<void>((resolve) => {
                    request.signal.addEventListener("abort", () => resolve(), { once: true });
                });
                return HttpResponse.json({ token: "must-not-replay-token" });
            }),
            http.post(`${API_BASE}/replacement-abort`, () => {
                businessRequests++;
                return HttpResponse.json(
                    { error: "forbidden", code: "CSRF_REJECTED" },
                    { status: 403 },
                );
            }),
        );
        const controller = new AbortController();

        const operation = apiFetch("/replacement-abort", {
            method: "POST",
            signal: controller.signal,
        });
        await replacementStarted;
        controller.abort();

        await expect(operation).rejects.toMatchObject({ name: "AbortError" });
        expect(bootstrapRequests).toBe(2);
        expect(businessRequests).toBe(1);
    });

    it("preserves the caller abort signal on replay", async () => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        let markReplayStarted!: () => void;
        const replayStarted = new Promise<void>((resolve) => {
            markReplayStarted = resolve;
        });
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: `replay-abort-token-${bootstrapRequests}` });
            }),
            http.post(`${API_BASE}/replay-abort`, async ({ request }) => {
                businessRequests++;
                if (businessRequests === 1) {
                    return HttpResponse.json(
                        { error: "forbidden", code: "CSRF_REJECTED" },
                        { status: 403 },
                    );
                }
                markReplayStarted();
                await new Promise<void>((resolve) => {
                    request.signal.addEventListener("abort", () => resolve(), { once: true });
                });
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const controller = new AbortController();

        const operation = apiFetch("/replay-abort", {
            method: "POST",
            signal: controller.signal,
        });
        await replayStarted;
        controller.abort();

        await expect(operation).rejects.toMatchObject({ name: "AbortError" });
        expect(bootstrapRequests).toBe(2);
        expect(businessRequests).toBe(2);
    });

    it("requires the current token for every method outside the safe set", async () => {
        let bootstrapRequests = 0;
        const observedMethods: string[] = [];
        const observedTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: "unsafe-method-token" });
            }),
            http.all(`${API_BASE}/method-classification`, ({ request }) => {
                observedMethods.push(request.method);
                observedTokens.push(request.headers.get("x-xsrf-token"));
                return new HttpResponse(null, { status: 204 });
            }),
        );

        for (const method of ["post", "PUT", "pAtCh", "DELETE"]) {
            await apiFetch("/method-classification", {
                method,
                ignoreSuccessfulResponseBody: true,
            });
        }

        expect(bootstrapRequests).toBe(1);
        expect(observedMethods).toEqual(["POST", "PUT", "PATCH", "DELETE"]);
        expect(observedTokens).toEqual(Array.from({ length: 4 }, () => "unsafe-method-token"));
    });

    it("preserves FormData request construction", async () => {
        let observedContentType: string | null = null;
        let observedFormData: FormData | undefined;
        server.use(
            http.get(`${API_BASE}/csrf`, () => HttpResponse.json({ token: "form-token" })),
            http.post(`${API_BASE}/form`, async ({ request }) => {
                observedContentType = request.headers.get("content-type");
                observedFormData = await request.formData();
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const body = new FormData();
        body.set("name", "example");

        await apiFetch("/form", {
            method: "POST",
            body,
            ignoreSuccessfulResponseBody: true,
        });

        expect(observedContentType).toMatch(/^multipart\/form-data; boundary=/);
        expect(observedFormData?.get("name")).toBe("example");
    });

    it.each([
        {
            name: "an authentication failure",
            response: () => HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
        },
        {
            name: "a network failure",
            response: () => HttpResponse.error(),
        },
        {
            name: "a malformed success",
            response: () => HttpResponse.json({ token: "   " }),
        },
        {
            name: "malformed JSON",
            response: () =>
                new HttpResponse("{not-json", {
                    headers: { "Content-Type": "application/json" },
                }),
        },
        {
            name: "a server failure",
            response: () => HttpResponse.json({ message: "Unavailable" }, { status: 503 }),
        },
    ])("fails closed when bootstrap returns $name", async ({ response }) => {
        let businessRequests = 0;
        server.use(
            http.get(`${API_BASE}/csrf`, response),
            http.post(`${API_BASE}/must-not-run`, () => {
                businessRequests++;
                return new HttpResponse(null, { status: 204 });
            }),
        );

        await expect(
            apiFetch("/must-not-run", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            }),
        ).rejects.toBeDefined();

        expect(businessRequests).toBe(0);
    });

    it("times out a stalled bootstrap and lets the next explicit attempt retry", async () => {
        const timeoutController = new AbortController();
        const timeoutError = new DOMException("CSRF token acquisition timed out", "TimeoutError");
        const timeoutSpy = vi
            .spyOn(AbortSignal, "timeout")
            .mockReturnValueOnce(timeoutController.signal);
        let markBootstrapStarted!: () => void;
        let releaseBootstrap!: () => void;
        let firstBootstrapSignal: AbortSignal | undefined;
        let bootstrapRequests = 0;
        let businessRequests = 0;
        const bootstrapStarted = new Promise<void>((resolve) => {
            markBootstrapStarted = resolve;
        });
        const bootstrapGate = new Promise<void>((resolve) => {
            releaseBootstrap = resolve;
        });
        server.use(
            http.get(`${API_BASE}/csrf`, async ({ request }) => {
                bootstrapRequests++;
                if (bootstrapRequests === 1) {
                    firstBootstrapSignal = request.signal;
                    markBootstrapStarted();
                    await bootstrapGate;
                    return HttpResponse.json({ token: "timed-out-token" });
                }
                return HttpResponse.json({ token: "retry-after-timeout-token" });
            }),
            http.post(`${API_BASE}/timeout-and-retry`, () => {
                businessRequests++;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const requestOptions = {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        } as const;

        const timedOutOperation = apiFetch("/timeout-and-retry", requestOptions);
        await bootstrapStarted;
        timeoutController.abort(timeoutError);
        const bootstrapAbort = {
            aborted: firstBootstrapSignal?.aborted,
            name:
                firstBootstrapSignal?.reason instanceof DOMException
                    ? firstBootstrapSignal.reason.name
                    : undefined,
        };
        releaseBootstrap();

        await expect(timedOutOperation).rejects.toMatchObject({ name: "TimeoutError" });
        expect(timeoutSpy).toHaveBeenCalledWith(10_000);
        expect(bootstrapAbort).toEqual({ aborted: true, name: "TimeoutError" });
        expect(bootstrapRequests).toBe(1);
        expect(businessRequests).toBe(0);

        await expect(apiFetch("/timeout-and-retry", requestOptions)).resolves.toBeUndefined();
        expect(bootstrapRequests).toBe(2);
        expect(businessRequests).toBe(1);
    });

    it("does not cache a rejected bootstrap", async () => {
        let bootstrapRequests = 0;
        let businessRequests = 0;
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return bootstrapRequests === 1
                    ? HttpResponse.json({ message: "Unavailable" }, { status: 503 })
                    : HttpResponse.json({ token: "retry-token" });
            }),
            http.post(`${API_BASE}/explicit-retry`, () => {
                businessRequests++;
                return new HttpResponse(null, { status: 204 });
            }),
        );

        const requestOptions = {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        } as const;
        await expect(apiFetch("/explicit-retry", requestOptions)).rejects.toBeDefined();
        expect(bootstrapRequests).toBe(1);
        expect(businessRequests).toBe(0);

        await expect(apiFetch("/explicit-retry", requestOptions)).resolves.toBeUndefined();
        expect(bootstrapRequests).toBe(2);
        expect(businessRequests).toBe(1);
    });

    it("keeps one caller's abort local while another caller shares the bootstrap", async () => {
        let markBootstrapStarted!: () => void;
        let releaseBootstrap!: () => void;
        let bootstrapRequests = 0;
        let businessRequests = 0;
        const bootstrapStarted = new Promise<void>((resolve) => {
            markBootstrapStarted = resolve;
        });
        const bootstrapGate = new Promise<void>((resolve) => {
            releaseBootstrap = resolve;
        });
        server.use(
            http.get(`${API_BASE}/csrf`, async () => {
                bootstrapRequests++;
                markBootstrapStarted();
                await bootstrapGate;
                return HttpResponse.json({ token: "shared-after-abort-token" });
            }),
            http.post(`${API_BASE}/aborted`, () => {
                businessRequests++;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const controller = new AbortController();

        const abortedOperation = apiFetch("/aborted", {
            method: "POST",
            signal: controller.signal,
            ignoreSuccessfulResponseBody: true,
        });
        const abortedOutcome = abortedOperation.catch((error: unknown) => error);
        const activeOperation = apiFetch("/aborted", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        }).then(
            () => "fulfilled" as const,
            () => "rejected" as const,
        );
        await bootstrapStarted;
        controller.abort();

        expect(await abortedOutcome).toMatchObject({ name: "AbortError" });
        expect(businessRequests).toBe(0);
        releaseBootstrap();

        await expect(activeOperation).resolves.toBe("fulfilled");
        expect(bootstrapRequests).toBe(1);
        expect(businessRequests).toBe(1);
    });

    it("discards an aborted bootstrap when no active caller remains", async () => {
        let bootstrapRequests = 0;
        let firstBootstrapSignal: AbortSignal | undefined;
        let markFirstBootstrapStarted!: () => void;
        let releaseFirstBootstrap!: () => void;
        let businessRequests = 0;
        const firstBootstrapStarted = new Promise<void>((resolve) => {
            markFirstBootstrapStarted = resolve;
        });
        const firstBootstrapGate = new Promise<void>((resolve) => {
            releaseFirstBootstrap = resolve;
        });
        server.use(
            http.get(`${API_BASE}/csrf`, async ({ request }) => {
                bootstrapRequests++;
                if (bootstrapRequests === 1) {
                    firstBootstrapSignal = request.signal;
                    markFirstBootstrapStarted();
                    await firstBootstrapGate;
                    return HttpResponse.json({ token: "aborted-token" });
                }
                return HttpResponse.json({ token: "retry-token" });
            }),
            http.post(`${API_BASE}/abort-and-retry`, () => {
                businessRequests++;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const controller = new AbortController();

        const abortedOperation = apiFetch("/abort-and-retry", {
            method: "POST",
            signal: controller.signal,
            ignoreSuccessfulResponseBody: true,
        });
        const abortedOutcome = abortedOperation.catch((error: unknown) => error);
        await firstBootstrapStarted;
        controller.abort();
        const bootstrapWasAborted = firstBootstrapSignal?.aborted;
        releaseFirstBootstrap();

        expect(await abortedOutcome).toMatchObject({ name: "AbortError" });
        expect(bootstrapWasAborted).toBe(true);
        expect(businessRequests).toBe(0);

        await apiFetch("/abort-and-retry", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });
        expect(bootstrapRequests).toBe(2);
        expect(businessRequests).toBe(1);
    });

    it("prevents a cleared in-flight generation from repopulating CSRF state", async () => {
        let bootstrapRequests = 0;
        let markFirstBootstrapStarted!: () => void;
        let releaseFirstBootstrap!: () => void;
        const firstBootstrapStarted = new Promise<void>((resolve) => {
            markFirstBootstrapStarted = resolve;
        });
        const firstBootstrapGate = new Promise<void>((resolve) => {
            releaseFirstBootstrap = resolve;
        });
        const businessTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, async () => {
                bootstrapRequests++;
                if (bootstrapRequests === 1) {
                    markFirstBootstrapStarted();
                    await firstBootstrapGate;
                    return HttpResponse.json({ token: "cleared-token" });
                }
                return HttpResponse.json({ token: "current-token" });
            }),
            http.post(`${API_BASE}/generation`, ({ request }) => {
                businessTokens.push(request.headers.get("x-xsrf-token"));
                return new HttpResponse(null, { status: 204 });
            }),
        );

        const clearedOperation = apiFetch("/generation", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });
        const clearedOutcome = clearedOperation.catch((error: unknown) => error);
        await firstBootstrapStarted;
        clearCsrfToken();
        releaseFirstBootstrap();

        expect(await clearedOutcome).toMatchObject({ name: "AbortError" });
        expect(businessTokens).toEqual([]);

        await apiFetch("/generation", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });
        expect(bootstrapRequests).toBe(2);
        expect(businessTokens).toEqual(["current-token"]);
    });

    it("does not send a cached token cleared while apiFetch is awaiting it", async () => {
        let staleBusinessRequests = 0;
        server.use(
            http.get(`${API_BASE}/csrf`, () => HttpResponse.json({ token: "cached-token" })),
            http.post(`${API_BASE}/warm-token`, () => new HttpResponse(null, { status: 204 })),
            http.post(`${API_BASE}/generation-race`, () => {
                staleBusinessRequests++;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        await apiFetch("/warm-token", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });

        const staleOperation = apiFetch("/generation-race", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });
        const staleOutcome = staleOperation.catch((error: unknown) => error);
        clearCsrfToken();

        expect(await staleOutcome).toMatchObject({ name: "AbortError" });
        expect(staleBusinessRequests).toBe(0);
    });
});

describe("withRequestTimeout", () => {
    // `AbortSignal.timeout` schedules its own internal timer outside the
    // surface vitest's fake timers can drive — `vi.advanceTimersByTimeAsync`
    // does not fire it (verified directly). So every test here replaces it
    // with a stand-in `AbortController`, giving deterministic control over
    // "the timeout fires" without waiting on or faking elapsed time.
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("aborts when the caller's own signal aborts — cancellation stays cancellation", () => {
        vi.spyOn(AbortSignal, "timeout").mockReturnValue(new AbortController().signal);
        const caller = new AbortController();

        const composed = withRequestTimeout(caller.signal, 45_000);
        expect(composed.aborted).toBe(false);

        caller.abort("component unmounted");

        expect(composed.aborted).toBe(true);
        expect(composed.reason).toBe("component unmounted");
    });

    it("aborts when the per-attempt timeout fires, independently of the caller's signal", () => {
        const timeoutController = new AbortController();
        vi.spyOn(AbortSignal, "timeout").mockReturnValue(timeoutController.signal);
        const caller = new AbortController();

        const composed = withRequestTimeout(caller.signal, 45_000);
        expect(composed.aborted).toBe(false);

        timeoutController.abort();

        expect(composed.aborted).toBe(true);
        // The caller's own signal is untouched — a query observing only its own
        // signal (as React Query does to detect its own cancellation) sees this
        // as an ordinary failure, not a cancellation.
        expect(caller.signal.aborted).toBe(false);
    });

    it("still applies the timeout when the caller passes no signal", () => {
        const timeoutController = new AbortController();
        vi.spyOn(AbortSignal, "timeout").mockReturnValue(timeoutController.signal);

        const composed = withRequestTimeout(undefined, 45_000);
        expect(composed.aborted).toBe(false);

        timeoutController.abort();

        expect(composed.aborted).toBe(true);
    });

    it("passes the exact millisecond bound through to AbortSignal.timeout", () => {
        const spy = vi.spyOn(AbortSignal, "timeout").mockReturnValue(new AbortController().signal);

        withRequestTimeout(undefined, 45_000);

        expect(spy).toHaveBeenCalledWith(45_000);
    });
});

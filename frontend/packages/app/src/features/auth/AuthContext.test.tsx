// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import { apiFetch, AUTH_QUERY_KEY, cacheUser } from "@opencop/api";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
    API_BASE,
    createTestUser,
    render,
    screen,
    server,
    userEvent,
    waitFor,
    type ObservedLocation,
} from "~/test";
import { useAuth } from "./AuthContext";

type AuthControlsProps = {
    captureLogout: (promise: Promise<void>) => void;
};

const LOGOUT_LABEL = "Logout";
const EXPIRE_SESSION_LABEL = "Expire session";
const SESSION_QUERY_KEY = ["session-scoped-data"] as const;

function AuthControls({ captureLogout }: AuthControlsProps) {
    const { handleSessionExpiry, logout } = useAuth();

    return (
        <>
            <button
                type="button"
                onClick={() => {
                    const promise = logout();
                    captureLogout(promise);
                    void promise.catch(() => undefined);
                }}
            >
                {LOGOUT_LABEL}
            </button>
            <button type="button" onClick={handleSessionExpiry}>
                {EXPIRE_SESSION_LABEL}
            </button>
        </>
    );
}

function renderAuthControls(initialRoute = "/layers?scope=my") {
    const user = createTestUser();
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: Infinity } },
    });
    queryClient.setQueryData(AUTH_QUERY_KEY, user);
    cacheUser(user);
    localStorage.setItem("lastActivity", Date.now().toString());
    window.history.replaceState({}, "", initialRoute);

    let logoutPromise: Promise<void> | undefined;
    let location: ObservedLocation = { pathname: "/layers", search: "?scope=my" };
    render(<AuthControls captureLogout={(promise) => (logoutPromise = promise)} />, {
        user,
        queryClient,
        initialRoute,
        onLocationChange: (nextLocation) => (location = nextLocation),
    });

    return {
        queryClient,
        getLocation: () => location,
        getLogoutPromise: () => {
            if (!logoutPromise) throw new Error("Logout has not started");
            return logoutPromise;
        },
    };
}

describe("AuthProvider terminal outcomes", () => {
    beforeEach(() => {
        localStorage.clear();
        sessionStorage.clear();
        window.history.replaceState({}, "", "/layers?scope=my");
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("treats a direct global-sign-out 401 as definitive session expiry", async () => {
        server.use(
            http.post(`${API_BASE}/bff/logout`, () =>
                HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
            ),
        );
        sessionStorage.setItem("map-state", "session-sentinel");
        const assign = vi.spyOn(window.location, "assign");
        const { getLocation } = renderAuthControls();
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: LOGOUT_LABEL }));

        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));
        const params = new URLSearchParams(getLocation().search);
        expect(params.get("reason")).toBe("expired");
        expect(params.get("returnUrl")).toBe("/layers?scope=my");
        expect(localStorage.getItem("cachedUser")).toBeNull();
        expect(localStorage.getItem("lastActivity")).toBeNull();
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
        expect(assign).not.toHaveBeenCalled();
    });

    it("clears CSRF state after confirmed global sign-out", async () => {
        let bootstrapRequests = 0;
        const unsafeTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: `token-${bootstrapRequests}` });
            }),
            http.post(`${API_BASE}/bff/logout`, ({ request }) => {
                unsafeTokens.push(request.headers.get("x-xsrf-token"));
                return new HttpResponse(null, { status: 204 });
            }),
            http.post(`${API_BASE}/after-logout`, ({ request }) => {
                unsafeTokens.push(request.headers.get("x-xsrf-token"));
                return new HttpResponse(null, { status: 204 });
            }),
        );
        vi.spyOn(window.location, "assign");
        const { getLogoutPromise } = renderAuthControls();
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: LOGOUT_LABEL }));
        await getLogoutPromise();
        await apiFetch("/after-logout", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });

        expect(bootstrapRequests).toBe(2);
        expect(unsafeTokens).toEqual(["token-1", "token-2"]);
    });

    it("preserves CSRF state when global sign-out is not confirmed", async () => {
        let bootstrapRequests = 0;
        let logoutRequests = 0;
        const logoutTokens: Array<string | null> = [];
        server.use(
            http.get(`${API_BASE}/csrf`, () => {
                bootstrapRequests++;
                return HttpResponse.json({ token: "retryable-logout-token" });
            }),
            http.post(`${API_BASE}/bff/logout`, ({ request }) => {
                logoutRequests++;
                logoutTokens.push(request.headers.get("x-xsrf-token"));
                return logoutRequests === 1
                    ? HttpResponse.json({ message: "Unavailable" }, { status: 503 })
                    : new HttpResponse(null, { status: 204 });
            }),
        );
        vi.spyOn(window.location, "assign");
        const { getLogoutPromise } = renderAuthControls();
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: LOGOUT_LABEL }));
        await expect(getLogoutPromise()).rejects.toMatchObject({ status: 503 });
        await user.click(screen.getByRole("button", { name: LOGOUT_LABEL }));
        await expect(getLogoutPromise()).resolves.toBeUndefined();

        expect(bootstrapRequests).toBe(1);
        expect(logoutTokens).toEqual(["retryable-logout-token", "retryable-logout-token"]);
    });

    it("evicts cached query data when the session expires", async () => {
        const { getLocation, queryClient } = renderAuthControls();
        queryClient.setQueryData(SESSION_QUERY_KEY, { sensitive: "session data" });
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: EXPIRE_SESSION_LABEL }));

        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));
        expect(queryClient.getQueryData(AUTH_QUERY_KEY)).toBeUndefined();
        expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeUndefined();
    });

    it("invalidates an in-flight CSRF generation when the session expires", async () => {
        let markBootstrapStarted!: () => void;
        let releaseBootstrap!: () => void;
        let businessRequests = 0;
        const bootstrapStarted = new Promise<void>((resolve) => {
            markBootstrapStarted = resolve;
        });
        const bootstrapGate = new Promise<void>((resolve) => {
            releaseBootstrap = resolve;
        });
        server.use(
            http.get(`${API_BASE}/csrf`, async () => {
                markBootstrapStarted();
                await bootstrapGate;
                return HttpResponse.json({ token: "expired-generation-token" });
            }),
            http.post(`${API_BASE}/pending-expiry`, () => {
                businessRequests++;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        renderAuthControls();
        const user = userEvent.setup();

        const operation = apiFetch("/pending-expiry", {
            method: "POST",
            ignoreSuccessfulResponseBody: true,
        });
        const operationOutcome = operation.catch((error: unknown) => error);
        await bootstrapStarted;
        await user.click(screen.getByRole("button", { name: EXPIRE_SESSION_LABEL }));
        releaseBootstrap();

        expect(await operationOutcome).toMatchObject({ name: "AbortError" });
        expect(businessRequests).toBe(0);
    });

    it("keeps session expiry when it wins against a pending global sign-out", async () => {
        let requestCount = 0;
        let releaseResponse!: () => void;
        const responseGate = new Promise<void>((resolve) => {
            releaseResponse = resolve;
        });
        server.use(
            http.post(`${API_BASE}/bff/logout`, async () => {
                requestCount++;
                await responseGate;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        sessionStorage.setItem("map-state", "session-sentinel");
        const assign = vi.spyOn(window.location, "assign");
        const { getLocation, getLogoutPromise } = renderAuthControls();
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: LOGOUT_LABEL }));
        await waitFor(() => expect(requestCount).toBe(1));
        await user.click(screen.getByRole("button", { name: EXPIRE_SESSION_LABEL }));
        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));

        releaseResponse();
        await getLogoutPromise();

        expect(new URLSearchParams(getLocation().search).get("reason")).toBe("expired");
        expect(assign).not.toHaveBeenCalled();
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
    });

    it("keeps confirmed global sign-out when it wins before session expiry", async () => {
        server.use(
            http.post(`${API_BASE}/bff/logout`, () => new HttpResponse(null, { status: 204 })),
        );
        const assign = vi.spyOn(window.location, "assign");
        const { getLocation, getLogoutPromise } = renderAuthControls();
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: LOGOUT_LABEL }));
        await getLogoutPromise();
        expect(assign).toHaveBeenCalledTimes(1);

        localStorage.setItem("cachedUser", "later-cache-sentinel");
        localStorage.setItem("lastActivity", "later-activity-sentinel");
        await user.click(screen.getByRole("button", { name: EXPIRE_SESSION_LABEL }));

        expect(getLocation().pathname).toBe("/layers");
        expect(assign).toHaveBeenCalledTimes(1);
        expect(localStorage.getItem("cachedUser")).toBe("later-cache-sentinel");
        expect(localStorage.getItem("lastActivity")).toBe("later-activity-sentinel");
    });
});

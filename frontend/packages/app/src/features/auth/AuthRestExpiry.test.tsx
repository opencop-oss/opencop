// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiFetch, AUTH_QUERY_KEY, cacheUser } from "@opencop/api";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { queryClient } from "~/queryClient";
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

const CONTROL_URL = `${API_BASE}/session-expiry-control`;
const CONTROL_LABEL = "Make protected request";
const UNSAFE_CONTROL_LABEL = "Make protected unsafe request";

function RestControl() {
    const query = useQuery({
        queryKey: ["session-expiry-control"],
        queryFn: () => apiFetch("/session-expiry-control"),
        enabled: false,
        retry: false,
    });

    return (
        <button type="button" onClick={() => void query.refetch()}>
            {CONTROL_LABEL}
        </button>
    );
}

function UnsafeRestControl() {
    const mutation = useMutation({
        mutationFn: () =>
            apiFetch("/session-expiry-control", {
                method: "POST",
                ignoreSuccessfulResponseBody: true,
            }),
    });

    return (
        <button type="button" onClick={() => mutation.mutate()}>
            {UNSAFE_CONTROL_LABEL}
        </button>
    );
}

function renderRestControl(unsafe = false) {
    const user = createTestUser();
    queryClient.setQueryData(AUTH_QUERY_KEY, user);
    cacheUser(user);
    localStorage.setItem("lastActivity", Date.now().toString());
    window.history.replaceState({}, "", "/layers?scope=my");

    let location: ObservedLocation = { pathname: "/layers", search: "?scope=my" };
    render(unsafe ? <UnsafeRestControl /> : <RestControl />, {
        queryClient,
        initialRoute: "/layers?scope=my",
        onLocationChange: (nextLocation) => (location = nextLocation),
    });
    return () => location;
}

describe("REST-detected session expiry", () => {
    beforeEach(() => {
        queryClient.clear();
        localStorage.clear();
        sessionStorage.clear();
    });

    afterEach(() => {
        queryClient.clear();
    });

    it("confirms an ordinary request 401 through the auth session before expiring", async () => {
        let meRequestCount = 0;
        let authSessionExpired = false;
        server.use(
            http.get(CONTROL_URL, () =>
                HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
            ),
            http.get(`${API_BASE}/me`, () => {
                meRequestCount++;
                return authSessionExpired
                    ? HttpResponse.json({ message: "Unauthorized" }, { status: 401 })
                    : HttpResponse.json(createTestUser());
            }),
        );
        sessionStorage.setItem("map-state", "session-sentinel");
        const getLocation = renderRestControl();
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: CONTROL_LABEL }));

        await waitFor(() => expect(meRequestCount).toBe(1));
        expect(getLocation().pathname).toBe("/layers");
        expect(localStorage.getItem("cachedUser")).not.toBeNull();

        authSessionExpired = true;
        await user.click(screen.getByRole("button", { name: CONTROL_LABEL }));

        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));
        const params = new URLSearchParams(getLocation().search);
        expect(params.get("reason")).toBe("expired");
        expect(params.get("returnUrl")).toBe("/layers?scope=my");
        expect(localStorage.getItem("cachedUser")).toBeNull();
        expect(localStorage.getItem("lastActivity")).toBeNull();
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
    });

    it("routes bootstrap 401 through the existing terminal authentication lifecycle", async () => {
        let meRequestCount = 0;
        let businessRequests = 0;
        server.use(
            http.get(`${API_BASE}/csrf`, () =>
                HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
            ),
            http.post(CONTROL_URL, () => {
                businessRequests++;
                return new HttpResponse(null, { status: 204 });
            }),
            http.get(`${API_BASE}/me`, () => {
                meRequestCount++;
                return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
            }),
        );
        const getLocation = renderRestControl(true);
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: UNSAFE_CONTROL_LABEL }));

        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));
        expect(meRequestCount).toBe(1);
        expect(businessRequests).toBe(0);
    });

    it("routes an unsafe business request 401 through the existing terminal lifecycle", async () => {
        let meRequestCount = 0;
        let businessRequests = 0;
        server.use(
            http.get(`${API_BASE}/csrf`, () =>
                HttpResponse.json({ token: "session-ending-token" }),
            ),
            http.post(CONTROL_URL, ({ request }) => {
                businessRequests++;
                expect(request.headers.has("x-xsrf-token")).toBe(true);
                return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
            }),
            http.get(`${API_BASE}/me`, () => {
                meRequestCount++;
                return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
            }),
        );
        const getLocation = renderRestControl(true);
        const user = userEvent.setup();

        await user.click(screen.getByRole("button", { name: UNSAFE_CONTROL_LABEL }));

        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));
        const params = new URLSearchParams(getLocation().search);
        expect(params.get("reason")).toBe("expired");
        expect(params.get("returnUrl")).toBe("/layers?scope=my");
        expect(meRequestCount).toBe(1);
        expect(businessRequests).toBe(1);
    });
});

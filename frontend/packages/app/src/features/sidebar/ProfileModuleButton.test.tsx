// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { API_BASE, HttpResponse, http, render, screen, server, userEvent, waitFor } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ProfileModuleButton } from "./ProfileModuleButton";

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

const logoutModule = { name: "logout", icon: <span /> };

describe("ProfileModuleButton global sign-out", () => {
    beforeEach(() => {
        showToast.mockReset();
        localStorage.clear();
        sessionStorage.clear();
        window.history.replaceState({}, "", "/layers?scope=my");
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("clears session state and navigates once after confirmed global sign-out", async () => {
        let requestCount = 0;
        server.use(
            http.post(`${API_BASE}/bff/logout`, () => {
                requestCount++;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        localStorage.setItem("cachedUser", "cached-user-sentinel");
        localStorage.setItem("lastActivity", "activity-sentinel");
        sessionStorage.setItem("map-state", "session-sentinel");
        const assign = vi.spyOn(window.location, "assign");
        const user = userEvent.setup();
        render(<ProfileModuleButton m={logoutModule} />);

        await user.click(screen.getByRole("button", { name: "Logout" }));

        await waitFor(() => {
            expect(assign).toHaveBeenCalledWith("/logged-out?reason=logout");
        });
        expect(requestCount).toBe(1);
        expect(assign).toHaveBeenCalledTimes(1);
        expect(localStorage.getItem("cachedUser")).toBeNull();
        expect(localStorage.getItem("lastActivity")).toBeNull();
        expect(sessionStorage.getItem("map-state")).toBeNull();
        expect(showToast).not.toHaveBeenCalled();
    });

    it("preserves the authenticated retry path when the server rejects global sign-out", async () => {
        let requestCount = 0;
        server.use(
            http.post(`${API_BASE}/bff/logout`, () => {
                requestCount++;
                return HttpResponse.json(
                    {
                        code: "GLOBAL_SIGN_OUT_UNAVAILABLE",
                        message: "Internal server copy must not be displayed",
                    },
                    { status: 503 },
                );
            }),
        );
        localStorage.setItem("cachedUser", "cached-user-sentinel");
        localStorage.setItem("lastActivity", "activity-sentinel");
        sessionStorage.setItem("map-state", "session-sentinel");
        const user = userEvent.setup();
        render(<ProfileModuleButton m={logoutModule} />, {
            initialRoute: "/layers?scope=my",
        });

        const logoutButton = screen.getByRole("button", { name: "Logout" });
        await user.click(logoutButton);

        await waitFor(() => expect(requestCount).toBe(1));
        expect(showToast).toHaveBeenCalledWith({
            type: "error",
            title: "You weren’t logged out. Please try again.",
        });
        expect(localStorage.getItem("cachedUser")).toBe("cached-user-sentinel");
        expect(localStorage.getItem("lastActivity")).toBe("activity-sentinel");
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
        expect(window.location.pathname + window.location.search).toBe("/layers?scope=my");
        expect(logoutButton).not.toBeDisabled();

        await user.click(logoutButton);
        await waitFor(() => expect(requestCount).toBe(2));
        expect(showToast).toHaveBeenCalledTimes(2);
    });

    it.each([
        {
            outcome: "an unmatched 503",
            response: () =>
                HttpResponse.json(
                    { code: "UPSTREAM_UNAVAILABLE", message: "Raw unmatched response" },
                    { status: 503 },
                ),
        },
        {
            outcome: "another non-success response",
            response: () =>
                HttpResponse.json(
                    { code: "INTERNAL_ERROR", message: "Raw server response" },
                    { status: 500 },
                ),
        },
        { outcome: "a network error", response: () => HttpResponse.error() },
    ])("preserves the retry path after $outcome", async ({ response }) => {
        server.use(http.post(`${API_BASE}/bff/logout`, response));
        localStorage.setItem("cachedUser", "cached-user-sentinel");
        localStorage.setItem("lastActivity", "activity-sentinel");
        sessionStorage.setItem("map-state", "session-sentinel");
        const user = userEvent.setup();
        render(<ProfileModuleButton m={logoutModule} />);

        const logoutButton = screen.getByRole("button", { name: "Logout" });
        await user.click(logoutButton);

        await waitFor(() => {
            expect(showToast).toHaveBeenCalledWith({
                type: "error",
                title: "We couldn’t confirm whether you were logged out. Please try again.",
            });
        });
        expect(localStorage.getItem("cachedUser")).toBe("cached-user-sentinel");
        expect(localStorage.getItem("lastActivity")).toBe("activity-sentinel");
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
        expect(window.location.pathname + window.location.search).toBe("/layers?scope=my");
        expect(logoutButton).not.toBeDisabled();
    });

    it("lets auth own a direct sign-out 401 without showing a failure toast", async () => {
        server.use(
            http.post(`${API_BASE}/bff/logout`, () =>
                HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
            ),
        );
        localStorage.setItem("cachedUser", "cached-user-sentinel");
        localStorage.setItem("lastActivity", Date.now().toString());
        sessionStorage.setItem("map-state", "session-sentinel");
        const user = userEvent.setup();
        render(<ProfileModuleButton m={logoutModule} />, {
            initialRoute: "/layers?scope=my",
        });

        await user.click(screen.getByRole("button", { name: "Logout" }));

        await waitFor(() => expect(localStorage.getItem("cachedUser")).toBeNull());
        expect(localStorage.getItem("lastActivity")).toBeNull();
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
        expect(showToast).not.toHaveBeenCalled();
    });

    it("issues one request for rapid repeated clicks while global sign-out is pending", async () => {
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
        const assign = vi.spyOn(window.location, "assign");
        render(<ProfileModuleButton m={logoutModule} />);
        const logoutButton = screen.getByRole("button", { name: "Logout" });

        const user = userEvent.setup();
        await user.dblClick(logoutButton);

        await waitFor(() => expect(requestCount).toBe(1));
        expect(logoutButton).toBeDisabled();
        expect(screen.getByRole("status")).toHaveTextContent("Logging out…");

        releaseResponse();
        await waitFor(() => expect(assign).toHaveBeenCalledTimes(1));
        expect(showToast).not.toHaveBeenCalled();
    });
});

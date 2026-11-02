// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryCache, QueryClient, useQuery } from "@tanstack/react-query";
import { cacheUser, updateLastActivity } from "@opencop/api";
import { SESSION_ENDED } from "@opencop/websocket";
import { useEffect, type ReactNode } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { useAuth, type AuthContextType } from "~/features/auth/AuthContext";
import { act, createTestUser, render, waitFor, type ObservedLocation } from "~/test";
import { websocketMock } from "~/test/websocket-mock";

function CaptureSessionExpiry({
    capture,
}: {
    capture: (handleSessionExpiry: AuthContextType["handleSessionExpiry"]) => void;
}) {
    const { handleSessionExpiry } = useAuth();

    useEffect(() => capture(handleSessionExpiry), [capture, handleSessionExpiry]);

    return null;
}

function PendingActivityQuery({
    onStart,
    result,
}: {
    onStart: () => void;
    result: Promise<string>;
}) {
    useQuery({
        queryKey: ["pending-session-activity", result],
        queryFn: () => {
            onStart();
            return result;
        },
    });

    return null;
}

function prepareAuthenticatedSession({
    children,
    queryClient,
}: {
    children?: ReactNode;
    queryClient?: QueryClient;
} = {}) {
    const user = createTestUser();
    cacheUser(user);
    localStorage.setItem("lastActivity", Date.now().toString());
    sessionStorage.setItem("map-state", "session-sentinel");
    window.history.replaceState({}, "", "/layers?scope=my");

    let location: ObservedLocation = { pathname: "/layers", search: "?scope=my" };
    const locations: ObservedLocation[] = [];
    let expireFromRest: AuthContextType["handleSessionExpiry"] | undefined;

    render(
        <>
            <CaptureSessionExpiry
                capture={(handleSessionExpiry) => {
                    expireFromRest = handleSessionExpiry;
                }}
            />
            {children}
        </>,
        {
            user,
            queryClient,
            initialRoute: "/layers?scope=my",
            onLocationChange: (nextLocation) => {
                location = nextLocation;
                locations.push(nextLocation);
            },
        },
    );

    return {
        getLocation: () => location,
        getLocations: () => locations,
        expireFromRest: () => {
            if (!expireFromRest) throw new Error("Session expiry callback was not captured");
            expireFromRest();
        },
    };
}

describe("WebSocketProvider session lifecycle", () => {
    beforeEach(() => {
        websocketMock.reset();
        localStorage.clear();
        sessionStorage.clear();
    });

    it("routes SESSION_ENDED through the auth-owned expiry flow", async () => {
        const { getLocation } = prepareAuthenticatedSession();

        act(() => websocketMock.close(SESSION_ENDED, "Session ended"));

        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));
        const params = new URLSearchParams(getLocation().search);
        expect(params.get("reason")).toBe("expired");
        expect(params.get("returnUrl")).toBe("/layers?scope=my");
        expect(localStorage.getItem("cachedUser")).toBeNull();
        expect(localStorage.getItem("lastActivity")).toBeNull();
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
    });

    it("prevents a late query success from restoring activity after SESSION_ENDED", async () => {
        let markStarted!: () => void;
        const started = new Promise<void>((resolve) => {
            markStarted = resolve;
        });
        let releaseQuery!: (value: string) => void;
        const queryResult = new Promise<string>((resolve) => {
            releaseQuery = resolve;
        });
        const queryClient = new QueryClient({
            defaultOptions: { queries: { retry: false, staleTime: Infinity } },
            queryCache: new QueryCache({
                onSuccess: () => updateLastActivity(),
            }),
        });
        const { getLocation } = prepareAuthenticatedSession({
            queryClient,
            children: <PendingActivityQuery onStart={markStarted} result={queryResult} />,
        });

        await started;
        act(() => websocketMock.close(SESSION_ENDED, "Session ended"));

        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));
        expect(localStorage.getItem("lastActivity")).toBeNull();

        await act(async () => {
            releaseQuery("late success");
            await queryResult;
            await Promise.resolve();
        });

        expect(localStorage.getItem("lastActivity")).toBeNull();
    });

    it.each([1000, 1006, 1011, 4403])("does not expire auth for close code %i", async (code) => {
        const { getLocation } = prepareAuthenticatedSession();

        act(() => websocketMock.close(code));

        await waitFor(() => expect(getLocation().pathname).toBe("/layers"));
        expect(localStorage.getItem("cachedUser")).not.toBeNull();
        expect(localStorage.getItem("lastActivity")).not.toBeNull();
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
    });

    it("keeps the first expiry outcome across repeated socket and REST signals", async () => {
        const { expireFromRest, getLocation, getLocations } = prepareAuthenticatedSession();

        act(() => websocketMock.close(SESSION_ENDED, "Session ended"));
        await waitFor(() => expect(getLocation().pathname).toBe("/logged-out"));

        localStorage.setItem("cachedUser", "later-cache-sentinel");
        localStorage.setItem("lastActivity", "later-activity-sentinel");
        act(() => {
            websocketMock.close(SESSION_ENDED, "Repeated session end");
            expireFromRest();
        });

        expect(getLocations().filter(({ pathname }) => pathname === "/logged-out")).toHaveLength(1);
        expect(localStorage.getItem("cachedUser")).toBe("later-cache-sentinel");
        expect(localStorage.getItem("lastActivity")).toBe("later-activity-sentinel");
        expect(sessionStorage.getItem("map-state")).toBe("session-sentinel");
    });
});

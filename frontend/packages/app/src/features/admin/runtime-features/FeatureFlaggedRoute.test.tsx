// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { useLocation } from "react-router";
import {
    API_BASE,
    createTestUser,
    http,
    HttpResponse,
    render,
    screen,
    server,
    waitFor,
} from "~/test";
import { appMetadataQueries } from "~/features/admin/app-metadata.queries";
import { FeatureFlaggedRoute } from "./FeatureFlaggedRoute";

function LocationDisplay() {
    const location = useLocation();
    return <div data-testid="location">{location.pathname}</div>;
}

/**
 * A client that actually fetches. The harness default pins `staleTime: Infinity` for seeded
 * caches, which would settle the query instantly and hide the very race these tests exist for.
 */
function fetchingClient(): QueryClient {
    return new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
    });
}

const admin = createTestUser({ authorities: ["ROLE_admin"] });

function mockAppMetadata(features: { name: string; enabled: boolean }[], delayMs = 0) {
    server.use(
        http.get(`${API_BASE}/app-metadata`, async () => {
            if (delayMs > 0) {
                await new Promise((resolve) => setTimeout(resolve, delayMs));
            }
            return HttpResponse.json({ features });
        }),
    );
}

function renderGuarded() {
    return render(
        <>
            <FeatureFlaggedRoute flag="themes-page">
                <div data-testid="guarded-content" />
            </FeatureFlaggedRoute>
            <LocationDisplay />
        </>,
        { user: admin, queryClient: fetchingClient() },
    );
}

describe("FeatureFlaggedRoute", () => {
    it("renders children once a real metadata response reports the flag enabled", async () => {
        mockAppMetadata([{ name: "themes-page", enabled: true }]);

        renderGuarded();

        expect(await screen.findByTestId("guarded-content")).toBeInTheDocument();
        expect(screen.getByTestId("location")).toHaveTextContent("/");
    });

    it("redirects to /map once metadata settles with the flag disabled", async () => {
        mockAppMetadata([{ name: "themes-page", enabled: false }]);

        renderGuarded();

        await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/map"));
        expect(screen.queryByTestId("guarded-content")).not.toBeInTheDocument();
    });

    it("does not redirect while metadata is still in flight for an enabled flag", async () => {
        // The regression this guards: a cold deep link to an enabled route used to bounce to /map
        // because the pending query read as `false` before the answer arrived.
        mockAppMetadata([{ name: "themes-page", enabled: true }], 50);

        renderGuarded();

        expect(screen.getByTestId("location")).toHaveTextContent("/");
        expect(screen.queryByTestId("guarded-content")).not.toBeInTheDocument();

        expect(await screen.findByTestId("guarded-content")).toBeInTheDocument();
        expect(screen.getByTestId("location")).toHaveTextContent("/");
    });

    it("lets a non-admin through when the audience includes them", async () => {
        // The audience is the whole access decision. The old hook also required ROLE_admin, which
        // was a holdover from the localStorage flags and made it impossible to express a flag that
        // gates a feature for ordinary users — rtsp is exactly that. Role restrictions belong on
        // the route (ProtectedRoute), not in the flag.
        mockAppMetadata([{ name: "themes-page", enabled: true }]);

        render(
            <>
                <FeatureFlaggedRoute flag="themes-page">
                    <div data-testid="guarded-content" />
                </FeatureFlaggedRoute>
                <LocationDisplay />
            </>,
            { user: createTestUser({ authorities: [] }), queryClient: fetchingClient() },
        );

        expect(await screen.findByTestId("guarded-content")).toBeInTheDocument();
        expect(screen.getByTestId("location")).toHaveTextContent("/");
    });

    it("redirects a non-admin the audience excludes", async () => {
        // The server resolves `enabled` per the stored audience and grants admins no bypass, so
        // this is the same code path an excluded admin takes.
        mockAppMetadata([{ name: "themes-page", enabled: false }]);

        render(
            <>
                <FeatureFlaggedRoute flag="themes-page">
                    <div data-testid="guarded-content" />
                </FeatureFlaggedRoute>
                <LocationDisplay />
            </>,
            { user: createTestUser({ authorities: [] }), queryClient: fetchingClient() },
        );

        await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/map"));
    });

    it("denies access rather than hanging when metadata fails", async () => {
        server.use(
            http.get(`${API_BASE}/app-metadata`, () => HttpResponse.json({}, { status: 500 })),
        );

        renderGuarded();

        await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/map"));
    });

    it("denies rather than throwing when metadata resolves to a null body", async () => {
        // A settled query holding `null` is not the same as an errored one holding `undefined`,
        // and dereferencing it would take down every consumer of the gate — not just redirect.
        server.use(http.get(`${API_BASE}/app-metadata`, () => HttpResponse.json(null)));

        renderGuarded();

        await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("/map"));
        expect(screen.queryByTestId("guarded-content")).not.toBeInTheDocument();
    });

    it("keeps serving an enabled gate when a background refetch fails after a successful load", async () => {
        // App metadata is stale after 60s and refetches on window focus, so one failed refetch is
        // reachable in normal operation. TanStack reports `isError` on that query while it still
        // holds the successful response — reading the error rather than the data would redirect a
        // user who is already on the page.
        let requests = 0;
        server.use(
            http.get(`${API_BASE}/app-metadata`, () => {
                requests += 1;
                return requests === 1
                    ? HttpResponse.json({ features: [{ name: "themes-page", enabled: true }] })
                    : HttpResponse.json({}, { status: 500 });
            }),
        );

        const queryClient = fetchingClient();
        render(
            <>
                <FeatureFlaggedRoute flag="themes-page">
                    <div data-testid="guarded-content" />
                </FeatureFlaggedRoute>
                <LocationDisplay />
            </>,
            { user: admin, queryClient },
        );

        expect(await screen.findByTestId("guarded-content")).toBeInTheDocument();

        await queryClient.refetchQueries({ queryKey: appMetadataQueries.detail().queryKey });
        await waitFor(() => expect(requests).toBe(2));

        expect(screen.getByTestId("guarded-content")).toBeInTheDocument();
        expect(screen.getByTestId("location")).toHaveTextContent("/");
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
    API_BASE,
    act,
    createTestUser,
    http,
    HttpResponse,
    render,
    screen,
    server,
    userEvent,
    waitFor,
} from "~/test";
import { i18n } from "~/i18n/config";
import { AdminRuntimeFeaturesView } from "./AdminRuntimeFeaturesView";

// Asserted through a spy rather than by mounting the notification stack, matching
// RuntimeFeatureDrawerContent.test.tsx.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

/** Names Sync would send: the declared flags with no catalog row. `rtsp` is seeded by migration. */
const MISSING = ["themes-page"];

let reconcileBodies: { names: string[] }[] = [];

function feature(name: string) {
    return {
        name,
        consumerClass: "FRONTEND_ONLY" as const,
        audience: { mode: "NONE" as const },
        revision: 0,
        updatedAt: "2026-08-11T00:00:00Z",
    };
}

function fetchingClient(): QueryClient {
    return new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
    });
}

function renderView() {
    return render(<AdminRuntimeFeaturesView />, {
        user: createTestUser({ authorities: ["ROLE_admin"] }),
        queryClient: fetchingClient(),
    });
}

/** Clicks Sync once the drift alert has rendered its trailing action. */
async function clickSync() {
    renderView();
    await userEvent.click(await screen.findByRole("button", { name: "Sync" }));
}

beforeEach(async () => {
    await i18n.changeLanguage("en");
    reconcileBodies = [];
    showToast.mockClear();

    server.use(
        // Only `rtsp` exists, so both other declared flags read as drift.
        http.get(`${API_BASE}/app-metadata`, () =>
            HttpResponse.json({
                features: [],
                admin: { runtimeFeatures: [feature("rtsp")] },
            }),
        ),
    );
});

describe("AdminRuntimeFeaturesView repair surface", () => {
    /**
     * The page must stay reachable and usable when every runtime feature is disabled, because it is
     * the only surface that can enable one again — the endpoint has no delete verb and Swagger is
     * not deployed in production. The shared `beforeEach` already reports `features: []`, which is
     * exactly that state; this asserts the page does not gate itself on any of them.
     *
     * The other half of the invariant — that the route is not wrapped in `FeatureFlaggedRoute` —
     * is pinned by `router.test.ts`.
     */
    it("renders the catalog with every runtime feature disabled", async () => {
        renderView();

        expect(await screen.findByRole("table")).toBeInTheDocument();
        expect(await screen.findByText("rtsp")).toBeInTheDocument();
    });
});

describe("AdminRuntimeFeaturesView localization", () => {
    it("keeps a consumer filter active while translating its value in place", async () => {
        await i18n.changeLanguage("fr");
        renderView();

        expect(await screen.findByText("Interface Web")).toBeVisible();

        await userEvent.click(screen.getByRole("button", { name: "Ajouter un filtre" }));
        await userEvent.click(screen.getAllByRole("combobox")[0]);
        await userEvent.click(await screen.findByRole("option", { name: "Contrôlé par" }));
        await userEvent.click(screen.getAllByRole("combobox")[2]);
        await userEvent.click(await screen.findByRole("option", { name: "Interface Web" }));
        await userEvent.click(screen.getByRole("button", { name: "Appliquer" }));

        expect(screen.getByText("Contrôlé par: Égale Interface Web")).toBeVisible();
        expect(screen.getByText("rtsp")).toBeVisible();
        expect(screen.queryByText("themes-page")).not.toBeInTheDocument();

        await act(async () => {
            await i18n.changeLanguage("tw");
        });

        expect(screen.getByText("前端")).toBeVisible();
        expect(screen.getByText("控管端: 等於 前端")).toBeVisible();
        expect(screen.getByText("rtsp")).toBeVisible();
        expect(screen.queryByText("themes-page")).not.toBeInTheDocument();
        expect(screen.queryByText("Interface Web")).not.toBeInTheDocument();
    });
});

describe("AdminRuntimeFeaturesView sync", () => {
    it("offers Sync for exactly the declared flags this environment is missing", async () => {
        server.use(
            http.post(`${API_BASE}/admin/runtime-features/reconcile`, async ({ request }) => {
                reconcileBodies.push((await request.json()) as (typeof reconcileBodies)[number]);
                return HttpResponse.json({
                    created: MISSING.map(feature),
                    alreadyRegistered: [],
                    rejected: [],
                    complete: true,
                });
            }),
        );

        await clickSync();

        await waitFor(() => expect(reconcileBodies).toHaveLength(1));
        // Order follows the table's sort and is not contractual; membership is.
        expect(reconcileBodies[0]?.names).toEqual(expect.arrayContaining(MISSING));
        expect(reconcileBodies[0]?.names).toHaveLength(MISSING.length);
        expect(showToast).toHaveBeenCalledWith({
            type: "success",
            title: "Registered 1 flag",
        });
    });

    it("reports what still needs attention when only some names converged", async () => {
        // Per-name isolation: the server returns 200 with rejections rather than failing the run,
        // and partial progress is already durable.
        server.use(
            http.post(`${API_BASE}/admin/runtime-features/reconcile`, () =>
                HttpResponse.json({
                    created: [feature("themes-page")],
                    alreadyRegistered: [],
                    rejected: [
                        {
                            name: "reserved-name",
                            code: "RUNTIME_FEATURE_RESERVED_NAME",
                            message: "Reserved name",
                        },
                    ],
                    complete: false,
                }),
            ),
        );

        await clickSync();

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith({
                type: "error",
                title: "Registered 1, 1 failed",
                description: "Reserved name",
            }),
        );
    });

    it("says the outcome is uncertain when the request itself fails", async () => {
        // The server may have committed an earlier prefix before it stopped, so claiming nothing
        // happened would be a guess.
        server.use(
            http.post(`${API_BASE}/admin/runtime-features/reconcile`, () =>
                HttpResponse.json({ message: "Boom" }, { status: 500 }),
            ),
        );

        await clickSync();

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: "error",
                    title: "Sync failed. Some flags may have been registered before it stopped.",
                }),
            ),
        );
    });
});

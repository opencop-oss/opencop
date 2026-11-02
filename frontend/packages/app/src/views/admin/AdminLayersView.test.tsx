// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
    API_BASE,
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
import { AdminLayersView } from "./AdminLayersView";

// Spied rather than mounting the notification stack, matching AdminRuntimeFeaturesView.test.tsx.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

function layer(id: string, name: string) {
    return {
        id,
        name,
        type: "LAYER" as const,
        archived: false,
        accessScope: "GLOBAL" as const,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
    };
}

const ALPHA = layer("l1", "Alpha layer");
const ZULU = layer("l2", "Zulu layer");

function page(content: ReturnType<typeof layer>[]) {
    return {
        content,
        page: 0,
        size: 25,
        totalElements: content.length,
        totalPages: 1,
    };
}

function fetchingClient(): QueryClient {
    return new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
    });
}

/** Row checkboxes follow the header's select-all, so row `i` is at index `i + 1`. */
function rowCheckbox(index: number) {
    return screen.getAllByRole("checkbox")[index + 1];
}

beforeEach(async () => {
    await i18n.changeLanguage("en");
    showToast.mockClear();

    // The server owns the filtering here — that is what makes a filtered-out row unreachable,
    // and it is the case the snapshot exists for.
    server.use(
        http.get(`${API_BASE}/admin/layer-directory`, ({ request }) => {
            const search = new URL(request.url).searchParams.get("search")?.toLowerCase();
            const all = [ALPHA, ZULU];
            return HttpResponse.json(
                page(search ? all.filter((l) => l.name.toLowerCase().includes(search)) : all),
            );
        }),
        http.get(`${API_BASE}/layer-groups`, () => HttpResponse.json([])),
    );
});

describe("AdminLayersView selection", () => {
    it("keeps a selected row that the server-side search has dropped from the page", async () => {
        // The server-paged case: once the search changes the query key, the directory restarts at
        // page 0 and the earlier row is genuinely gone from `rows`. Only the snapshot keeps it
        // resolvable, so this is where the toolbar count and the actions would otherwise diverge.
        render(<AdminLayersView />, {
            user: createTestUser({ authorities: ["ROLE_admin"] }),
            queryClient: fetchingClient(),
        });
        await screen.findByText("Alpha layer");

        await userEvent.click(rowCheckbox(0));
        // A generous timeout, not RTL's 1000ms default: this passes through the
        // same table-library recalculation, selection-hook memoization, and
        // ResourceTable's own requestAnimationFrame-driven overflow check as the
        // debounced search below, so it needs the same CI headroom.
        expect(await screen.findByText(/1 of 2 selected/, {}, { timeout: 3000 })).toBeVisible();

        await userEvent.type(screen.getByRole("textbox", { name: "Search" }), "zulu");
        await waitFor(() => expect(screen.queryByText("Alpha layer")).not.toBeInTheDocument(), {
            timeout: 3000,
        });

        // Still counted while off-page, and the second row adds to it rather than replacing it.
        expect(screen.getByText(/1 of 1 selected/)).toBeVisible();
        await userEvent.click(rowCheckbox(0));

        expect(await screen.findByText(/2 of 1 selected/, {}, { timeout: 3000 })).toBeVisible();
    });
});

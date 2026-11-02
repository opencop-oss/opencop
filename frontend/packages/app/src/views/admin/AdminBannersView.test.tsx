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
import { AdminBannersView } from "./AdminBannersView";

// Spied rather than mounting the notification stack, matching AdminRuntimeFeaturesView.test.tsx.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

function banner(id: string, name: string) {
    return {
        id,
        name,
        kind: "HEADER" as const,
        enabled: true,
        startsAt: "2026-01-01T00:00:00Z",
        endsAt: "2026-12-31T00:00:00Z",
        color: "#112233",
        format: "PLAIN" as const,
        dismissible: true,
        linkUrl: "",
        priority: 0,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
        translations: [],
    };
}

const BANNERS = [banner("b1", "Maintenance window"), banner("b2", "Winter storm advisory")];

function fetchingClient(): QueryClient {
    return new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
    });
}

function renderView() {
    return render(<AdminBannersView />, {
        user: createTestUser({ authorities: ["ROLE_admin"] }),
        queryClient: fetchingClient(),
    });
}

/** Row checkboxes follow the header's select-all, so row `i` is at index `i + 1`. */
function rowCheckbox(index: number) {
    return screen.getAllByRole("checkbox")[index + 1];
}

beforeEach(async () => {
    await i18n.changeLanguage("en");
    server.use(http.get(`${API_BASE}/banners`, () => HttpResponse.json(BANNERS)));
});

describe("AdminBannersView selection", () => {
    it("builds a selection across two different searches", async () => {
        // The end-to-end shape: a banner ticked under one search term is still selected after the
        // term changes to one that excludes it, so both can be acted on together.
        renderView();
        await screen.findByText("Maintenance window");

        await userEvent.click(rowCheckbox(0));
        expect(await screen.findByText("1 selected")).toBeVisible();

        const search = screen.getByRole("textbox", { name: "Filter for any field..." });
        await userEvent.type(search, "Winter");
        await waitFor(() =>
            expect(screen.queryByText("Maintenance window")).not.toBeInTheDocument(),
        );

        // The first banner is off screen but still counted — then the second is added to it.
        expect(screen.getByText("1 selected")).toBeVisible();
        await userEvent.click(rowCheckbox(0));

        expect(await screen.findByText("2 selected")).toBeVisible();
    });
});

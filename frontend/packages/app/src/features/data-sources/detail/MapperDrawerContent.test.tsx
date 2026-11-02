// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapperProfile } from "@opencop/api";
import { SideDrawer } from "@opencop/ui";
import { API_BASE, HttpResponse, http, render, screen, server, within } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { MapperDrawerContent } from "./MapperDrawerContent";

// These tests exercise the overview rows, not the notification stack.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

const MAPPER: MapperProfile = {
    id: "mapper-1",
    name: "Read-only Mapper",
    description: "Maps inbound data",
    version: 3,
    dynamicMapper: {
        enabled: true,
        formatConfig: { type: "json", allowComments: false },
        recordSelector: { source: { type: "jsonpath", expr: "$" }, onMissing: "ERROR" },
        rules: [],
    },
};

function renderMapper(mapper: MapperProfile) {
    return render(
        <SideDrawer open onClose={() => {}}>
            <DrawerContextProvider onClose={() => {}}>
                <MapperDrawerContent
                    mapper={mapper}
                    usedBy={[]}
                    archived={[]}
                    isUsageError={false}
                />
            </DrawerContextProvider>
        </SideDrawer>,
    );
}

describe("MapperDrawerContent", () => {
    it("renders audit rows with createdBy/updatedBy resolved to usernames", async () => {
        server.use(
            http.get(`${API_BASE}/admin/profile/mapping/:id/versions`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/users/:userId`, ({ params }) =>
                HttpResponse.json({
                    id: params.userId,
                    username: params.userId === "author-id" ? "jsmith" : "rlee",
                }),
            ),
        );

        renderMapper({
            ...MAPPER,
            createdAt: "2026-03-04T05:06:07Z",
            createdBy: "author-id",
            updatedAt: "2026-03-05T08:09:10Z",
            updatedBy: "editor-id",
            lastEventAt: "2026-03-06T11:12:13Z",
        });

        const drawer = await screen.findByRole("dialog");
        expect(await within(drawer).findByText("jsmith")).toBeVisible();
        expect(within(drawer).getByText("rlee")).toBeVisible();
        expect(within(drawer).getByText("Created By")).toBeVisible();
        expect(within(drawer).getByText("Updated By")).toBeVisible();
        expect(within(drawer).getByText("Last Event")).toBeVisible();
        // Matched by shape rather than literal — the value renders in the viewer's zone.
        expect(within(drawer).getAllByText(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)).toHaveLength(3);
    });

    it("omits every audit row for a mapper that carries no audit metadata", async () => {
        server.use(
            http.get(`${API_BASE}/admin/profile/mapping/:id/versions`, () => HttpResponse.json([])),
        );

        renderMapper(MAPPER);

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByText("v3")).toBeVisible();
        for (const label of [
            "Created At",
            "Created By",
            "Last Updated",
            "Updated By",
            "Last Event",
        ])
            expect(within(drawer).queryByText(label)).not.toBeInTheDocument();
    });
});

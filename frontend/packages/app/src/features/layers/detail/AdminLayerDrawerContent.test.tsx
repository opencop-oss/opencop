// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { LayerInfo } from "@opencop/api";
import { SideDrawer } from "@opencop/ui";
import { API_BASE, HttpResponse, http, render, screen, server, within } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { AdminLayerDrawerContent } from "./AdminLayerDrawerContent";

// These tests exercise the overview rows, not the notification stack.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

const LAYER: LayerInfo = {
    id: "layer-1",
    name: "Alpha Layer",
    createdAt: "2026-03-04T05:06:07Z",
    status: "ACTIVE",
    lifecycleState: "ACTIVE",
    editing: false,
    accessScope: "PRIVATE",
    defaultVisible: true,
};

/** Resolves any user id to a stable username, so a rendered username proves the lookup ran. */
function stubUserLookup() {
    server.use(
        http.get(`${API_BASE}/users/:userId`, ({ params }) =>
            HttpResponse.json({
                id: params.userId,
                username: params.userId === "author-id" ? "jsmith" : "rlee",
            }),
        ),
    );
}

function renderLayer(layer: LayerInfo) {
    return render(
        <SideDrawer open onClose={() => {}}>
            <AdminLayerDrawerContent layer={layer} onOpenModal={() => {}} />
        </SideDrawer>,
    );
}

describe("AdminLayerDrawerContent", () => {
    it("renders audit rows with createdBy/updatedBy resolved to usernames", async () => {
        stubUserLookup();

        renderLayer({
            ...LAYER,
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
    });

    it("falls back to the raw id when the user isn't readable", async () => {
        server.use(
            http.get(`${API_BASE}/users/:userId`, () => new HttpResponse(null, { status: 403 })),
        );

        renderLayer({ ...LAYER, createdBy: "author-id" });

        // A deleted or unreadable user leaves the id on screen rather than blanking the row —
        // retry is off for 4xx, so this is the permanent state, not a transient one.
        const drawer = await screen.findByRole("dialog");
        expect(await within(drawer).findByText("author-id")).toBeVisible();
        expect(within(drawer).getByText("Created By")).toBeVisible();
    });

    it("omits the optional audit rows for a layer that carries no audit metadata", async () => {
        renderLayer(LAYER);

        const drawer = await screen.findByRole("dialog");
        // createdAt is required on LayerInfo, so Created At always renders; the rest do not.
        expect(within(drawer).getByText("Created At")).toBeVisible();
        for (const label of ["Created By", "Last Updated", "Updated By", "Last Event"])
            expect(within(drawer).queryByText(label)).not.toBeInTheDocument();
    });

    it("renders every timestamp at full granularity, archived rows included", async () => {
        stubUserLookup();

        renderLayer({
            ...LAYER,
            status: "ARCHIVED",
            lifecycleState: "ARCHIVED",
            updatedAt: "2026-03-05T08:09:10Z",
            archivedAt: "2026-03-07T14:15:16Z",
            archivedBy: "editor-id",
        });

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByText("Archived At")).toBeVisible();
        // Created, updated and archived all render as timestamps rather than a bare date — the
        // archived row previously used a date-only format while its siblings did not. Matched by
        // shape rather than literal, since the value renders in the viewer's zone.
        expect(within(drawer).getAllByText(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)).toHaveLength(3);
    });

    it("resolves archivedBy only once the layer is archived", async () => {
        let userLookups = 0;
        server.use(
            http.get(`${API_BASE}/users/:userId`, ({ params }) => {
                userLookups += 1;
                return HttpResponse.json({ id: params.userId, username: "rlee" });
            }),
        );

        // archivedBy is set but the layer is active, so the row never renders and the id must not
        // be looked up — the gate moved from an `enabled` flag to the hook's argument.
        renderLayer({ ...LAYER, archivedBy: "editor-id" });

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).queryByText("Archived By")).not.toBeInTheDocument();
        expect(userLookups).toBe(0);
    });

    it("shows the resolved archivedBy username on an archived layer", async () => {
        stubUserLookup();

        renderLayer({
            ...LAYER,
            status: "ARCHIVED",
            lifecycleState: "ARCHIVED",
            archivedAt: "2026-03-07T14:15:16Z",
            archivedBy: "editor-id",
        });

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByText("Archived By")).toBeVisible();
        expect(await within(drawer).findByText("rlee")).toBeVisible();
    });
});

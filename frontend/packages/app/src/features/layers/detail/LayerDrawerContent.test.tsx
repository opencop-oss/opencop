// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyLayerInfo } from "@opencop/api";
import { SideDrawer } from "@opencop/ui";
import { API_BASE, HttpResponse, http, render, screen, server, within } from "~/test";
import { describe, expect, it } from "vitest";
import { i18n } from "~/i18n/config";
import { LayerDrawerContent } from "./LayerDrawerContent";

const LAYER: MyLayerInfo = {
    layerId: "layer-1",
    layerName: "Operations",
    role: "OWNER",
    accessScope: "PRIVATE",
    defaultVisible: true,
    createdAt: "2026-10-21T16:41:20Z",
    createdBy: "user-ada",
    updatedAt: "2026-10-21T17:10:15Z",
    updatedBy: "user-bob",
    lastEventAt: "2026-10-22T08:00:00Z",
};

function renderDrawer(layer: MyLayerInfo) {
    render(
        <SideDrawer open onClose={() => {}}>
            <LayerDrawerContent layer={layer} onOpenModal={() => {}} isArchived={false} />
        </SideDrawer>,
    );
}

describe("LayerDrawerContent audit rows", () => {
    it("lists created, updated and last-event rows, resolving who made each change", async () => {
        server.use(
            http.get(`${API_BASE}/users/user-ada`, () =>
                HttpResponse.json({ id: "user-ada", username: "ada" }),
            ),
            http.get(`${API_BASE}/users/user-bob`, () =>
                HttpResponse.json({ id: "user-bob", username: "bob" }),
            ),
        );

        renderDrawer(LAYER);

        const drawer = await screen.findByRole("dialog");
        expect(await within(drawer).findByText("ada")).toBeVisible();
        expect(await within(drawer).findByText("bob")).toBeVisible();
        expect(within(drawer).getAllByText(i18n.t("common.createdAt")).length).toBeGreaterThan(0);
        expect(within(drawer).getAllByText(i18n.t("common.updatedAt")).length).toBeGreaterThan(0);
        expect(within(drawer).getAllByText(i18n.t("common.lastEventAt")).length).toBeGreaterThan(0);
    });

    it("omits rows for audit data the layer does not have", async () => {
        renderDrawer({
            ...LAYER,
            createdBy: null,
            updatedAt: null,
            updatedBy: null,
            lastEventAt: null,
        });

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).queryByText(i18n.t("common.updatedAt"))).not.toBeInTheDocument();
        expect(within(drawer).queryByText(i18n.t("common.lastEventAt"))).not.toBeInTheDocument();
        expect(within(drawer).queryByText(i18n.t("common.createdBy"))).not.toBeInTheDocument();
    });
});

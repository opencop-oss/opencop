// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyLayerInfo } from "@opencop/api";
import { API_BASE, HttpResponse, http, render, screen, server, userEvent, within } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import {
    TABLE_COLUMNS_STORAGE_KEY,
    useTableColumnsStore,
} from "~/features/admin/table-columns.store";
import { MyLayers } from "./MyLayers";

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

const LAYERS: MyLayerInfo[] = [
    {
        layerId: "active",
        layerName: "Active Ops",
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
        createdAt: "2026-10-21T16:41:20Z",
        updatedAt: "2026-10-21T17:10:15Z",
        lastEventAt: "2026-10-22T08:00:00Z",
    },
    {
        layerId: "idle",
        layerName: "Idle Ops",
        role: "MEMBER",
        accessScope: "PRIVATE",
        defaultVisible: true,
        createdAt: "2026-10-21T16:40:49Z",
        updatedAt: "2026-10-21T16:40:49Z",
        lastEventAt: null,
    },
];

const TIMESTAMP = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} /;

beforeEach(() => {
    useTableColumnsStore.setState({ hiddenColumnsByTable: {} });
    localStorage.removeItem(TABLE_COLUMNS_STORAGE_KEY);
    server.use(
        http.get(`${API_BASE}/me/layers`, () => HttpResponse.json(LAYERS)),
        http.get(`${API_BASE}/me/layer-groups`, () => HttpResponse.json([])),
    );
});

describe("MyLayers audit columns", () => {
    it("shows created, updated and last-event timestamps, with a dash for a never-active layer", async () => {
        render(<MyLayers />);

        const activeRow = (await screen.findByText("Active Ops")).closest("tr")!;
        expect(
            screen.getByRole("columnheader", { name: i18n.t("common.createdAt") }),
        ).toBeVisible();
        expect(
            screen.getByRole("columnheader", { name: i18n.t("common.updatedAt") }),
        ).toBeVisible();
        expect(
            screen.getByRole("columnheader", { name: new RegExp(i18n.t("common.lastEventAt")) }),
        ).toBeVisible();
        // Pattern, not values: the cells render in the viewer's own timezone.
        expect(within(activeRow).getAllByText(TIMESTAMP)).toHaveLength(3);

        const idleRow = screen.getByText("Idle Ops").closest("tr")!;
        expect(within(idleRow).getAllByText(TIMESTAMP)).toHaveLength(2);
        expect(within(idleRow).getByText("-")).toBeVisible();
    });

    it("keeps a never-active layer last when Last Event is sorted either way", async () => {
        const user = userEvent.setup();
        render(<MyLayers />);
        await screen.findByText("Active Ops");
        const lastEventHeader = screen.getByRole("columnheader", {
            name: new RegExp(i18n.t("common.lastEventAt")),
        });
        const rowOrder = () =>
            screen
                .getAllByRole("row")
                .map((row) => row.textContent ?? "")
                .filter((text) => /Active Ops|Idle Ops/.test(text))
                .map((text) => (text.includes("Idle Ops") ? "idle" : "active"));

        await user.click(lastEventHeader);
        expect(rowOrder()).toEqual(["active", "idle"]);

        await user.click(lastEventHeader);
        expect(rowOrder()).toEqual(["active", "idle"]);
    });

    it("hides a column picked off in the Columns popover and remembers it for this table only", async () => {
        const user = userEvent.setup();
        render(<MyLayers />);
        await screen.findByText("Active Ops");

        await user.click(
            screen.getByRole("button", { name: i18n.t("resourceTable.chooseColumns") }),
        );
        await user.click(await screen.findByRole("checkbox", { name: i18n.t("common.updatedAt") }));

        expect(
            screen.queryByRole("columnheader", { name: i18n.t("common.updatedAt") }),
        ).not.toBeInTheDocument();
        expect(useTableColumnsStore.getState().hiddenColumnsByTable).toEqual({
            "my-layers": ["updatedAt"],
        });
    });
});

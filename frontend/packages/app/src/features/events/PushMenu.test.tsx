// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource } from "@opencop/api";
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, userEvent } from "~/test";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { PushMenu } from "./PushMenu";

vi.mock("./use-event-details.hook", () => ({
    useEventDetails: () => ({ details: { source: "s-kept", displayName: "Event" } }),
}));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));
vi.mock("./use-event-push.hook", () => ({ useEventPush: () => vi.fn() }));

function writableSource(id: string, name: string): MyDataSource {
    return {
        serverId: id,
        serverName: name,
        serverState: "CONNECTED",
        role: "OWNER",
        directRole: "OWNER",
        via: "DIRECT",
        access: "RW",
        transportType: "TCP",
        defaultVisible: true,
    };
}

function renderMenu() {
    return render(<PushMenu />, {
        queryData: {
            [JSON.stringify(dataSourceQueries.myList().queryKey)]: [
                writableSource("s-kept", "Kept Source"),
                writableSource("s-removed", "Removed Source"),
            ],
        },
    });
}

afterEach(() => useFilterPanelStore.setState({ excludedIds: [] }));

describe("PushMenu — panel exclusion", () => {
    it("lists every writable data source when nothing is removed", async () => {
        renderMenu();
        await userEvent.click(screen.getByRole("button"));

        expect(await screen.findByRole("menuitem", { name: "Removed Source" })).toBeInTheDocument();
    });

    it("does not offer a data source the user removed from the map panel", async () => {
        useFilterPanelStore.setState({ excludedIds: ["s-removed"] });
        renderMenu();
        await userEvent.click(screen.getByRole("button"));

        expect(await screen.findByRole("menuitem", { name: "Kept Source" })).toBeInTheDocument();
        expect(screen.queryByRole("menuitem", { name: "Removed Source" })).not.toBeInTheDocument();
    });
});

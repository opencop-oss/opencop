// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DataSourceInfo, MyDataSource } from "@opencop/api";
import { API_BASE, HttpResponse, http, render, screen, server, userEvent, waitFor } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { MyDataSourceEditContent } from "./MyDataSourceEditContent";

const SUMMARY: MyDataSource = {
    serverId: "source-1",
    serverName: "Summary name",
    serverState: "CONNECTED",
    role: "OWNER",
    directRole: "OWNER",
    via: "DIRECT",
    access: "R",
    transportType: "TCP",
    defaultVisible: true,
};

const CONNECTION: DataSourceInfo = {
    id: "source-1",
    name: "Persisted detail name",
    state: "CONNECTED",
    access: "W",
    transportType: "TCP",
    defaultVisible: false,
};

describe("MyDataSourceEditContent", () => {
    it("initializes exact persisted detail and omits unchanged access from a name-only patch", async () => {
        const bodies: unknown[] = [];
        server.use(
            http.patch(`${API_BASE}/connections/source-1`, async ({ request }) => {
                bodies.push(await request.json());
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const user = userEvent.setup();
        const onDone = vi.fn();

        render(
            <MyDataSourceEditContent
                dataSource={SUMMARY}
                connection={CONNECTION}
                mapperName="Track mapper"
                onDone={onDone}
            />,
        );

        const name = screen.getByRole("textbox", { name: "Name" });
        expect(name).toHaveValue("Persisted detail name");
        expect(screen.getByRole("radio", { name: "Write" })).toBeChecked();
        expect(screen.getByRole("checkbox", { name: "Visible by default" })).not.toBeChecked();
        expect(screen.getByRole("textbox", { name: "Mapper" })).toHaveValue("Track mapper");

        await user.clear(name);
        await user.type(name, "Direct owner rename");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(onDone).toHaveBeenCalledOnce());
        expect(bodies).toEqual([{ name: "Direct owner rename" }]);
    });

    it("keeps a writing source on its stored access, explaining why", () => {
        render(
            <MyDataSourceEditContent
                dataSource={SUMMARY}
                connection={CONNECTION}
                onDone={vi.fn()}
            />,
        );

        expect(screen.queryByRole("textbox", { name: "Mapper" })).not.toBeInTheDocument();
        expect(screen.getByRole("radio", { name: "Write" })).toBeEnabled();
        expect(screen.getByRole("radio", { name: "Read" })).toHaveAccessibleDescription(
            expect.stringContaining(
                "Can't change: write access is set when the source is created.",
            ),
        );
        expect(screen.getByRole("radio", { name: "Read" })).toBeDisabled();
        expect(screen.queryByRole("img")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
    });

    it("states an incompatible stored access without offering to change it for the user", () => {
        render(
            <MyDataSourceEditContent
                dataSource={{ ...SUMMARY, access: "RW", transportType: "UDP" }}
                connection={{ ...CONNECTION, access: "RW", transportType: "UDP" }}
                mapperName="Track mapper"
                onDone={vi.fn()}
            />,
        );

        expect(
            screen.getByRole("img", {
                name: "Read & Write is incompatible with UDP.",
            }),
        ).toBeVisible();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Repair to Read" })).not.toBeInTheDocument();
    });
});

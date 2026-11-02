// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DataSourceInfo } from "@opencop/api";
import { API_BASE, HttpResponse, http, render, screen, server, userEvent, waitFor } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { DataSourceEditContent } from "./DataSourceEditContent";

const SOURCE: DataSourceInfo = {
    id: "source-1",
    name: "Original source",
    state: "CONNECTED",
    access: "R",
    transportType: "TCP",
    defaultVisible: true,
};

describe("DataSourceEditContent", () => {
    it("atomically patches dirty metadata and never requests a lifecycle change", async () => {
        let patchRequests = 0;
        let lifecycleRequests = 0;
        let requestBody: unknown;
        server.use(
            http.patch(`${API_BASE}/connections/source-1`, async ({ request }) => {
                patchRequests += 1;
                requestBody = await request.json();
                return new HttpResponse(null, { status: 204 });
            }),
            http.post(`${API_BASE}/connections/source-1/:action`, () => {
                lifecycleRequests += 1;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const onDone = vi.fn();
        const user = userEvent.setup();

        render(
            <DataSourceEditContent dataSource={SOURCE} mapperName="Track mapper" onDone={onDone} />,
        );

        const name = screen.getByRole("textbox", { name: "Name" });
        await user.clear(name);
        await user.type(name, "Renamed source");
        await user.click(screen.getByRole("checkbox", { name: "Visible by default" }));
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(onDone).toHaveBeenCalledOnce());
        expect(patchRequests).toBe(1);
        expect(requestBody).toEqual({ name: "Renamed source", defaultVisible: false });
        expect(lifecycleRequests).toBe(0);
    });

    it("omits unchanged access and prevents a pristine submission", async () => {
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
            <DataSourceEditContent dataSource={SOURCE} mapperName="Track mapper" onDone={onDone} />,
        );

        const save = screen.getByRole("button", { name: "Save" });
        expect(save).toBeDisabled();
        const name = screen.getByRole("textbox", { name: "Name" });
        await user.clear(name);
        await user.type(name, "Name only");
        await user.click(save);

        await waitFor(() => expect(onDone).toHaveBeenCalledOnce());
        expect(bodies).toEqual([{ name: "Name only" }]);
    });

    it("keeps dirty form state and protects backend detail after a forbidden patch", async () => {
        server.use(
            http.patch(`${API_BASE}/connections/source-1`, () =>
                HttpResponse.json(
                    {
                        code: "CORE-CONNECTION-1006",
                        message: "protected backend validation detail",
                    },
                    { status: 403 },
                ),
            ),
        );
        const user = userEvent.setup();
        const onDone = vi.fn();

        render(
            <DataSourceEditContent dataSource={SOURCE} mapperName="Track mapper" onDone={onDone} />,
        );

        const name = screen.getByRole("textbox", { name: "Name" });
        await user.clear(name);
        await user.type(name, "Still dirty");
        await user.click(screen.getByRole("button", { name: "Save" }));

        expect(await screen.findByText("Data source updates are not permitted.")).toBeVisible();
        expect(screen.queryByText("protected backend validation detail")).not.toBeInTheDocument();
        expect(name).toHaveValue("Still dirty");
        expect(onDone).not.toHaveBeenCalled();
    });

    it.each([
        {
            title: "active duplicate name",
            body: {
                code: "CORE-CONNECTION-1003",
                message: "protected duplicate-name detail",
            },
            expected: "A data source with this name already exists. Choose a different name.",
        },
        {
            title: "archived source",
            body: { message: "protected archived-source detail" },
            expected: "Archived data sources are read-only.",
        },
    ])("distinguishes $title conflicts and retains the form", async ({ body, expected }) => {
        server.use(
            http.patch(`${API_BASE}/connections/source-1`, () =>
                HttpResponse.json(body, { status: 409 }),
            ),
        );
        const user = userEvent.setup();
        const onDone = vi.fn();

        render(
            <DataSourceEditContent dataSource={SOURCE} mapperName="Track mapper" onDone={onDone} />,
        );

        const name = screen.getByRole("textbox", { name: "Name" });
        await user.clear(name);
        await user.type(name, "Conflicting name");
        await user.click(screen.getByRole("button", { name: "Save" }));

        expect(await screen.findByText(expected)).toBeVisible();
        expect(screen.queryByText(body.message)).not.toBeInTheDocument();
        expect(name).toHaveValue("Conflicting name");
        expect(onDone).not.toHaveBeenCalled();
    });

    it("shows the mapper read-only, and omits it when unresolved", () => {
        const { unmount } = render(
            <DataSourceEditContent
                dataSource={SOURCE}
                mapperName="Track mapper"
                onDone={vi.fn()}
            />,
        );
        expect(screen.getByRole("textbox", { name: "Mapper" })).toHaveValue("Track mapper");
        unmount();

        render(<DataSourceEditContent dataSource={SOURCE} onDone={vi.fn()} />);

        expect(screen.queryByRole("textbox", { name: "Mapper" })).not.toBeInTheDocument();
    });

    it("states an incompatible stored access without offering to change it for the user", () => {
        render(
            <DataSourceEditContent
                dataSource={{ ...SOURCE, access: "RW", transportType: "UDP" }}
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

    it("offers no access change, since Core refuses one that makes a source writable", () => {
        render(
            <DataSourceEditContent
                dataSource={SOURCE}
                mapperName="Track mapper"
                onDone={vi.fn()}
            />,
        );

        expect(screen.getByRole("radio", { name: "Read" })).toBeChecked();
        expect(screen.getByRole("radio", { name: "Write" })).toBeDisabled();
        expect(screen.getByRole("radio", { name: "Read & Write" })).toBeDisabled();
    });
});

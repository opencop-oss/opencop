// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource } from "@opencop/api";
import { SideDrawer } from "@opencop/ui";
import { useQuery } from "@tanstack/react-query";
import type { ReactElement } from "react";
import {
    API_BASE,
    HttpResponse,
    http,
    render,
    screen,
    server,
    userEvent,
    waitFor,
    within,
} from "~/test";
import { beforeEach, describe, expect, it } from "vitest";
import { dataSourceQueries } from "../data-sources.queries";
import { MyDataSourceDrawerContent } from "./MyDataSourceDrawerContent";

const SOURCE_ID = "source-1";

const SUMMARY: MyDataSource = {
    serverId: SOURCE_ID,
    serverName: "Owned TCP source",
    serverState: "CONNECTED",
    role: "OWNER",
    directRole: "OWNER",
    via: "DIRECT",
    access: "R",
    transportType: "TCP",
    defaultVisible: true,
};

/**
 * Mirrors `MyDataSources`: the drawer's summary prop is re-derived from the live
 * list query, so an invalidated list genuinely re-renders the drawer here.
 */
function ListDrivenDrawer(): ReactElement | null {
    const { data } = useQuery(dataSourceQueries.myList());
    const dataSource = data?.find((source) => source.serverId === SOURCE_ID);
    if (!dataSource) return null;
    return (
        <SideDrawer open onClose={() => {}}>
            <MyDataSourceDrawerContent
                dataSource={dataSource}
                onOpenModal={() => {}}
                isArchived={false}
            />
        </SideDrawer>
    );
}

describe("MyDataSourceDrawerContent", () => {
    beforeEach(() => {
        server.use(http.get(`${API_BASE}/profile/mapping`, () => HttpResponse.json([])));
    });

    it("keeps the acknowledged name when the detail readback fails after a save", async () => {
        let persisted = "Owned TCP source";
        let detailReads = 0;

        server.use(
            http.get(`${API_BASE}/me/sources`, () =>
                HttpResponse.json([{ ...SUMMARY, serverName: persisted }]),
            ),
            http.patch(`${API_BASE}/connections/${SOURCE_ID}`, async ({ request }) => {
                const body = (await request.json()) as { name?: string };
                if (body.name) persisted = body.name;
                return new HttpResponse(null, { status: 204 });
            }),
            http.get(`${API_BASE}/connections/${SOURCE_ID}`, () => {
                detailReads += 1;
                // The drawer opens on a healthy read; every readback after the
                // PATCH fails, which `invalidateQueries` swallows.
                if (detailReads > 1) return new HttpResponse(null, { status: 500 });
                return HttpResponse.json({
                    id: SOURCE_ID,
                    name: "Owned TCP source",
                    state: "CONNECTED",
                    access: "R",
                    transportType: "TCP",
                    defaultVisible: true,
                    setup: { kind: "TCP", mapperId: "mapper-1", values: {} },
                });
            }),
            http.get(`${API_BASE}/sources/${SOURCE_ID}/users`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/sources/${SOURCE_ID}/layers`, () => HttpResponse.json([])),
        );

        const user = userEvent.setup();
        render(<ListDrivenDrawer />);

        await screen.findByRole("dialog");
        await waitFor(() => expect(detailReads).toBe(1));

        await user.click(screen.getByRole("button", { name: "Edit Data Source" }));
        const name = await screen.findByRole("textbox", { name: "Name" });
        await user.clear(name);
        await user.type(name, "Renamed source");
        await user.click(screen.getByRole("button", { name: "Save" }));

        // The save is acknowledged and edit mode closes.
        await waitFor(() =>
            expect(screen.getByRole("button", { name: "Edit Data Source" })).toBeVisible(),
        );
        expect(persisted).toBe("Renamed source");

        // The readback failed, so re-entering edit mode must start from the value the
        // server acknowledged — never the pre-patch one.
        await waitFor(() => expect(detailReads).toBeGreaterThan(1));
        await user.click(screen.getByRole("button", { name: "Edit Data Source" }));
        expect(await screen.findByRole("textbox", { name: "Name" })).toHaveValue("Renamed source");
    });

    it("reads the integration and its unavailable status from the connection detail", async () => {
        server.use(
            http.get(`${API_BASE}/me/sources`, () =>
                // A stale list state: the drawer must take state and reason from the detail.
                HttpResponse.json([{ ...SUMMARY, serverState: "CONNECTED" }]),
            ),
            http.get(`${API_BASE}/connections/${SOURCE_ID}`, () =>
                HttpResponse.json({
                    id: SOURCE_ID,
                    name: "Owned TCP source",
                    state: "FAILED",
                    stateReason: "INTEGRATION_UNAVAILABLE",
                    integration: { integrationId: "tak-id", schemaVersion: 1 },
                    access: "R",
                    transportType: "TCP",
                    defaultVisible: true,
                }),
            ),
            http.get(`${API_BASE}/integrations`, () =>
                HttpResponse.json([
                    {
                        integrationId: "tak-id",
                        schemaVersion: 1,
                        name: "TAK",
                        description: "",
                        available: false,
                    },
                ]),
            ),
        );

        render(<ListDrivenDrawer />);

        expect(await screen.findByText("TAK")).toBeVisible();
        expect(screen.getByText("Unavailable")).toBeInTheDocument();
        expect(screen.getByRole("status")).toHaveTextContent("Integration unavailable");
        expect(await screen.findByText("FAILED")).toBeVisible();
        expect(screen.queryByText("CONNECTED")).toBeNull();
    });

    it("names a mapper the viewer's list leaves out instead of showing none", async () => {
        // The non-admin mapper list leaves out archived mappers (beforeEach serves none).
        server.use(
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([SUMMARY])),
            http.get(`${API_BASE}/connections/${SOURCE_ID}`, () =>
                HttpResponse.json({
                    id: SOURCE_ID,
                    name: "Owned TCP source",
                    state: "CONNECTED",
                    access: "R",
                    transportType: "TCP",
                    defaultVisible: true,
                    setup: { kind: "TCP", mapperId: "archived-mapper", values: {} },
                }),
            ),
            http.get(`${API_BASE}/sources/${SOURCE_ID}/users`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/sources/${SOURCE_ID}/layers`, () => HttpResponse.json([])),
        );
        render(<ListDrivenDrawer />);

        const drawer = await screen.findByRole("dialog");
        expect(await within(drawer).findByText("Archived or not available to you")).toBeVisible();
    });

    it("shows a writing source's egress mapper from the egress list, or names it unavailable", async () => {
        server.use(
            http.get(`${API_BASE}/me/sources`, () =>
                HttpResponse.json([{ ...SUMMARY, access: "W" }]),
            ),
            http.get(`${API_BASE}/connections/${SOURCE_ID}`, () =>
                HttpResponse.json({
                    id: SOURCE_ID,
                    name: "Owned TCP source",
                    state: "CONNECTED",
                    access: "W",
                    transportType: "TCP",
                    defaultVisible: true,
                    setup: { kind: "TCP", mapperId: null, egressMapperId: "egress-1", values: {} },
                }),
            ),
            http.get(`${API_BASE}/profile/mapping`, ({ request }) =>
                HttpResponse.json(
                    new URL(request.url).searchParams.get("qualifier") === "EGRESS"
                        ? [
                              {
                                  id: "egress-1",
                                  name: "COT egress",
                                  description: "",
                                  dynamicMapper: {},
                              },
                          ]
                        : [],
                ),
            ),
            http.get(`${API_BASE}/sources/${SOURCE_ID}/users`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/sources/${SOURCE_ID}/layers`, () => HttpResponse.json([])),
        );
        const user = userEvent.setup();
        render(<ListDrivenDrawer />);

        const drawer = await screen.findByRole("dialog");
        expect(await within(drawer).findByText("COT egress")).toBeVisible();
        expect(within(drawer).getByText("Egress Mapper")).toBeVisible();

        await user.click(within(drawer).getByRole("button", { name: "Edit Data Source" }));
        expect(within(drawer).getByRole("textbox", { name: "Egress Mapper" })).toHaveValue(
            "COT egress",
        );
        expect(within(drawer).queryByRole("textbox", { name: "Mapper" })).toBeNull();
    });

    describe("write capability", () => {
        /** An owned TCP Read & Write source; `detail` answers each read of its connection. */
        function serveWriteSource(detail: () => Response) {
            server.use(
                http.get(`${API_BASE}/me/sources`, () =>
                    HttpResponse.json([{ ...SUMMARY, access: "RW" }]),
                ),
                http.get(`${API_BASE}/connections/${SOURCE_ID}`, detail),
                http.get(`${API_BASE}/sources/${SOURCE_ID}/users`, () => HttpResponse.json([])),
                http.get(`${API_BASE}/sources/${SOURCE_ID}/layers`, () => HttpResponse.json([])),
            );
        }
        const WRITER = {
            id: SOURCE_ID,
            name: "Owned TCP source",
            state: "CONNECTED",
            access: "RW",
            transportType: "TCP",
            defaultVisible: true,
            setup: { kind: "TCP", mapperId: "mapper-1", egressMapperId: "mapper-2", values: {} },
        };
        const UNVERIFIED = "Couldn't check write support: this source's details didn't load.";

        it("is proven by the stored egress mapper, even when the mapper isn't visible", async () => {
            // The non-admin mapper list leaves out archived mappers (beforeEach serves none).
            let detailReads = 0;
            serveWriteSource(() => {
                detailReads += 1;
                return HttpResponse.json(WRITER);
            });
            const { queryClient } = render(<ListDrivenDrawer />);

            const drawer = await screen.findByRole("dialog");
            expect(await within(drawer).findByText("Read & Write")).toBeVisible();
            await waitFor(() =>
                expect(
                    queryClient.getQueryState(dataSourceQueries.detail(SOURCE_ID).queryKey)?.status,
                ).toBe("success"),
            );
            expect(detailReads).toBe(1);
            expect(
                within(drawer).queryByRole("img", { name: /incompatible|write support/i }),
            ).not.toBeInTheDocument();
            expect(within(drawer).queryByText(/no mapper on record/)).not.toBeInTheDocument();
            expect(within(drawer).queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
        });

        it("is unverified when the detail never loaded, and Retry reloads it", async () => {
            let detailReads = 0;
            serveWriteSource(() => {
                detailReads += 1;
                return detailReads === 1
                    ? new HttpResponse(null, { status: 500 })
                    : HttpResponse.json(WRITER);
            });
            const user = userEvent.setup();
            render(<ListDrivenDrawer />);

            const drawer = await screen.findByRole("dialog");
            expect(await within(drawer).findByRole("img", { name: UNVERIFIED })).toBeVisible();

            await user.click(within(drawer).getByRole("button", { name: "Retry" }));
            await waitFor(() =>
                expect(
                    within(drawer).queryByRole("img", { name: UNVERIFIED }),
                ).not.toBeInTheDocument(),
            );
            expect(within(drawer).queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
        });
    });
});

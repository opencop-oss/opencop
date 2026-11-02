// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DataSourceInfo, IntegrationConnectionSetup, TcpFramingSelection } from "@opencop/api";
import { SideDrawer } from "@opencop/ui";
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
import { describe, expect, it } from "vitest";
import { integrationQueries } from "../integrations.queries";
import { DataSourceDrawerContent } from "./DataSourceDrawerContent";

const SOURCE: DataSourceInfo = {
    id: "admin-source",
    name: "Admin TCP source",
    state: "CONNECTED",
    access: "W",
    transportType: "TCP",
    defaultVisible: true,
    setup: {
        kind: "TCP",
        integration: null,
        extractorId: null,
        values: {},
        // A Write-only connection has no ingress mapper; Core stores it with its egress mapper.
        mapperId: null,
        egressMapperId: "egress-mapper",
        framing: { type: "CUSTOM", mode: "delimiterSplit", delimiter: "\n" },
    },
};

const MAPPER_NAMES = new Map([
    ["read-only-mapper", "Read-only Mapper"],
    ["egress-mapper", "Egress COT"],
]);

describe("DataSourceDrawerContent", () => {
    it("resolves protocol, access and mapper, proving write support from the setup", async () => {
        let detailRequests = 0;
        server.use(
            http.get(`${API_BASE}/connections/:sourceId`, () => {
                detailRequests += 1;
                return HttpResponse.json({});
            }),
        );
        const user = userEvent.setup();

        render(
            <SideDrawer open onClose={() => {}}>
                <DataSourceDrawerContent
                    dataSource={SOURCE}
                    onOpenModal={() => {}}
                    mapperNames={MAPPER_NAMES}
                />
            </SideDrawer>,
        );

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByText("Protocol")).toBeVisible();
        expect(within(drawer).getByText("TCP")).toBeVisible();
        expect(within(drawer).getByText("Access Mode")).toBeVisible();
        expect(within(drawer).getByText("Write")).toBeVisible();
        expect(within(drawer).getByText("Egress Mapper")).toBeVisible();
        expect(within(drawer).getByRole("link", { name: "Egress COT" })).toHaveAttribute(
            "href",
            "/admin/mappers?id=egress-mapper&type=mapper",
        );
        expect(within(drawer).queryByRole("link", { name: "Read-only Mapper" })).toBeNull();
        expect(
            within(drawer).queryByRole("img", { name: /incompatible|write support/i }),
        ).not.toBeInTheDocument();
        expect(within(drawer).queryByText(/no mapper on record/)).not.toBeInTheDocument();
        expect(within(drawer).queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
        expect(within(drawer).queryByRole("alert")).not.toBeInTheDocument();
        await waitFor(() => expect(detailRequests).toBe(0));

        await user.click(within(drawer).getByRole("button", { name: "Edit Data Source" }));
        const write = within(drawer).getByRole("radio", { name: "Write" });
        expect(write).toBeChecked();
        expect(write).toBeEnabled();
        expect(within(drawer).getByRole("radio", { name: "Read" })).toBeDisabled();
        expect(within(drawer).getByRole("radio", { name: "Read & Write" })).toBeDisabled();
    });

    it("shows both mappers of a Read & Write source, each labelled by direction", async () => {
        const user = userEvent.setup();
        render(
            <SideDrawer open onClose={() => {}}>
                <DataSourceDrawerContent
                    dataSource={{
                        ...SOURCE,
                        access: "RW",
                        setup: {
                            kind: "TCP",
                            integration: null,
                            extractorId: null,
                            values: {},
                            mapperId: "read-only-mapper",
                            egressMapperId: "egress-mapper",
                            framing: { type: "CUSTOM", mode: "delimiterSplit", delimiter: "\n" },
                        },
                    }}
                    onOpenModal={() => {}}
                    mapperNames={MAPPER_NAMES}
                />
            </SideDrawer>,
        );

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByText("Mapper")).toBeVisible();
        expect(within(drawer).getByRole("link", { name: "Read-only Mapper" })).toBeVisible();
        expect(within(drawer).getByText("Egress Mapper")).toBeVisible();
        expect(within(drawer).getByRole("link", { name: "Egress COT" })).toBeVisible();

        await user.click(within(drawer).getByRole("button", { name: "Edit Data Source" }));
        expect(within(drawer).getByRole("textbox", { name: "Mapper" })).toHaveValue(
            "Read-only Mapper",
        );
        expect(within(drawer).getByRole("textbox", { name: "Egress Mapper" })).toHaveValue(
            "Egress COT",
        );
    });

    it("renders audit rows with createdBy/updatedBy resolved to usernames", async () => {
        server.use(
            http.get(`${API_BASE}/users/:userId`, ({ params }) =>
                HttpResponse.json({
                    id: params.userId,
                    username: params.userId === "author-id" ? "jsmith" : "rlee",
                }),
            ),
        );

        render(
            <SideDrawer open onClose={() => {}}>
                <DataSourceDrawerContent
                    dataSource={{
                        ...SOURCE,
                        createdAt: "2026-03-04T05:06:07Z",
                        createdBy: "author-id",
                        updatedAt: "2026-03-05T08:09:10Z",
                        updatedBy: "editor-id",
                        lastEventAt: "2026-03-06T11:12:13Z",
                    }}
                    onOpenModal={() => {}}
                    mapperNames={MAPPER_NAMES}
                />
            </SideDrawer>,
        );

        const drawer = await screen.findByRole("dialog");
        // The raw ids render first, then flip to usernames once the lookups resolve.
        expect(await within(drawer).findByText("jsmith")).toBeVisible();
        expect(within(drawer).getByText("rlee")).toBeVisible();
        expect(within(drawer).getByText("Created By")).toBeVisible();
        expect(within(drawer).getByText("Updated By")).toBeVisible();
        expect(within(drawer).getByText("Last Event")).toBeVisible();
        // Created/updated/last-event all render a full timestamp, not a date-only value. Matched
        // by shape rather than literal — the value is rendered in the viewer's zone.
        expect(within(drawer).getAllByText(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)).toHaveLength(3);
    });

    it("omits every audit row for a source that carries no audit metadata", async () => {
        render(
            <SideDrawer open onClose={() => {}}>
                <DataSourceDrawerContent
                    dataSource={SOURCE}
                    onOpenModal={() => {}}
                    mapperNames={MAPPER_NAMES}
                />
            </SideDrawer>,
        );

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByText("Protocol")).toBeVisible();
        for (const label of [
            "Created At",
            "Created By",
            "Last Updated",
            "Updated By",
            "Last Event",
        ])
            expect(within(drawer).queryByText(label)).not.toBeInTheDocument();
    });

    it("says the capability check doesn't apply, with no retry, for a source with no setup", async () => {
        render(
            <SideDrawer open onClose={() => {}}>
                <DataSourceDrawerContent
                    dataSource={{ ...SOURCE, setup: null }}
                    onOpenModal={() => {}}
                    mapperNames={MAPPER_NAMES}
                />
            </SideDrawer>,
        );

        const drawer = await screen.findByRole("dialog");
        expect(
            within(drawer).getByText("Not applicable: this source has no mapper on record."),
        ).toBeVisible();
        expect(within(drawer).queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
    });

    describe("integration", () => {
        const TAK = { integrationId: "tak-id", schemaVersion: 1 };
        const CATALOG = [
            {
                integrationId: "tak-id",
                schemaVersion: 1,
                name: "TAK",
                description: "",
                available: true,
            },
        ];

        function renderDrawer(dataSource: DataSourceInfo) {
            server.use(http.get(`${API_BASE}/integrations`, () => HttpResponse.json(CATALOG)));
            render(
                <SideDrawer open onClose={() => {}}>
                    <DataSourceDrawerContent
                        dataSource={dataSource}
                        onOpenModal={() => {}}
                        mapperNames={MAPPER_NAMES}
                    />
                </SideDrawer>,
            );
        }

        it("names the integration a running connection is bound to, with no warning", async () => {
            renderDrawer({ ...SOURCE, integration: TAK, stateReason: null });

            expect(await screen.findByText("TAK")).toBeVisible();
            expect(await screen.findByText("Available")).toBeInTheDocument();
            expect(screen.queryByText("Unavailable")).toBeNull();
            expect(screen.getByRole("status")).toBeEmptyDOMElement();
        });

        it("keeps an unavailable integration visible and explains why the source is not running", async () => {
            renderDrawer({
                ...SOURCE,
                state: "FAILED",
                integration: TAK,
                stateReason: "INTEGRATION_UNAVAILABLE",
            });

            expect(await screen.findByText("TAK")).toBeVisible();
            expect(screen.getByText("Unavailable")).toBeInTheDocument();
            expect(screen.getByRole("status")).toHaveTextContent("Integration unavailable");
            expect(
                screen.getByText(
                    /^TAK isn't available in this deployment, so this data source isn't running\./,
                ),
            ).toBeVisible();
        });

        it("shows the stored identity when the catalog no longer lists the version", async () => {
            renderDrawer({
                ...SOURCE,
                state: "FAILED",
                integration: { integrationId: "retired-id", schemaVersion: 4 },
                stateReason: "INTEGRATION_UNAVAILABLE",
            });

            expect(
                await screen.findAllByText("Integration retired-id (version 4)"),
            ).not.toHaveLength(0);
            expect(screen.getByRole("status")).toHaveTextContent("Integration unavailable");
        });
    });

    describe("framing", () => {
        const TAK = { integrationId: "tak-id", schemaVersion: 1 };
        const TAK_SETUP_URL = `${API_BASE}/integrations/tak-id/versions/1/connection/setup`;
        const TAK_SETUP: IntegrationConnectionSetup = {
            integrationId: "tak-id",
            schemaVersion: 1,
            protocols: [
                {
                    kind: "TCP",
                    accessModes: ["R", "W", "RW"],
                    extractors: [],
                    ingress: [{ type: "EXACT_PAIR", mapperId: "cot-mapper", framingId: "cot" }],
                    egress: null,
                    framings: [{ id: "cot", mode: "delimiterInclude" }],
                },
            ],
        };
        const CATALOG = [
            {
                integrationId: "tak-id",
                schemaVersion: 1,
                name: "TAK",
                description: "",
                available: true,
            },
        ];

        function tcpSource(framing: TcpFramingSelection): DataSourceInfo {
            return {
                ...SOURCE,
                access: "R",
                integration: TAK,
                setup: {
                    kind: "TCP",
                    integration: { id: TAK.integrationId, schemaVersion: TAK.schemaVersion },
                    extractorId: null,
                    values: {},
                    mapperId: "read-only-mapper",
                    egressMapperId: null,
                    framing,
                },
            };
        }

        async function renderDrawer(dataSource: DataSourceInfo) {
            server.use(http.get(`${API_BASE}/integrations`, () => HttpResponse.json(CATALOG)));
            const { queryClient } = render(
                <SideDrawer open onClose={() => {}}>
                    <DataSourceDrawerContent
                        dataSource={dataSource}
                        onOpenModal={() => {}}
                        mapperNames={MAPPER_NAMES}
                    />
                </SideDrawer>,
            );
            return { drawer: await screen.findByRole("dialog"), queryClient };
        }

        /** The value shown beside the drawer's Framing label. */
        function framingValue(drawer: HTMLElement): string | null | undefined {
            return within(drawer).getByText("Framing").nextElementSibling?.textContent;
        }

        it("names an integration framing's provider and its mode from the integration's setup", async () => {
            server.use(http.get(TAK_SETUP_URL, () => HttpResponse.json(TAK_SETUP)));

            const { drawer } = await renderDrawer(
                tcpSource({ type: "INTEGRATION", framingId: "cot" }),
            );

            await waitFor(() =>
                expect(framingValue(drawer)).toBe("Provided by TAK (Delimiter Include)"),
            );
        });

        it.each([
            [
                "can't be loaded",
                () =>
                    HttpResponse.json(
                        { code: "INTERNAL_ERROR", message: "Unavailable" },
                        { status: 500 },
                    ),
            ],
            [
                "declares only another framing",
                () =>
                    HttpResponse.json({
                        ...TAK_SETUP,
                        protocols: [
                            {
                                ...TAK_SETUP.protocols[0],
                                framings: [{ id: "other", mode: "passthrough" }],
                            },
                        ],
                    }),
            ],
        ])("names the provider alone when the integration's setup %s", async (_, respond) => {
            server.use(http.get(TAK_SETUP_URL, respond));

            const { drawer, queryClient } = await renderDrawer(
                tcpSource({ type: "INTEGRATION", framingId: "cot" }),
            );

            const setupKey = integrationQueries.connectionSetup("tak-id", 1).queryKey;
            await waitFor(() =>
                expect(queryClient.getQueryState(setupKey)?.status).not.toBe("pending"),
            );
            await waitFor(() => expect(framingValue(drawer)).toBe("Provided by TAK"));
        });

        it("names the provider alone, without asking for the setup, when the integration is unavailable", async () => {
            // No handler serves the setup, so requesting it would fail the test.
            const { drawer } = await renderDrawer({
                ...tcpSource({ type: "INTEGRATION", framingId: "cot" }),
                state: "FAILED",
                stateReason: "INTEGRATION_UNAVAILABLE",
            });

            await waitFor(() => expect(framingValue(drawer)).toBe("Provided by TAK"));
        });

        it("shows a custom framing's mode and its delimiter as typed", async () => {
            const { drawer } = await renderDrawer(
                tcpSource({ type: "CUSTOM", mode: "delimiterSplit", delimiter: "</event>\n" }),
            );

            expect(framingValue(drawer)).toBe("Delimiter Split (</event>\\n)");
        });

        it("shows a custom framing with no delimiter by its mode alone", async () => {
            const { drawer } = await renderDrawer(
                tcpSource({ type: "CUSTOM", mode: "passthrough" }),
            );

            expect(framingValue(drawer)).toBe("Pass-through");
        });

        it.each(["UDP", "RTSP", "KAFKA"] as const)(
            "shows no framing for a %s connection",
            async (kind) => {
                const { drawer } = await renderDrawer({
                    ...SOURCE,
                    access: "R",
                    transportType: kind,
                    integration: TAK,
                    setup: {
                        kind,
                        integration: { id: TAK.integrationId, schemaVersion: 1 },
                        extractorId: null,
                        values: {},
                        mapperId: "read-only-mapper",
                    },
                });

                expect(
                    within(drawer).getByRole("link", { name: "Read-only Mapper" }),
                ).toBeVisible();
                expect(within(drawer).queryByText("Framing")).not.toBeInTheDocument();
            },
        );
    });
});

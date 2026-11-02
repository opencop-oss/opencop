// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource } from "@opencop/api";
import {
    API_BASE,
    HttpResponse,
    createTestUser,
    fireEvent,
    http,
    render,
    screen,
    server,
    userEvent,
    waitFor,
    within,
} from "~/test";
import type { ObservedLocation } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { MyDataSources } from "./MyDataSources";

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

const SOURCES: MyDataSource[] = [
    {
        serverId: "tcp-source",
        serverName: "TCP Read & Write",
        serverState: "CONNECTED",
        role: "OWNER",
        directRole: "OWNER",
        via: "DIRECT",
        access: "RW",
        transportType: "TCP",
        defaultVisible: true,
    },
    {
        serverId: "udp-source",
        serverName: "UDP Write",
        serverState: "CONNECTED",
        role: "OWNER",
        directRole: "OWNER",
        via: "DIRECT",
        access: "W",
        transportType: "UDP",
        defaultVisible: true,
    },
];

/** The TCP source's detail, decoding with `mapper-1`. */
function tcpConnection(name = "TCP Read & Write") {
    return {
        id: "tcp-source",
        name,
        state: "CONNECTED",
        access: "RW",
        transportType: "TCP",
        defaultVisible: true,
        setup: {
            kind: "TCP",
            integration: null,
            extractorId: null,
            values: {},
            mapperId: "mapper-1",
            // Core stores a writing connection only with its egress mapper.
            egressMapperId: "mapper-2",
            framing: null,
        },
    };
}

/** `mapper-1` as the mapper list returns it. */
function mapper1(name: string) {
    return { id: "mapper-1", name, description: "", dynamicMapper: {} };
}

describe("MyDataSources", () => {
    beforeEach(() => {
        server.use(http.get(`${API_BASE}/profile/mapping`, () => HttpResponse.json([])));
    });

    afterEach(async () => {
        await i18n.changeLanguage("en");
    });

    it("renders exact protocol and access labels from the source summary response", async () => {
        server.use(http.get(`${API_BASE}/me/sources`, () => HttpResponse.json(SOURCES)));

        render(<MyDataSources />);

        expect(await screen.findByRole("columnheader", { name: "Protocol" })).toBeVisible();
        expect(screen.getByRole("columnheader", { name: "Access Mode" })).toBeVisible();

        const tcpRow = (await screen.findByText("TCP Read & Write")).closest("tr");
        expect(tcpRow).not.toBeNull();
        expect(within(tcpRow!).getByText("TCP")).toBeVisible();
        expect(within(tcpRow!).getByText("Read & Write")).toBeVisible();

        const udpRow = screen.getByText("UDP Write").closest("tr");
        expect(udpRow).not.toBeNull();
        expect(within(udpRow!).getByText("UDP")).toBeVisible();
        expect(within(udpRow!).getByText("Write")).toBeVisible();
    });

    it("shows created, updated and last-event columns, dashing a never-active source", async () => {
        server.use(
            http.get(`${API_BASE}/me/sources`, () =>
                HttpResponse.json([
                    {
                        ...SOURCES[0],
                        createdAt: "2026-10-21T16:41:20Z",
                        updatedAt: "2026-10-21T17:10:15Z",
                        lastEventAt: "2026-10-22T08:00:00Z",
                    },
                    {
                        ...SOURCES[1],
                        createdAt: "2026-10-21T16:40:49Z",
                        updatedAt: "2026-10-21T16:40:49Z",
                        lastEventAt: null,
                    },
                ]),
            ),
        );

        render(<MyDataSources />);

        const tcpRow = (await screen.findByText("TCP Read & Write")).closest("tr")!;
        expect(
            screen.getByRole("columnheader", { name: i18n.t("common.createdAt") }),
        ).toBeVisible();
        expect(
            screen.getByRole("columnheader", { name: i18n.t("common.updatedAt") }),
        ).toBeVisible();
        // Pattern, not values: the cells render in the viewer's own timezone.
        expect(within(tcpRow).getAllByText(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} /)).toHaveLength(
            3,
        );

        const udpRow = screen.getByText("UDP Write").closest("tr")!;
        expect(within(udpRow).getAllByText(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} /)).toHaveLength(
            2,
        );
        expect(within(udpRow).getByText("-")).toBeVisible();
    });

    it("lists the audit rows in the drawer, resolving who created the source", async () => {
        server.use(
            http.get(`${API_BASE}/me/sources`, () =>
                HttpResponse.json([
                    {
                        ...SOURCES[0],
                        createdAt: "2026-10-21T16:41:20Z",
                        createdBy: "user-ada",
                        updatedAt: "2026-10-21T17:10:15Z",
                    },
                ]),
            ),
            http.get(`${API_BASE}/connections/:sourceId`, () =>
                HttpResponse.json({
                    id: "tcp-source",
                    name: "TCP Read & Write",
                    state: "CONNECTED",
                    access: "RW",
                    transportType: "TCP",
                    defaultVisible: true,
                    createdAt: "2026-10-21T16:41:20Z",
                    createdBy: "user-ada",
                    updatedAt: "2026-10-21T17:10:15Z",
                }),
            ),
            http.get(`${API_BASE}/users/user-ada`, () =>
                HttpResponse.json({ id: "user-ada", username: "ada" }),
            ),
        );
        const user = userEvent.setup();

        render(<MyDataSources />);
        await user.click(await screen.findByText("TCP Read & Write"));

        const drawer = await screen.findByRole("dialog");
        expect(await within(drawer).findByText(i18n.t("common.createdBy"))).toBeVisible();
        expect(await within(drawer).findByText("ada")).toBeVisible();
        expect(within(drawer).getByText(i18n.t("common.updatedAt"))).toBeVisible();
    });

    it("warns only when transport summary data proves incompatibility without detail fan-out", async () => {
        let listRequests = 0;
        let detailRequests = 0;
        server.use(
            http.get(`${API_BASE}/me/sources`, () => {
                listRequests += 1;
                return HttpResponse.json(SOURCES);
            }),
            http.get(`${API_BASE}/connections/:sourceId`, () => {
                detailRequests += 1;
                return HttpResponse.json({});
            }),
            http.get(`${API_BASE}/profile/mapping`, () => {
                detailRequests += 1;
                return HttpResponse.json([]);
            }),
            http.get(`${API_BASE}/admin/profile/mapping/:mapperId`, () => {
                detailRequests += 1;
                return HttpResponse.json({});
            }),
        );

        render(<MyDataSources />);

        const udpRow = (await screen.findByText("UDP Write")).closest("tr");
        expect(udpRow).not.toBeNull();
        expect(
            within(udpRow!).getByRole("img", {
                name: "Write is incompatible with UDP.",
            }),
        ).toBeVisible();

        const tcpRow = screen.getByText("TCP Read & Write").closest("tr");
        expect(tcpRow).not.toBeNull();
        expect(within(tcpRow!).queryByRole("img")).toBeNull();

        await waitFor(() => expect(listRequests).toBe(1));
        expect(detailRequests).toBe(0);
    });

    it("refuses drawer dismissal while a metadata save is pending so its failure is reported", async () => {
        let releaseResponse!: () => void;
        const responseGate = new Promise<void>((resolve) => {
            releaseResponse = resolve;
        });
        let patchRequests = 0;
        server.use(
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json(SOURCES)),
            http.get(`${API_BASE}/connections/:sourceId`, () =>
                HttpResponse.json({
                    id: "tcp-source",
                    name: "TCP Read & Write",
                    state: "CONNECTED",
                    access: "RW",
                    transportType: "TCP",
                    defaultVisible: true,
                }),
            ),
            http.patch(`${API_BASE}/connections/:sourceId`, async () => {
                patchRequests += 1;
                await responseGate;
                return HttpResponse.json(
                    { code: "CORE-CONNECTION-1004", message: "protected backend detail" },
                    { status: 403 },
                );
            }),
        );
        const user = userEvent.setup();

        render(<MyDataSources />);
        await user.click(await screen.findByText("TCP Read & Write"));
        const drawer = await screen.findByRole("dialog");
        await user.click(await within(drawer).findByRole("button", { name: "Edit Data Source" }));

        const nameField = await within(drawer).findByDisplayValue("TCP Read & Write");
        await user.clear(nameField);
        await user.type(nameField, "Renamed Source");
        await user.click(within(drawer).getByRole("button", { name: "Save" }));
        await waitFor(() => expect(patchRequests).toBe(1));

        const closeTrigger = document.querySelector<HTMLElement>(
            '[data-scope="dialog"][data-part="close-trigger"]',
        );
        if (!closeTrigger) throw new Error("Expected drawer close trigger");
        expect(closeTrigger).toBeDisabled();
        fireEvent.click(closeTrigger);

        await user.keyboard("{Escape}");
        const backdrop = document.querySelector<HTMLElement>(
            '[data-scope="dialog"][data-part="backdrop"]',
        );
        if (!backdrop) throw new Error("Expected drawer backdrop");
        fireEvent.pointerDown(backdrop);
        fireEvent.click(backdrop);

        expect(await screen.findByRole("dialog")).toBeVisible();
        expect(patchRequests).toBe(1);

        releaseResponse();

        expect(
            await within(await screen.findByRole("dialog")).findByText(
                "Data source updates are not permitted.",
            ),
        ).toBeVisible();
        expect(screen.queryByText("protected backend detail")).not.toBeInTheDocument();
        expect(screen.getByDisplayValue("Renamed Source")).toBeVisible();
        expect(patchRequests).toBe(1);
    });

    it("keeps a source opened from the archived listing read-only", async () => {
        const mutatingRequests: string[] = [];
        const archivedSource: MyDataSource = {
            serverId: "archived-source",
            serverName: "Archived TCP",
            serverState: "DISCONNECTED",
            role: "OWNER",
            directRole: "OWNER",
            via: "DIRECT",
            access: "R",
            transportType: "TCP",
            defaultVisible: true,
        };
        server.use(
            http.get(`${API_BASE}/me/sources`, ({ request }) => {
                const visibility = new URL(request.url).searchParams.get("recordVisibility");
                return HttpResponse.json(visibility === "ARCHIVED" ? [archivedSource] : SOURCES);
            }),
            http.get(`${API_BASE}/connections/:sourceId`, () =>
                HttpResponse.json({
                    id: "archived-source",
                    name: "Archived TCP",
                    state: "DISCONNECTED",
                    access: "R",
                    transportType: "TCP",
                    defaultVisible: true,
                }),
            ),
            http.patch(`${API_BASE}/connections/:sourceId`, ({ params }) => {
                mutatingRequests.push(String(params.sourceId));
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const user = userEvent.setup();

        render(<MyDataSources />);

        await user.click(await screen.findByRole("button", { name: "Manage view" }));
        await user.click(await screen.findByRole("menuitem", { name: "Show Archived" }));
        await user.click(await screen.findByText("Archived TCP"));

        const drawer = await screen.findByRole("dialog");
        expect(
            within(drawer).queryByRole("button", { name: "Edit Data Source" }),
        ).not.toBeInTheDocument();
        expect(within(drawer).queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
        expect(within(drawer).getByRole("checkbox", { name: "Visible by default" })).toBeDisabled();
        expect(mutatingRequests).toEqual([]);
    });

    it("opens one source with exactly its connection detail and its mapper", async () => {
        const connectionDetailRequests: string[] = [];
        server.use(
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json(SOURCES)),
            http.get(`${API_BASE}/connections/:sourceId`, ({ params }) => {
                connectionDetailRequests.push(String(params.sourceId));
                return HttpResponse.json(tcpConnection());
            }),
            http.get(`${API_BASE}/profile/mapping`, () =>
                HttpResponse.json([mapper1("Bidirectional Mapper")]),
            ),
        );
        const user = userEvent.setup();

        render(<MyDataSources />);
        await user.click(await screen.findByText("TCP Read & Write"));

        const drawer = await screen.findByRole("dialog");
        expect(await within(drawer).findByText("Bidirectional Mapper")).toBeVisible();
        expect(within(drawer).getByText("Protocol")).toBeVisible();
        expect(within(drawer).getByText("TCP")).toBeVisible();
        expect(within(drawer).getByText("Access Mode")).toBeVisible();
        expect(within(drawer).getByText("Read & Write")).toBeVisible();
        expect(within(drawer).getByText("Mapper")).toBeVisible();
        expect(within(drawer).queryByRole("alert")).not.toBeInTheDocument();

        await waitFor(() => expect(connectionDetailRequests).toEqual(["tcp-source"]));
        expect(connectionDetailRequests).not.toContain("udp-source");
    });

    it("proves TCP write support from the setup, with no warning, even when the mapper list fails", async () => {
        server.use(
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json(SOURCES)),
            http.get(`${API_BASE}/connections/tcp-source`, () =>
                HttpResponse.json(tcpConnection()),
            ),
            http.get(`${API_BASE}/profile/mapping`, () =>
                HttpResponse.json({ message: "Temporary failure" }, { status: 503 }),
            ),
        );
        const user = userEvent.setup();

        render(<MyDataSources />);
        await user.click(await screen.findByText("TCP Read & Write"));

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByText("Read & Write")).toBeVisible();
        await waitFor(() => expect(within(drawer).getByText("Mapper")).toBeVisible());
        expect(
            within(drawer).queryByRole("img", { name: /incompatible|write support/i }),
        ).not.toBeInTheDocument();
        expect(within(drawer).queryByText(/no mapper on record/)).not.toBeInTheDocument();
        expect(within(drawer).queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
    });

    it.each([
        { role: "OWNER", directRole: "OWNER", via: "DIRECT", admin: false, canEdit: true },
        { role: "OWNER", directRole: "OWNER", via: "BOTH", admin: false, canEdit: true },
        { role: "OWNER", directRole: "VIEWER", via: "BOTH", admin: false, canEdit: false },
        { role: "OWNER", directRole: null, via: "INDIRECT", admin: false, canEdit: false },
        { role: "VIEWER", directRole: null, via: "INDIRECT", admin: true, canEdit: true },
    ] as const)(
        "gates direct-owner operations for effective $role via $via with direct role $directRole and admin=$admin",
        async ({ role, directRole, via, admin, canEdit }) => {
            const source: MyDataSource = {
                ...SOURCES[0],
                serverName: `${SOURCES[0].serverName} ${role}-${directRole}-${via}-${admin}`,
                role,
                directRole,
                via,
            };
            server.use(
                http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([source])),
                http.get(`${API_BASE}/connections/tcp-source`, () =>
                    HttpResponse.json(tcpConnection(source.serverName)),
                ),
                http.get(`${API_BASE}/profile/mapping`, () =>
                    HttpResponse.json([mapper1("Bidirectional Mapper")]),
                ),
            );
            const user = userEvent.setup();

            render(<MyDataSources />, {
                user: createTestUser({ authorities: admin ? ["ROLE_admin"] : [] }),
            });
            // A name-filtered role query recomputes the role and accessible name of every
            // node under the container on each poll, which on a loaded CI runner can burn
            // the whole 1s asyncUtilTimeout in a single attempt and time out before the
            // listing lands. Waiting on the cell's text is the cheap wait the rest of this
            // file uses, and clicking it opens the same row drawer.
            await user.click(await screen.findByText(source.serverName));

            const drawer = await screen.findByRole("dialog");
            const edit = within(drawer).getByRole("button", { name: "Edit Data Source" });
            const defaultVisible = within(drawer).getByRole("checkbox", {
                name: "Visible by default",
            });
            const disconnect = within(drawer).getByRole("button", { name: "Disconnect" });
            if (!canEdit) {
                expect(edit).toBeDisabled();
                expect(defaultVisible).toBeDisabled();
                expect(disconnect).toBeDisabled();
                return;
            }

            expect(edit).toBeEnabled();
            expect(defaultVisible).toBeEnabled();
            expect(disconnect).toBeEnabled();
            await user.click(edit);
            expect(within(drawer).getByRole("radio", { name: "Read & Write" })).toBeChecked();
            expect(within(drawer).getByRole("textbox", { name: "Mapper" })).toHaveValue(
                "Bidirectional Mapper",
            );
        },
    );

    it.each([
        ["en", "Protocol", "Access Mode"],
        ["fr", "Protocole", "Mode d'accès"],
        ["tw", "通訊協定", "存取模式"],
    ] as const)(
        "renders localized source-summary headings in %s",
        async (locale, protocol, access) => {
            await i18n.changeLanguage(locale);
            server.use(http.get(`${API_BASE}/me/sources`, () => HttpResponse.json(SOURCES)));

            render(<MyDataSources />);

            expect(await screen.findByRole("columnheader", { name: protocol })).toBeVisible();
            expect(screen.getByRole("columnheader", { name: access })).toBeVisible();
        },
    );
    // The old object-form `setSearchParams({ id })` replaced the whole query string;
    // `openDrawer` mutates a draft, so unrelated params survive.
    it("preserves unrelated query params when opening a drawer", async () => {
        server.use(
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json(SOURCES)),
            http.get(`${API_BASE}/connections/:sourceId`, () => HttpResponse.json({})),
        );

        const user = userEvent.setup();
        let location: ObservedLocation = { pathname: "/data-sources", search: "?showArchived=1" };

        render(<MyDataSources />, {
            initialRoute: "/data-sources?showArchived=1",
            onLocationChange: (next) => (location = next),
        });

        await user.click(await screen.findByText("TCP Read & Write"));

        await waitFor(() => {
            const params = new URLSearchParams(location.search);
            expect(params.get("id")).toBe("tcp-source");
            expect(params.get("showArchived")).toBe("1");
        });
    });
    // The Archive control lives inside the drawer, and the confirm handler closes the drawer and
    // the dialog in one commit — so the dialog's restore-to-trigger target is gone and focus fell
    // to <body>. The view passes `finalFocusEl` at its table container instead.
    it("returns focus to the table after archiving from the drawer", async () => {
        server.use(
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json(SOURCES)),
            http.get(`${API_BASE}/connections/:sourceId`, () => HttpResponse.json({})),
            http.delete(
                `${API_BASE}/connections/:sourceId`,
                () => new HttpResponse(null, { status: 204 }),
            ),
        );
        const user = userEvent.setup();
        render(<MyDataSources />);

        await user.click(await screen.findByText("TCP Read & Write"));
        const drawer = await screen.findByRole("dialog");
        await user.click(await within(drawer).findByRole("button", { name: "Archive" }));

        const confirm = await screen.findByRole("alertdialog");
        await user.click(within(confirm).getByRole("button", { name: "Archive" }));

        await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
        await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

        // Focus must land on the view container that still holds the table, not on <body>.
        await waitFor(() => expect(document.body).not.toHaveFocus());
        expect(document.activeElement).toContainElement(screen.getByRole("table"));
    });
});

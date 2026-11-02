// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import {
    API_BASE,
    createTestUser,
    http,
    HttpResponse,
    render,
    screen,
    server,
    userEvent,
    waitFor,
    within,
} from "~/test";
import type { ObservedLocation } from "~/test";
import { i18n } from "~/i18n/config";
import { AdminMappersView } from "./AdminMappersView";

// The drawer content toasts, and the harness has no DropNotificationProvider.
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

// ColumnFilterBuilder is a zag.js surface that doesn't drive in jsdom, and no test here filters.
vi.mock("@opencop/ui", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@opencop/ui")>()),
    ColumnFilterBuilder: () => null,
}));

const ACTIVE_MAPPER = {
    id: "m1",
    name: "Track mapper",
    description: "",
    dynamicMapper: {},
    archived: false,
};
const ARCHIVED_MAPPER = {
    ...ACTIVE_MAPPER,
    id: "m2",
    name: "Retired mapper-ARCHIVE",
    archived: true,
};
const SOURCE = {
    id: "s1",
    name: "Track feed",
    state: "CONNECTED",
    access: "R",
    transportType: "TCP",
    defaultVisible: true,
    setup: {
        kind: "TCP",
        integration: null,
        extractorId: null,
        values: {},
        mapperId: "m1",
        egressMapperId: null,
    },
};

/** An archived source still pointing at the Track mapper. */
const ARCHIVED_SOURCE = { ...SOURCE, id: "s2", name: "Old feed", archived: true };

/** Answers the admin sources list by its active / archived filter, as Core does. */
function sourcesHandler(active: unknown[], archived: unknown[]) {
    return http.get(`${API_BASE}/admin/connections`, ({ request }) =>
        HttpResponse.json(
            new URL(request.url).searchParams.get("recordVisibility") === "ARCHIVED"
                ? archived
                : active,
        ),
    );
}

function renderView(
    initialRoute = "/admin/mappers",
    onLocationChange?: (location: ObservedLocation) => void,
) {
    return render(<AdminMappersView />, {
        user: createTestUser({ authorities: ["ROLE_admin"] }),
        initialRoute,
        onLocationChange,
    });
}

const USAGE_STATUS =
    "Couldn't load which data sources use these mappers. Use Refresh to try again.";

function rowFor(name: string) {
    return screen.getByRole("row", { name: new RegExp(name) });
}

beforeEach(async () => {
    await i18n.changeLanguage("en");
    server.use(
        sourcesHandler([SOURCE], []),
        http.get(`${API_BASE}/admin/profile/mapping`, () =>
            HttpResponse.json([ACTIVE_MAPPER, ARCHIVED_MAPPER]),
        ),
        http.get(`${API_BASE}/admin/profile/mapping/:id/versions`, () => HttpResponse.json([])),
    );
});

describe("AdminMappersView", () => {
    it("lists active mappers, with no Type column", async () => {
        renderView();

        expect(await screen.findByRole("table", { name: "Mappers" })).toBeInTheDocument();
        expect(await screen.findByText("Track mapper")).toBeVisible();
        expect(screen.queryByRole("columnheader", { name: /^Type/ })).not.toBeInTheDocument();
        expect(screen.queryByText("Retired mapper")).not.toBeInTheDocument();
    });

    it("links each row to the data sources that use it", async () => {
        renderView();
        await screen.findByText("Track mapper");

        expect(
            within(rowFor("Track mapper")).getByRole("link", { name: "Data Source: Track feed" }),
        ).toHaveAttribute("href", "/admin/data-sources?id=s1&type=source");
    });

    describe("egress mappers", () => {
        function serveWriter() {
            const egressMapper = { ...ACTIVE_MAPPER, id: "e1", name: "COT egress" };
            const writer = {
                ...SOURCE,
                id: "s3",
                name: "Write feed",
                access: "W",
                setup: { ...SOURCE.setup, mapperId: null, egressMapperId: "e1" },
            };
            server.use(
                sourcesHandler([SOURCE, writer], []),
                http.get(`${API_BASE}/admin/profile/mapping`, () =>
                    HttpResponse.json([ACTIVE_MAPPER, egressMapper]),
                ),
            );
        }

        it("lists one as used by the sources that write with it", async () => {
            serveWriter();
            renderView();
            await screen.findByText("COT egress");

            expect(
                within(rowFor("COT egress")).getByRole("link", { name: "Data Source: Write feed" }),
            ).toHaveAttribute("href", "/admin/data-sources?id=s3&type=source");
        });

        it("opens one from a link", async () => {
            serveWriter();
            renderView("/admin/mappers?id=e1&type=mapper");

            const drawer = await screen.findByRole("dialog");
            expect(within(drawer).getByRole("heading", { name: "COT egress" })).toBeVisible();
        });
    });

    it("follows a Used By link without also opening the row's drawer", async () => {
        const locations: ObservedLocation[] = [];
        renderView("/admin/mappers", (location) => locations.push(location));
        await screen.findByText("Track mapper");

        await userEvent.click(
            within(rowFor("Track mapper")).getByRole("link", { name: "Data Source: Track feed" }),
        );

        await waitFor(() =>
            expect(locations.at(-1)).toEqual({
                pathname: "/admin/data-sources",
                search: "?id=s1&type=source",
            }),
        );
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("opens the mapper builder from Create", async () => {
        const locations: ObservedLocation[] = [];
        renderView("/admin/mappers", (location) => locations.push(location));
        await screen.findByText("Track mapper");

        await userEvent.click(screen.getByRole("button", { name: "Create" }));

        await waitFor(() =>
            expect(locations.at(-1)).toEqual({ pathname: "/admin/mappers/new", search: "" }),
        );
    });

    it("shows archived mappers, with the suffix stripped, only once archived rows are shown", async () => {
        renderView();
        await screen.findByText("Track mapper");

        await userEvent.click(screen.getByRole("button", { name: "Manage view" }));
        await userEvent.click(await screen.findByRole("menuitem", { name: "Show Archived" }));

        expect(await screen.findByText("Retired mapper")).toBeVisible();
        expect(screen.queryByText("Track mapper")).not.toBeInTheDocument();
    });

    it("opens the drawer for the mapper named in ?id=&type=", async () => {
        renderView("/admin/mappers?id=m1&type=mapper");

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByRole("heading", { name: "Track mapper" })).toBeVisible();
    });

    it("leaves the drawer closed for a type this page does not list", async () => {
        // m1 is a real mapper, so only the type gate keeps the drawer closed.
        renderView("/admin/mappers?id=m1&type=delimiter");
        await screen.findByText("Track mapper");

        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    describe("archived sources in Used By", () => {
        it("links the first active source and lists every source from +N more", async () => {
            server.use(sourcesHandler([SOURCE], [ARCHIVED_SOURCE]));
            const user = userEvent.setup();
            renderView();
            await screen.findByText("Track mapper");

            const row = rowFor("Track mapper");
            expect(
                within(row).getByRole("link", { name: "Data Source: Track feed" }),
            ).toBeVisible();
            // Counted, not linked: the Data Sources page doesn't open archived sources from a link.
            expect(within(row).queryByRole("link", { name: /Old feed/ })).not.toBeInTheDocument();
            await user.click(within(row).getByRole("button", { name: "+1 more" }));

            const list = await screen.findByRole("dialog", { name: "Used By" });
            expect(within(list).getByText("1 active · 1 archived")).toBeVisible();
            expect(
                within(list).getByRole("link", { name: "Data Source: Track feed" }),
            ).toBeVisible();
            expect(within(list).queryByText("Old feed (archived)")).not.toBeVisible();
            await user.click(within(list).getByRole("button", { name: "Archived (1)" }));
            expect(within(list).getByText("Old feed (archived)")).toBeVisible();
            // Opening the list must not also open the row's drawer.
            expect(screen.queryByRole("heading", { name: "Track mapper" })).not.toBeInTheDocument();
        });

        it("keeps a row one line, with every active source still a link in the list", async () => {
            const SECOND = { ...SOURCE, id: "s3", name: "Second feed" };
            const THIRD = { ...SOURCE, id: "s4", name: "Third feed" };
            server.use(sourcesHandler([SOURCE, SECOND, THIRD], []));
            const user = userEvent.setup();
            renderView();
            await screen.findByText("Track mapper");

            const row = rowFor("Track mapper");
            expect(within(row).getAllByRole("link", { name: /^Data Source:/ })).toHaveLength(1);
            await user.click(within(row).getByRole("button", { name: "+2 more" }));

            expect(await screen.findByText("3 active · 0 archived")).toBeVisible();
            expect(screen.getByRole("link", { name: "Data Source: Third feed" })).toHaveAttribute(
                "href",
                "/admin/data-sources?id=s4&type=source",
            );
        });

        it("shows a record used only by archived sources as archived, not unused", async () => {
            server.use(sourcesHandler([], [ARCHIVED_SOURCE]));
            renderView();
            await screen.findByText("Track mapper");

            expect(within(rowFor("Track mapper")).getByText("1 archived")).toBeVisible();
        });

        it("keeps them collapsed under the drawer's active links until asked", async () => {
            server.use(sourcesHandler([SOURCE], [ARCHIVED_SOURCE]));
            const user = userEvent.setup();
            renderView("/admin/mappers?id=m1&type=mapper");

            const drawer = await screen.findByRole("dialog");
            expect(
                await within(drawer).findByRole("link", { name: "Data Source: Track feed" }),
            ).toBeVisible();
            const toggle = within(drawer).getByRole("button", { name: "Archived (1)" });
            expect(toggle).toHaveAttribute("aria-expanded", "false");
            expect(within(drawer).queryByText("Old feed (archived)")).not.toBeVisible();

            await user.click(toggle);

            expect(toggle).toHaveAttribute("aria-expanded", "true");
            expect(within(drawer).getByText("Old feed (archived)")).toBeVisible();
            expect(
                within(drawer).queryByRole("link", { name: /Old feed/ }),
            ).not.toBeInTheDocument();
        });
    });

    describe("when a request fails", () => {
        const failing = () => HttpResponse.json({}, { status: 500 });

        it("shows an error with Retry when mappers fail", async () => {
            server.use(http.get(`${API_BASE}/admin/profile/mapping`, failing));
            renderView();

            expect(await screen.findByText("Couldn't load data")).toBeVisible();
            expect(screen.getByRole("button", { name: "Retry" })).toBeVisible();
            expect(screen.queryByText("Track mapper")).not.toBeInTheDocument();
        });

        it("lists the rows again after a successful Retry", async () => {
            server.use(http.get(`${API_BASE}/admin/profile/mapping`, failing));
            renderView();
            await screen.findByText("Couldn't load data");

            server.use(
                http.get(`${API_BASE}/admin/profile/mapping`, () =>
                    HttpResponse.json([ACTIVE_MAPPER]),
                ),
            );
            await userEvent.click(screen.getByRole("button", { name: "Retry" }));

            expect(await screen.findByText("Track mapper")).toBeVisible();
        });

        it("keeps the rows but marks usage unknown when data sources fail", async () => {
            server.use(http.get(`${API_BASE}/admin/connections`, failing));
            renderView();
            await screen.findByText("Track mapper");

            expect(within(rowFor("Track mapper")).getByText("Couldn't load usage")).toBeVisible();
            // One page-level announcement, not one per row.
            expect(screen.getByRole("status", { name: "" })).toHaveTextContent(USAGE_STATUS);
        });

        it("brings the Used By links back on Refresh once data sources load", async () => {
            server.use(http.get(`${API_BASE}/admin/connections`, failing));
            const user = userEvent.setup();
            renderView();
            await screen.findByText("Track mapper");
            expect(within(rowFor("Track mapper")).getByText("Couldn't load usage")).toBeVisible();

            server.use(sourcesHandler([SOURCE], []));
            await user.click(screen.getByRole("button", { name: "Refresh" }));

            expect(
                await within(rowFor("Track mapper")).findByRole("link", {
                    name: "Data Source: Track feed",
                }),
            ).toBeVisible();
            expect(screen.queryByText("Couldn't load usage")).not.toBeInTheDocument();
        });

        it("keeps the rows and Used By links when a background refresh fails", async () => {
            const user = userEvent.setup();
            renderView();
            await screen.findByText("Track mapper");
            await within(rowFor("Track mapper")).findByRole("link", {
                name: "Data Source: Track feed",
            });
            let failedRefreshes = 0;
            const failOnce = () => {
                failedRefreshes += 1;
                return failing();
            };
            server.use(
                http.get(`${API_BASE}/admin/profile/mapping`, failOnce),
                http.get(`${API_BASE}/admin/connections`, failOnce),
            );

            await user.click(screen.getByRole("button", { name: "Refresh" }));

            // The refreshes really failed: three lists (mappers, active and archived sources).
            await waitFor(() => expect(failedRefreshes).toBe(3));
            expect(rowFor("Track mapper")).toBeVisible();
            expect(
                within(rowFor("Track mapper")).getByRole("link", {
                    name: "Data Source: Track feed",
                }),
            ).toBeVisible();
            expect(screen.queryByText("Couldn't load data")).not.toBeInTheDocument();
            expect(screen.queryByText("Couldn't load usage")).not.toBeInTheDocument();
            expect(screen.queryByText(USAGE_STATUS)).not.toBeInTheDocument();
        });

        it("marks usage unknown in the mapper drawer when data sources fail", async () => {
            server.use(http.get(`${API_BASE}/admin/connections`, failing));
            renderView("/admin/mappers?id=m1&type=mapper");

            const drawer = await screen.findByRole("dialog");
            expect(within(drawer).getByText("Couldn't load usage")).toBeVisible();
        });
    });
});

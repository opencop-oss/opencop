// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";
import {
    API_BASE,
    createTestUser,
    http,
    HttpResponse,
    render,
    screen,
    server,
    waitFor,
} from "~/test";
import type { ObservedLocation } from "~/test";
import { i18n } from "~/i18n/config";
import { AdminDataSourcesView } from "./AdminDataSourcesView";

const MAPPER = {
    id: "m1",
    name: "Track mapper",
    description: "",
    dynamicMapper: {},
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
        framing: { type: "CUSTOM", mode: "delimiterInclude", delimiter: "|" },
    },
};

function renderView(initialRoute: string, onLocationChange?: (l: ObservedLocation) => void) {
    return render(<AdminDataSourcesView />, {
        user: createTestUser({ authorities: ["ROLE_admin"] }),
        initialRoute,
        onLocationChange,
    });
}

beforeEach(async () => {
    await i18n.changeLanguage("en");
    server.use(
        http.get(`${API_BASE}/admin/connections`, () => HttpResponse.json([SOURCE])),
        http.get(`${API_BASE}/admin/profile/mapping`, () => HttpResponse.json([MAPPER])),
    );
});

describe("AdminDataSourcesView", () => {
    it.each([
        { access: "W", mapperId: null, links: ["Egress Mapper: COT egress"] },
        {
            access: "RW",
            mapperId: "m1",
            links: ["Mapper: Track mapper", "Egress Mapper: COT egress"],
        },
    ])(
        "lists a TCP $access source's egress mapper among its uses",
        async ({ access, mapperId, links }) => {
            const qualifiers: string[][] = [];
            server.use(
                http.get(`${API_BASE}/admin/connections`, () =>
                    HttpResponse.json([
                        {
                            ...SOURCE,
                            access,
                            setup: { ...SOURCE.setup, mapperId, egressMapperId: "e1" },
                        },
                    ]),
                ),
                http.get(`${API_BASE}/admin/profile/mapping`, ({ request }) => {
                    qualifiers.push(new URL(request.url).searchParams.getAll("qualifier"));
                    return HttpResponse.json([
                        MAPPER,
                        { id: "e1", name: "COT egress", description: "", dynamicMapper: {} },
                    ]);
                }),
            );
            renderView("/admin/data-sources");

            expect(await screen.findByText("Track feed")).toBeVisible();
            for (const name of links)
                expect(await screen.findByRole("link", { name })).toBeVisible();
            expect(screen.getByRole("link", { name: "Egress Mapper: COT egress" })).toHaveAttribute(
                "href",
                "/admin/mappers?id=e1&type=mapper",
            );
            // Core lists only ingress mappers unless asked for both directions.
            expect(qualifiers[0]).toEqual(["INGRESS", "EGRESS"]);
        },
    );

    it("lists only data sources, each naming its own mapper", async () => {
        renderView("/admin/data-sources");

        expect(await screen.findByText("Track feed")).toBeVisible();
        expect(screen.getByRole("link", { name: "Mapper: Track mapper" })).toHaveAttribute(
            "href",
            "/admin/mappers?id=m1&type=mapper",
        );
        // Header plus the one source: no profile or mapper rows.
        expect(screen.getAllByRole("row")).toHaveLength(2);
        expect(screen.queryByRole("columnheader", { name: "Type" })).not.toBeInTheDocument();
    });

    it("creates a data source straight from Create, with no profile option", async () => {
        renderView("/admin/data-sources");
        await screen.findByText("Track feed");

        expect(screen.getByRole("button", { name: "New Data Source" })).toBeVisible();
        expect(screen.queryByRole("button", { name: "Create" })).not.toBeInTheDocument();
        expect(screen.queryByText("New Data Profile")).not.toBeInTheDocument();
    });

    it("forwards a mapper link to the Mappers page", async () => {
        const locations: ObservedLocation[] = [];
        renderView("/admin/data-sources?id=x1&type=mapper", (l) => locations.push(l));

        await waitFor(() =>
            expect(locations.at(-1)).toEqual({
                pathname: "/admin/mappers",
                search: "?id=x1&type=mapper",
            }),
        );
    });

    it("keeps a data source link on the Data Sources page", async () => {
        const locations: ObservedLocation[] = [];
        renderView("/admin/data-sources?id=s1&type=source", (l) => locations.push(l));

        expect(await screen.findByRole("row", { name: /Track feed/ })).toBeVisible();
        expect(locations.at(-1)).toEqual({
            pathname: "/admin/data-sources",
            search: "?id=s1&type=source",
        });
    });
});

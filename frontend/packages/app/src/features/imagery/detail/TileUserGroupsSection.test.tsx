// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MapTileUserGroupGrant } from "@opencop/api";
import { i18n } from "~/i18n/config";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { TileUserGroupsSection } from "./TileUserGroupsSection";
import { groupQueries } from "~/features/groups/groups.queries";
import type { Group } from "~/features/groups/groups.queries";

// Mocked at the HTTP boundary (MSW), so the section, useRelationSection, the real mutations and
// DrawerRelationSection all run. Request bodies are captured to assert the wire contract.
//
// The candidate pool here is a plain admin query rather than a server-paged hook, so it is seeded
// through the query cache instead of being mocked.

// ResourceTable and OcSelect are zag.js widgets that do not drive in jsdom. Same doubles the
// layer and data-source section tests use: a button per row, the toolbar actions as buttons, and
// OcSelect as a native select.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    type Row = Record<string, unknown>;
    interface TableProps {
        "aria-label"?: string;
        data: Row[];
        getRowId: (row: Row) => string;
        rowSelection?: Record<string, boolean>;
        onRowSelectionChange?: (next: Record<string, boolean>) => void;
        toolbar?: {
            leading?: ReactNode;
            actions?: { label: string; onClick: () => void }[];
            selectionActions?: { label: string; onClick: () => void; disabled?: boolean }[];
        };
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        ResourceTable: (props: TableProps) =>
            createElement(
                "div",
                { "aria-label": props["aria-label"] },
                props.toolbar?.leading,
                ...(props.toolbar?.actions ?? []).map((a) =>
                    createElement(
                        "button",
                        { key: a.label, type: "button", onClick: a.onClick },
                        a.label,
                    ),
                ),
                ...(props.toolbar?.selectionActions ?? []).map((a) =>
                    createElement(
                        "button",
                        { key: a.label, type: "button", onClick: a.onClick, disabled: a.disabled },
                        a.label,
                    ),
                ),
                ...props.data.map((row) => {
                    const id = props.getRowId(row);
                    const selected = !!props.rowSelection?.[id];
                    return createElement(
                        "div",
                        { key: id },
                        createElement(
                            "button",
                            {
                                type: "button",
                                onClick: () => {
                                    const next = { ...(props.rowSelection ?? {}) };
                                    if (selected) delete next[id];
                                    else next[id] = true;
                                    props.onRowSelectionChange?.(next);
                                },
                            },
                            `row ${id}`,
                        ),
                        createElement(
                            "span",
                            { "data-testid": `display-${id}` },
                            String(row.name ?? id),
                        ),
                    );
                }),
            ),
    };
});

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

const TILE_ID = "t1";
const GROUPS_URL = `${API_BASE}/map/tiles/:id/user-groups`;

let postBodies: unknown[] = [];
let deleteBodies: unknown[] = [];

const POOL: Group[] = [
    {
        id: "g1",
        name: "Coalition",
        description: null,
        createdAt: "2026-01-01T00:00:00Z",
        source: "OPENCOP",
    },
    {
        id: "g2",
        name: "Analysts",
        description: null,
        createdAt: "2026-01-01T00:00:00Z",
        source: "SCIM",
    },
];

function grant(userGroupId: string, name: string): MapTileUserGroupGrant {
    return {
        mapTileId: TILE_ID,
        userGroupId,
        grantedAt: "2026-01-01T00:00:00Z",
        name,
        source: "OPENCOP",
    };
}

function renderSection(granted: MapTileUserGroupGrant[]) {
    render(<TileUserGroupsSection tileId={TILE_ID} />, {
        queryData: {
            [JSON.stringify(tileQueries.userGroups(TILE_ID).queryKey)]: granted,
            [JSON.stringify(groupQueries.adminList().queryKey)]: POOL,
        },
    });
}

const assignButton = () => screen.getByRole("button", { name: i18n.t("adminDialogs.assign") });

// The assign panel's confirm button carries the selected count once rows are chosen, so match on
// the stable prefix.
const addButton = () => screen.getByRole("button", { name: /^Add/ });

describe("TileUserGroupsSection", () => {
    beforeEach(() => {
        postBodies = [];
        deleteBodies = [];
        showToast.mockReset();
        server.use(
            http.get(GROUPS_URL, () => HttpResponse.json([])),
            http.get(`${API_BASE}/admin/user-groups`, () => HttpResponse.json(POOL)),
            http.get(`${API_BASE}/map/tiles`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/admin/map/tiles`, () => HttpResponse.json([])),
            http.post(GROUPS_URL, async ({ request }) => {
                postBodies.push(await request.json());
                return new HttpResponse(null, { status: 204 });
            }),
            http.delete(GROUPS_URL, async ({ request }) => {
                deleteBodies.push(await request.json());
                return new HttpResponse(null, { status: 204 });
            }),
        );
    });

    it("lists granted groups by name", async () => {
        renderSection([grant("g1", "Coalition")]);

        await waitFor(() => expect(screen.getByTestId("display-g1").textContent).toBe("Coalition"));
    });

    it("offers only groups that are not already granted", async () => {
        const user = userEvent.setup();
        renderSection([grant("g1", "Coalition")]);

        await waitFor(() => expect(assignButton()).toBeInTheDocument());
        await user.click(assignButton());

        // g1 already holds a grant, so only g2 remains assignable.
        await waitFor(() => expect(screen.getByText("row g2")).toBeInTheDocument());
        expect(screen.queryByText("row g1")).toBeNull();
    });

    it("POSTs an array of group ids when granting access", async () => {
        const user = userEvent.setup();
        renderSection([]);

        await waitFor(() => expect(assignButton()).toBeInTheDocument());
        await user.click(assignButton());

        await waitFor(() => expect(screen.getByText("row g1")).toBeInTheDocument());
        await user.click(screen.getByRole("button", { name: "row g1" }));
        await user.click(addButton());

        await waitFor(() => expect(postBodies).toContainEqual(["g1"]));
    });

    it("DELETEs an array of group ids when revoking", async () => {
        const user = userEvent.setup();
        renderSection([grant("g1", "Coalition"), grant("g2", "Analysts")]);

        await waitFor(() => expect(screen.getByText("row g1")).toBeInTheDocument());
        await user.click(screen.getByRole("button", { name: "row g1" }));
        await user.click(screen.getByRole("button", { name: i18n.t("adminDialogs.revokeAccess") }));

        await waitFor(() => expect(deleteBodies).toContainEqual(["g1"]));
    });
});

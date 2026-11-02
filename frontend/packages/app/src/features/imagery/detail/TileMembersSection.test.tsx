// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MapTileMember } from "@opencop/api";
import { i18n } from "~/i18n/config";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { TileMembersSection } from "./TileMembersSection";

// Mocked at the HTTP boundary (MSW), so the section, useRelationSection, the real mutations and
// DrawerRelationSection all run. Request bodies are captured to assert the wire contract.

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
                        // The rendered display name, so the null-username fallback is observable.
                        createElement(
                            "span",
                            { "data-testid": `display-${id}` },
                            String(row.username ?? row.userId),
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

// The assign pool is server-paged. These tests cover the assigned list and the wire contract, so
// the pool is stubbed with two candidates.
vi.mock("~/features/users/use-user-picker-pool.hook", () => ({
    useUserPickerPool: () => ({
        pool: [
            { id: "u9", username: "ninth", firstName: "", lastName: "", email: "" },
            { id: "u8", username: "eighth", firstName: "", lastName: "", email: "" },
        ],
        poolLoading: false,
        serverPaging: {
            onSearchChange: () => {},
            onLoadMore: () => {},
            hasMore: false,
            isFetchingMore: false,
            isError: false,
            onRetry: () => {},
        },
    }),
}));

const TILE_ID = "t1";
const MEMBERS_URL = `${API_BASE}/map/tiles/:id/members`;

let postBodies: unknown[] = [];
let deleteBodies: unknown[] = [];

function member(userId: string, username: string | null): MapTileMember {
    return {
        mapTileId: TILE_ID,
        userId,
        grantedAt: "2026-01-01T00:00:00Z",
        username,
        firstName: null,
        lastName: null,
        email: null,
    };
}

function renderSection(members: MapTileMember[]) {
    render(<TileMembersSection tileId={TILE_ID} />, {
        queryData: {
            [JSON.stringify(tileQueries.members(TILE_ID).queryKey)]: members,
        },
    });
}

const assignButton = () => screen.getByRole("button", { name: i18n.t("adminDialogs.assign") });

// The assign panel's confirm button label varies with the selection; match the stable prefix.
const addButton = () => screen.getByRole("button", { name: /^Add/ });

describe("TileMembersSection", () => {
    beforeEach(() => {
        postBodies = [];
        deleteBodies = [];
        showToast.mockReset();
        server.use(
            http.get(MEMBERS_URL, () => HttpResponse.json([])),
            http.get(`${API_BASE}/map/tiles`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/admin/map/tiles`, () => HttpResponse.json([])),
            http.post(MEMBERS_URL, async ({ request }) => {
                postBodies.push(await request.json());
                return new HttpResponse(null, { status: 204 });
            }),
            http.delete(MEMBERS_URL, async ({ request }) => {
                deleteBodies.push(await request.json());
                return new HttpResponse(null, { status: 204 });
            }),
        );
    });

    it("lists granted users by username", async () => {
        renderSection([member("u1", "alice")]);

        await waitFor(() => expect(screen.getByTestId("display-u1").textContent).toBe("alice"));
    });

    it("falls back to the user id when the grant has no local user row", async () => {
        // A grant can name a user who was never provisioned locally, so the server's LEFT JOIN
        // returns a null username. The row must still show, or a real grant becomes invisible.
        renderSection([member("u-orphan", null)]);

        await waitFor(() =>
            expect(screen.getByTestId("display-u-orphan").textContent).toBe("u-orphan"),
        );
    });

    it("POSTs an array of user ids when granting access", async () => {
        const user = userEvent.setup();
        renderSection([member("u1", "alice")]);

        await waitFor(() => expect(assignButton()).toBeInTheDocument());
        await user.click(assignButton());

        await waitFor(() => expect(screen.getByText("row u9")).toBeInTheDocument());
        await user.click(screen.getByRole("button", { name: "row u9" }));
        await user.click(addButton());

        await waitFor(() => expect(postBodies).toContainEqual(["u9"]));
    });

    it("sends every selected user in one grant request", async () => {
        const user = userEvent.setup();
        renderSection([member("u1", "alice")]);

        await waitFor(() => expect(assignButton()).toBeInTheDocument());
        await user.click(assignButton());

        await waitFor(() => expect(screen.getByText("row u9")).toBeInTheDocument());
        await user.click(screen.getByRole("button", { name: "row u9" }));
        await user.click(screen.getByRole("button", { name: "row u8" }));
        await user.click(addButton());

        await waitFor(() => expect(postBodies).toContainEqual(["u9", "u8"]));
        expect(postBodies).toHaveLength(1);
    });

    it("DELETEs an array of user ids when revoking", async () => {
        const user = userEvent.setup();
        renderSection([member("u1", "alice"), member("u2", "bob")]);

        await waitFor(() => expect(screen.getByText("row u1")).toBeInTheDocument());
        await user.click(screen.getByRole("button", { name: "row u1" }));
        await user.click(screen.getByRole("button", { name: i18n.t("adminDialogs.revokeAccess") }));

        await waitFor(() => expect(deleteBodies).toContainEqual(["u1"]));
    });

    it("surfaces an error toast when a grant fails", async () => {
        const user = userEvent.setup();
        server.use(http.post(MEMBERS_URL, () => new HttpResponse(null, { status: 403 })));
        renderSection([member("u1", "alice")]);

        await waitFor(() => expect(assignButton()).toBeInTheDocument());
        await user.click(assignButton());
        await waitFor(() => expect(screen.getByText("row u9")).toBeInTheDocument());
        await user.click(screen.getByRole("button", { name: "row u9" }));
        await user.click(addButton());

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ type: "error" })),
        );
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, userEvent } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { LayerGroupMembersSection } from "./LayerGroupMembersSection";
import { layerGroupQueries } from "../layer-groups.queries";
import type { LayerGroupMember, LayerGroupRole } from "../layer-groups.queries";

// @opencop/ui widgets don't drive in jsdom — minimal doubles exposing the section's wiring:
// one button per row that toggles selection, plus the toolbar selection actions (with disabled).
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
            selectionActions?: { label: string; onClick: () => void; disabled?: boolean }[];
        };
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        ResourceTable: (props: TableProps) =>
            createElement(
                "div",
                { "aria-label": props["aria-label"] },
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
                        "button",
                        {
                            key: id,
                            type: "button",
                            onClick: () => {
                                const next = { ...(props.rowSelection ?? {}) };
                                if (selected) delete next[id];
                                else next[id] = true;
                                props.onRowSelectionChange?.(next);
                            },
                        },
                        `row ${id}`,
                    );
                }),
            ),
    };
});

// The toast surface is spied — no need to mount the notification stack.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

// The assign-user pool is server-paged (useUserPickerPool); these tests exercise the members
// list, not the pool, so stub it empty.
vi.mock("~/features/users/use-user-picker-pool.hook", () => ({
    useUserPickerPool: () => ({
        pool: [],
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

const GROUP_ID = "g1";

function member(userId: string, role: LayerGroupRole): LayerGroupMember {
    return {
        userId,
        username: userId,
        firstName: "",
        lastName: "",
        email: `${userId}@example.com`,
        role,
        joinedAt: "2026-01-01T00:00:00Z",
    };
}

function renderSection(members: LayerGroupMember[]) {
    // Seed the cache so the first render paints immediately (harness client is
    // staleTime: Infinity → no on-mount refetch). These tests only assert the
    // Revoke gate's disabled state, so no mutation/refetch fires.
    render(<LayerGroupMembersSection groupId={GROUP_ID} />, {
        queryData: {
            [JSON.stringify(layerGroupQueries.members(GROUP_ID).queryKey)]: members,
        },
    });
}

const revokeButton = () =>
    screen.getByRole("button", { name: i18n.t("adminDialogs.revokeAccess") }) as HTMLButtonElement;

describe("LayerGroupMembersSection — revoke guard", () => {
    it("disables Revoke access when only the sole owner is selected", async () => {
        const user = userEvent.setup();
        renderSection([member("o1", "OWNER"), member("u2", "MEMBER")]);

        // Revoking the sole owner would leave the group ownerless — the server rejects it.
        await user.click(screen.getByRole("button", { name: "row o1" }));
        expect(revokeButton().disabled).toBe(true);

        // Selecting only the non-owner keeps the owner → revoke is allowed.
        await user.click(screen.getByRole("button", { name: "row o1" })); // deselect o1
        await user.click(screen.getByRole("button", { name: "row u2" }));
        expect(revokeButton().disabled).toBe(false);
    });

    it("disables Revoke access when the selection would remove every owner", async () => {
        const user = userEvent.setup();
        renderSection([member("o1", "OWNER"), member("o2", "OWNER"), member("u3", "MEMBER")]);

        // One of two owners can be revoked — an owner remains.
        await user.click(screen.getByRole("button", { name: "row o1" }));
        expect(revokeButton().disabled).toBe(false);

        // Both owners selected — revoking them all would leave no owner.
        await user.click(screen.getByRole("button", { name: "row o2" }));
        expect(revokeButton().disabled).toBe(true);
    });
});

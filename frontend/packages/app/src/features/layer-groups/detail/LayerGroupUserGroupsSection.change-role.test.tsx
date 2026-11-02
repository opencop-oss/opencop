// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { layerGroupQueries } from "../layer-groups.queries";
import type { LayerGroupRole, LayerGroupUserGroup } from "../layer-groups.queries";
import { LayerGroupUserGroupsSection } from "./LayerGroupUserGroupsSection";

// Drives the real DrawerRelationSection and ChangeRolePanel — the flavor documented in
// docs/architecture/admin-layouts.md § Testing for interaction tests. The sibling spec stubs the
// section to capture the hook config; that flavor cannot see whether the panel is reachable, which
// is the whole point here.
//
// ResourceTable/OcSelect are Chakra v3 (zag.js) widgets that don't drive in happy-dom, so they get
// minimal doubles: one button per row that toggles selection, the toolbar's selection actions as
// buttons, and OcSelect as a native <select>.
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
    interface SelectProps {
        items: { label: string; value: string }[];
        value?: string;
        onValueChange: (value: string) => void;
        placeholder?: string;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        ResourceTable: (props: TableProps) =>
            createElement(
                "div",
                { "aria-label": props["aria-label"] },
                ...(props.toolbar?.selectionActions ?? []).map((action) =>
                    createElement(
                        "button",
                        {
                            key: action.label,
                            type: "button",
                            onClick: action.onClick,
                            disabled: action.disabled,
                        },
                        action.label,
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
        OcSelect: (props: SelectProps) =>
            createElement(
                "select",
                {
                    "aria-label": "role-select",
                    value: props.value ?? "",
                    onChange: (e: { target: { value: string } }) =>
                        props.onValueChange(e.target.value),
                },
                createElement("option", { value: "" }, props.placeholder ?? ""),
                ...props.items.map((item) =>
                    createElement("option", { key: item.value, value: item.value }, item.label),
                ),
            ),
    };
});

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

// The assign picker is server-paged and irrelevant to change-role; stub it empty.
vi.mock("~/features/groups/use-user-group-picker-pool.hook", () => ({
    useUserGroupPickerPool: () => ({
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

const GROUP_ID = "lg1";
const USER_GROUPS_URL = `${API_BASE}/layer-groups/:id/user-groups`;

function assigned(userGroupId: string, name: string, role: LayerGroupRole): LayerGroupUserGroup {
    return { userGroupId, name, role, assignedAt: "2026-01-01T00:00:00Z", source: "OPENCOP" };
}

const ASSIGNED = [assigned("ug1", "Recon Team", "MEMBER"), assigned("ug2", "Ops Team", "MEMBER")];

function renderSection() {
    // Seeded so the first render paints immediately; the harness client pins staleTime.
    render(<LayerGroupUserGroupsSection groupId={GROUP_ID} />, {
        queryData: {
            [JSON.stringify(layerGroupQueries.userGroups(GROUP_ID).queryKey)]: ASSIGNED,
        },
    });
}

/** Select the given assigned rows, then open the bulk change-role dialog. */
async function openChangeRoleDialog(
    user: ReturnType<typeof userEvent.setup>,
    rowIds: string[],
): Promise<HTMLElement> {
    for (const id of rowIds) {
        await user.click(screen.getByRole("button", { name: `row ${id}` }));
    }
    await user.click(screen.getByRole("button", { name: i18n.t("adminDialogs.changeRole") }));
    return screen.findByLabelText("role-select");
}

const applyButton = () =>
    screen.getByRole("button", { name: i18n.t("common.apply") }) as HTMLButtonElement;

describe("LayerGroupUserGroupsSection — change role", () => {
    beforeEach(() => {
        showToast.mockReset();
        server.use(
            http.get(USER_GROUPS_URL, () => HttpResponse.json(ASSIGNED)),
            http.patch(USER_GROUPS_URL, () => new HttpResponse(null, { status: 200 })),
        );
    });

    /**
     * The panel's rows come from `getChangeRoleRows`; omitting it leaves them `[]`, which seeds
     * `checkedIds` empty and permanently disables Apply. The toolbar gate does not include that
     * prop, so the action still enables and the flow looks wired right up until Apply.
     */
    it("lists the selected groups and enables Apply", async () => {
        const user = userEvent.setup();
        renderSection();

        const select = await openChangeRoleDialog(user, ["ug1", "ug2"]);

        expect(
            screen.getByText(i18n.t("adminDialogs.usersToUpdate", { n: 2 })),
        ).toBeInTheDocument();
        expect(screen.getByText("Recon Team")).toBeInTheDocument();
        expect(screen.getByText("Ops Team")).toBeInTheDocument();

        await user.selectOptions(select, "ADMIN");

        expect(applyButton()).toBeEnabled();
    });

    it("reaches the panel from a single selection too", async () => {
        // No onChangeRoleSingle is passed, so one row falls through to the same bulk panel.
        const user = userEvent.setup();
        renderSection();

        const select = await openChangeRoleDialog(user, ["ug1"]);

        expect(
            screen.getByText(i18n.t("adminDialogs.usersToUpdate", { n: 1 })),
        ).toBeInTheDocument();

        await user.selectOptions(select, "ADMIN");

        expect(applyButton()).toBeEnabled();
    });
});

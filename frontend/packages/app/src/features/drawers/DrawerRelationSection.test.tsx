// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, userEvent } from "~/test";
import { beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import type { ReactNode } from "react";
import { i18n } from "~/i18n/config";
import { DrawerRelationSection, type DrawerRelationSectionProps } from "./DrawerRelationSection";
import type { AnyColumns, DrawerRelationSectionLabels } from "./drawer-relation.model";

// DrawerRelationSection is generic over T/U/R and is only ever exercised through a domain consumer
// (layers, groups, data sources), so its own contract — the toolbar gates, the mode machine, the
// drawer lock, the one-toast-per-bulk-revoke rule — is nowhere asserted directly. This file drives
// it against a synthetic "vault / widget tier" domain so those behaviors are pinned independently
// of any role vocabulary.
//
// Scope, for whoever executes the move into `@opencop/ui`: retargeting *this* file's mock is a
// one-line change, because the specifier it mocks becomes the one the component itself imports.
// The six sibling specs that mock `@opencop/ui` to drive the real component
// (LayerMembersSection, LayerGroupMembersSection, LayerGroupLayersSection, LayerLayerGroupsSection,
// TileMembersSection, TileUserGroupsSection) still need their own repair for their
// consumer-specific coverage PATCH bucketing, owner-safety gating, role-cap resolvers. This file
// does not replace them.

// ResourceTable/OcSelect are Chakra v3 (zag.js) widgets that don't drive in happy-dom. Replace them
// with minimal doubles that surface the props this section is responsible for wiring: the toolbar
// and selection actions (label, disabled, disabledReason, confirm), row selection, the column-filter
// state, and the error/retry pair. The doubles render props — they never decide behavior — so an
// assertion here is about the section, not about the stand-in.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    type Row = Record<string, unknown>;
    interface Action {
        label: string;
        onClick?: () => void;
        disabled?: boolean;
        disabledReason?: string;
        confirm?: { title: string; message: string; confirmLabel?: string };
    }
    interface TableProps {
        "aria-label"?: string;
        data: Row[];
        getRowId: (row: Row) => string;
        enableRowSelection?: boolean;
        rowSelection?: Record<string, boolean>;
        onRowSelectionChange?: (next: Record<string, boolean>) => void;
        globalFilter?: string;
        columnFilters?: { id: string; value: unknown }[];
        onColumnFiltersChange?: (next: { id: string; value: unknown }[]) => void;
        isError?: boolean;
        onRetry?: () => void;
        filterBar?: ReactNode;
        toolbar?: {
            leading?: ReactNode;
            onRefresh?: () => Promise<unknown>;
            isRefreshing?: boolean;
            actions?: Action[];
            selectionActions?: Action[];
        };
    }
    interface FilterBarProps {
        onChange: (filters: { id: string; value: unknown }[]) => void;
        onSearchChange?: (value: string) => void;
    }
    interface SelectProps {
        items: { label: string; value: string }[];
        value?: string;
        onValueChange: (value: string) => void;
        placeholder?: string;
    }
    // Both toolbar and selection actions surface `disabledReason` — ToolbarActionButton and
    // SelectionActionButton each wrap a disabled control in a Tooltip. `title` stands in for that.
    const actionButton = (a: Action) =>
        createElement(
            "button",
            {
                key: a.label,
                type: "button",
                onClick: a.onClick,
                disabled: a.disabled,
                title: a.disabledReason,
                "data-confirm": a.confirm ? a.confirm.message : undefined,
                "data-confirm-label": a.confirm?.confirmLabel,
            },
            a.label,
        );
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        ResourceTable: (props: TableProps) =>
            createElement(
                "div",
                {
                    "aria-label": props["aria-label"],
                    "data-error": String(!!props.isError),
                    "data-selectable": String(!!props.enableRowSelection),
                    "data-column-filters": String(props.columnFilters?.length ?? 0),
                    "data-global-filter": props.globalFilter ?? "",
                },
                props.toolbar?.leading,
                ...(props.toolbar?.actions ?? []).map(actionButton),
                ...(props.toolbar?.selectionActions ?? []).map(actionButton),
                props.onRetry
                    ? createElement(
                          "button",
                          { key: "retry", type: "button", onClick: props.onRetry },
                          "retry",
                      )
                    : null,
                props.toolbar?.onRefresh
                    ? createElement(
                          "button",
                          {
                              key: "refresh",
                              type: "button",
                              onClick: props.toolbar.onRefresh,
                              "data-refreshing": String(!!props.toolbar.isRefreshing),
                          },
                          "refresh",
                      )
                    : null,
                props.filterBar ?? null,
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
        ColumnFilterBuilder: (props: FilterBarProps) =>
            createElement(
                "div",
                null,
                createElement(
                    "button",
                    {
                        key: "chip",
                        type: "button",
                        onClick: () => props.onChange([{ id: "tier", value: ["GOLD"] }]),
                    },
                    "apply column filter",
                ),
                createElement(
                    "button",
                    {
                        key: "search",
                        type: "button",
                        onClick: () => props.onSearchChange?.("alpha"),
                    },
                    "type in filter search",
                ),
            ),
        OcSelect: (props: SelectProps) =>
            createElement(
                "select",
                {
                    "aria-label": "tier-select",
                    value: props.value ?? "",
                    onChange: (e: { target: { value: string } }) =>
                        props.onValueChange(e.target.value),
                },
                createElement("option", { value: "" }, props.placeholder ?? ""),
                ...props.items.map((it) =>
                    createElement("option", { key: it.value, value: it.value }, it.label),
                ),
            ),
    };
});

// The toast surface is asserted directly via this spy — no need to mount the notification stack.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

// ── Synthetic domain ──────────────────────────────────────────────────────────
// Deliberately not a role ladder, and with different id accessors on each side, so nothing here
// can accidentally lean on the layer/group vocabulary the real consumers supply.

type Tier = "BRONZE" | "SILVER" | "GOLD";

interface Assignment {
    assignmentId: string;
    name: string;
    tier: Tier;
}

interface Candidate {
    candidateId: string;
    name: string;
}

const ASSIGNED: Assignment[] = [
    { assignmentId: "a1", name: "Alpha", tier: "GOLD" },
    { assignmentId: "a2", name: "Bravo", tier: "BRONZE" },
];

const POOL: Candidate[] = [{ candidateId: "c1", name: "Charlie" }];

const assignedColumns: AnyColumns<Assignment> = [
    { id: "name", accessorKey: "name", header: "Name" },
    // `meta.filterDef` is what `toFilterableColumns` keys on; without it the section never builds a
    // `filterBar`, and the selection-clearing guard inside its onChange is unreachable.
    {
        id: "tier",
        accessorKey: "tier",
        header: "Tier",
        meta: { filterDef: { label: "Tier", type: "string" as const } },
    },
];
const unassignedColumns: AnyColumns<Candidate> = [
    { id: "name", accessorKey: "name", header: "Name" },
];

const TIER_OPTIONS = [
    { label: "Bronze", value: "BRONZE" as const },
    { label: "Silver", value: "SILVER" as const },
    { label: "Gold", value: "GOLD" as const },
];

const labels: DrawerRelationSectionLabels = {
    title: "Vault Widgets",
    description: "Widgets stored in this vault.",
    assignTitle: "Assign Widgets to Vault",
    assignDescription: "Pick widgets, designate a tier, and add them to this vault.",
    changeRoleTitle: "Change Tier in Vault",
    changeRoleDescription: "Pick a new tier, then choose which widgets it applies to.",
    assign: "Assign widgets",
    revoke: "Revoke widget",
    revokeConfirm: {
        title: "Revoke access?",
        message: (count) => `Revoke ${count} widget(s)?`,
        confirmLabel: "Revoke",
    },
    add: "Add",
    back: "Back to Widgets",
    changeRole: "Change tier",
    changeRoleError: "Tier change failed",
    rolePlaceholder: "Pick a tier",
    revokeError: "Revoke failed",
    revokeDisabledReason: "The vault must keep one gold widget",
    assignError: "Assign failed",
    listAriaLabel: "Assigned widgets",
    assignAriaLabel: "Widget pool",
};

let onRevoke: Mock<(id: string) => Promise<unknown>>;
let onAssign: Mock<(ids: string[], role?: Tier) => Promise<unknown>>;

type SectionProps = DrawerRelationSectionProps<Assignment, Candidate, Tier>;

function renderSection(overrides: Partial<SectionProps> = {}) {
    const props: SectionProps = {
        labels,
        assigned: ASSIGNED,
        assignedColumns,
        getAssignedRowId: (row) => row.assignmentId,
        onRevoke,
        unassigned: POOL,
        unassignedColumns,
        getUnassignedRowId: (row) => row.candidateId,
        onAssign,
        ...overrides,
    };
    return render(<DrawerRelationSection {...props} />);
}

/** Props for a section that offers the bulk change-tier flow (the Tier-A shape). */
function bulkEditProps(overrides: Partial<SectionProps> = {}): Partial<SectionProps> {
    return {
        roleOptions: TIER_OPTIONS,
        onChangeRole: vi.fn().mockResolvedValue(undefined),
        getChangeRoleRows: vi.fn((ids: string[]) =>
            ids.map((id) => ({ id, label: id, role: "Bronze" })),
        ),
        ...overrides,
    };
}

const listTable = () => screen.getByLabelText(labels.listAriaLabel);
const row = (id: string) => screen.getByRole("button", { name: `row ${id}` });
const button = (name: string) => screen.getByRole("button", { name }) as HTMLButtonElement;
const assignButton = () => button(labels.assign);
const changeTierButton = () => button(labels.changeRole);
const revokeButton = () => button(labels.revoke);

describe("DrawerRelationSection", () => {
    beforeEach(() => {
        showToast.mockReset();
        onRevoke = vi.fn().mockResolvedValue(undefined);
        onAssign = vi.fn().mockResolvedValue(undefined);
    });

    describe("toolbar gates", () => {
        it("disables the bulk change action when the guard rejects the selection", async () => {
            const user = userEvent.setup();
            // "a1" is the vault's only gold widget — nothing about it can change.
            renderSection(
                bulkEditProps({ canChangeRoleFor: (ids) => ids.some((id) => id !== "a1") }),
            );

            await user.click(row("a1"));
            expect(changeTierButton().disabled).toBe(true);

            // Widening the selection to something changeable re-enables the action.
            await user.click(row("a2"));
            expect(changeTierButton().disabled).toBe(false);
        });

        it("disables Revoke when the guard rejects the selection, and says why", async () => {
            const user = userEvent.setup();
            renderSection({ canRevokeFor: (ids) => !ids.includes("a1") });

            await user.click(row("a1"));
            expect(revokeButton().disabled).toBe(true);
            expect(revokeButton().title).toBe(labels.revokeDisabledReason);

            await user.click(row("a1")); // deselect
            await user.click(row("a2"));
            expect(revokeButton().disabled).toBe(false);
        });

        it("offers no bulk change action when the section supplies no change handler", async () => {
            const user = userEvent.setup();
            renderSection({ roleOptions: TIER_OPTIONS });

            await user.click(row("a1"));
            expect(screen.queryByRole("button", { name: labels.changeRole })).toBeNull();
            // Revoke is the one action every relation has, so it is still offered.
            expect(revokeButton()).toBeTruthy();
        });

        it("blocks Assign while the list has a selection", async () => {
            const user = userEvent.setup();
            renderSection();

            expect(assignButton().disabled).toBe(false);

            // The picker carries its own selection; a live list selection would survive the round
            // trip and silently re-arm the bulk actions on return.
            await user.click(row("a1"));
            expect(assignButton().disabled).toBe(true);
            expect(assignButton().title).toBe(i18n.t("adminDialogs.clearSelectionToAssign"));
        });

        it("wires the toolbar refresh to the section's refresh handler", async () => {
            const user = userEvent.setup();
            const onRefresh = vi.fn().mockResolvedValue(undefined);
            renderSection({ onRefresh, isFetching: true });

            expect(button("refresh").dataset.refreshing).toBe("true");
            await user.click(button("refresh"));
            expect(onRefresh).toHaveBeenCalled();
        });

        it("attaches a count-aware confirmation to Revoke", async () => {
            const user = userEvent.setup();
            renderSection();

            await user.click(row("a1"));
            expect(revokeButton().dataset.confirm).toBe(labels.revokeConfirm.message(1));

            await user.click(row("a2"));
            expect(revokeButton().dataset.confirm).toBe(labels.revokeConfirm.message(2));
            // No relation sets this today — baseRelationLabels builds title+message only — so this
            // pins the override path independently of the default labels contract.
            expect(revokeButton().dataset.confirmLabel).toBe(labels.revokeConfirm.confirmLabel);
        });
    });

    describe("bulk change flow", () => {
        it("routes a single selection to the single-row handler instead of the bulk panel", async () => {
            const user = userEvent.setup();
            const onChangeRoleSingle = vi.fn();
            const props = bulkEditProps({ onChangeRoleSingle });
            renderSection(props);

            await user.click(row("a1"));
            await user.click(changeTierButton());

            expect(onChangeRoleSingle).toHaveBeenCalledWith("a1");
            // Still in list mode — the bulk panel never opened, so its row snapshot was never taken.
            expect(props.getChangeRoleRows).not.toHaveBeenCalled();
            expect(listTable()).toBeTruthy();
        });

        it("opens the bulk panel for a multi-row selection, snapshotting the selected ids once", async () => {
            const user = userEvent.setup();
            const props = bulkEditProps({ onChangeRoleSingle: vi.fn() });
            renderSection(props);

            await user.click(row("a1"));
            await user.click(row("a2"));
            await user.click(changeTierButton());

            expect(props.getChangeRoleRows).toHaveBeenCalledTimes(1);
            expect(props.getChangeRoleRows).toHaveBeenCalledWith(["a1", "a2"]);
            await screen.findByText(i18n.t("adminDialogs.usersToUpdate", { n: 2 }));
        });

        it("opens the bulk panel for a single selection when there is no single-row handler", async () => {
            const user = userEvent.setup();
            renderSection(bulkEditProps());

            await user.click(row("a1"));
            await user.click(changeTierButton());

            await screen.findByText(i18n.t("adminDialogs.usersToUpdate", { n: 1 }));
        });

        it("applies the chosen value to the snapshotted rows and returns to a cleared list", async () => {
            const user = userEvent.setup();
            const props = bulkEditProps();
            renderSection(props);

            await user.click(row("a1"));
            await user.click(row("a2"));
            await user.click(changeTierButton());

            // The section's `rolePlaceholder` label reaches the panel's select.
            const select = await screen.findByLabelText("tier-select");
            expect(select.querySelector("option")?.textContent).toBe(labels.rolePlaceholder);

            await user.selectOptions(select, "SILVER");
            await user.click(button(i18n.t("common.apply")));

            await waitFor(() =>
                expect(props.onChangeRole).toHaveBeenCalledWith(["a1", "a2"], "SILVER"),
            );
            // Back in list mode with the selection dropped, so the bulk actions are disarmed.
            await waitFor(() =>
                expect(screen.queryByRole("button", { name: labels.revoke })).toBeNull(),
            );
            expect(assignButton().disabled).toBe(false);
        });
    });

    describe("bulk change failure", () => {
        it("keeps the bulk panel open and toasts when the change-role call fails", async () => {
            const user = userEvent.setup();
            renderSection(
                bulkEditProps({ onChangeRole: vi.fn().mockRejectedValue(new Error("nope")) }),
            );

            await user.click(row("a1"));
            await user.click(row("a2"));
            await user.click(changeTierButton());
            await user.selectOptions(await screen.findByLabelText("tier-select"), "SILVER");
            await user.click(button(i18n.t("common.apply")));

            await waitFor(() =>
                expect(showToast).toHaveBeenCalledWith(
                    expect.objectContaining({ type: "error", title: labels.changeRoleError }),
                ),
            );
        });
    });

    describe("host drawer lock", () => {
        it("locks the drawer while the bulk panel is open and releases it on cancel", async () => {
            const user = userEvent.setup();
            const onLockedChange = vi.fn();
            renderSection(bulkEditProps({ onLockedChange }));

            // List mode is unlocked from the first effect.
            expect(onLockedChange).toHaveBeenLastCalledWith(false);

            await user.click(row("a1"));
            await user.click(changeTierButton());
            expect(onLockedChange).toHaveBeenLastCalledWith(true);

            await user.click(button(i18n.t("common.close")));
            expect(onLockedChange).toHaveBeenLastCalledWith(false);
        });

        // The assign picker is the same shape as change role: it takes the panel over and
        // unmounts the toolbar holding the button that opened it. Reporting only change role
        // left the host unlocked here, so its focus effect never ran and focus fell to the body.
        it("locks the drawer while the assign picker is open and releases it on back", async () => {
            const user = userEvent.setup();
            const onLockedChange = vi.fn();
            renderSection({ onLockedChange });

            expect(onLockedChange).toHaveBeenLastCalledWith(false);

            await user.click(assignButton());
            expect(onLockedChange).toHaveBeenLastCalledWith(true);

            await user.click(button(labels.back));
            expect(onLockedChange).toHaveBeenLastCalledWith(false);
        });

        it("releases the drawer lock when it unmounts mid-flow", async () => {
            const user = userEvent.setup();
            const onLockedChange = vi.fn();
            const { unmount } = renderSection(bulkEditProps({ onLockedChange }));

            await user.click(row("a1"));
            await user.click(changeTierButton());
            expect(onLockedChange).toHaveBeenLastCalledWith(true);

            // e.g. the drawer closes from under the panel — the parent must not be left locked.
            unmount();
            expect(onLockedChange).toHaveBeenLastCalledWith(false);
        });
    });

    describe("assign picker", () => {
        it("clears the whole filter bar and the pool search on the way in", async () => {
            const user = userEvent.setup();
            const serverPaging = {
                onSearchChange: vi.fn(),
                onLoadMore: vi.fn(),
                hasMore: false,
                isFetchingMore: false,
                isError: false,
                onRetry: vi.fn(),
            };
            renderSection({ serverPaging });

            await user.click(button("apply column filter"));
            await user.click(button("type in filter search"));
            expect(listTable().dataset.columnFilters).toBe("1");
            expect(listTable().dataset.globalFilter).toBe("alpha");

            await user.click(assignButton());
            expect(serverPaging.onSearchChange).toHaveBeenCalledWith("");
            expect(screen.getByLabelText(labels.assignAriaLabel)).toBeTruthy();

            // Both halves clear together: a chip *or* a leftover search term can hide the row the
            // user just assigned, which reads as the assign having failed.
            await user.click(button(labels.back));
            expect(listTable().dataset.columnFilters).toBe("0");
            expect(listTable().dataset.globalFilter).toBe("");
        });

        it("assigns the picked candidate with the chosen tier and returns to the list", async () => {
            const user = userEvent.setup();
            renderSection({ roleOptions: TIER_OPTIONS });

            await user.click(assignButton());
            await user.click(row("c1"));
            await user.selectOptions(await screen.findByLabelText("tier-select"), "SILVER");
            // AssignPanel renames Add once a row and a role are picked, so `labels.add` no longer matches.
            await user.click(
                button(i18n.t("adminDialogs.addAsRole", { role: "Silver", selected: 1 })),
            );

            await waitFor(() => expect(onAssign).toHaveBeenCalledWith(["c1"], "SILVER"));
            await waitFor(() => expect(listTable()).toBeTruthy());
        });

        it("keeps the picker open and toasts when the assign fails", async () => {
            const user = userEvent.setup();
            onAssign.mockRejectedValue(new Error("nope"));
            renderSection({ roleOptions: TIER_OPTIONS });

            await user.click(assignButton());
            await user.click(row("c1"));
            await user.selectOptions(await screen.findByLabelText("tier-select"), "SILVER");
            await user.click(
                button(i18n.t("adminDialogs.addAsRole", { role: "Silver", selected: 1 })),
            );

            await waitFor(() =>
                expect(showToast).toHaveBeenCalledWith(
                    expect.objectContaining({ type: "error", title: labels.assignError }),
                ),
            );
            // Still in the picker so the user can retry.
            expect(screen.getByLabelText(labels.assignAriaLabel)).toBeTruthy();
        });

        it("keeps the list selection when a column filter is applied", async () => {
            const user = userEvent.setup();
            renderSection();

            await user.click(row("a1"));
            await user.click(row("a2"));
            expect(revokeButton()).toBeTruthy();

            // Filtering changes what is shown, not what was picked — the rows were captured when
            // they were ticked, so Revoke still covers both.
            await user.click(button("apply column filter"));

            expect(revokeButton()).toBeTruthy();
            expect(listTable().dataset.columnFilters).toBe("1");
        });

        it("keeps the list selection when the search narrows the list", async () => {
            const user = userEvent.setup();
            renderSection();

            await user.click(row("a1"));
            await user.click(row("a2"));
            expect(revokeButton()).toBeTruthy();

            await user.click(button("type in filter search"));

            expect(revokeButton()).toBeTruthy();
            expect(listTable().dataset.globalFilter).toBe("alpha");
        });
    });

    describe("bulk revoke", () => {
        it("reports a failed fan-out once, not once per row", async () => {
            const user = userEvent.setup();
            onRevoke.mockRejectedValue(new Error("nope"));
            renderSection();

            await user.click(row("a1"));
            await user.click(row("a2"));
            await user.click(revokeButton());

            await waitFor(() => expect(showToast).toHaveBeenCalledTimes(1));
            expect(showToast).toHaveBeenCalledWith({ type: "error", title: labels.revokeError });
            // Both rows were attempted — the single toast is a summary, not an early exit.
            expect(onRevoke).toHaveBeenCalledTimes(2);
        });

        it("stays quiet when every revoke succeeds", async () => {
            const user = userEvent.setup();
            renderSection();

            await user.click(row("a1"));
            await user.click(revokeButton());

            await waitFor(() => expect(onRevoke).toHaveBeenCalledWith("a1"));
            expect(showToast).not.toHaveBeenCalled();
        });
    });

    describe("assigned list state", () => {
        it("marks a failed assigned query as an error and wires retry to the refresh", async () => {
            const user = userEvent.setup();
            const onRefresh = vi.fn().mockResolvedValue(undefined);
            // Without this an errored query renders as an empty table, which reads as
            // "this relation has no members" — indistinguishable from a real empty state.
            renderSection({ assigned: [], assignedError: true, onRefresh });

            expect(listTable().dataset.error).toBe("true");

            await user.click(button("retry"));
            expect(onRefresh).toHaveBeenCalled();
        });

        it("reports no error for a healthy empty relation", () => {
            renderSection({ assigned: [] });

            expect(listTable().dataset.error).toBe("false");
        });
    });

    describe("read-only mode", () => {
        it("offers neither Assign nor the bulk actions", async () => {
            const user = userEvent.setup();
            renderSection(bulkEditProps({ readOnly: true }));

            expect(screen.queryByRole("button", { name: labels.assign })).toBeNull();
            expect(listTable().dataset.selectable).toBe("false");

            // Even if a selection is somehow made, the destructive actions stay withheld.
            await user.click(row("a1"));
            expect(screen.queryByRole("button", { name: labels.revoke })).toBeNull();
            expect(screen.queryByRole("button", { name: labels.changeRole })).toBeNull();
        });
    });
});

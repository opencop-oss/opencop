// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { I18nextProvider } from "react-i18next";
import type {
    ColumnDef,
    OnChangeFn,
    RowSelectionState,
    VisibilityState,
} from "@tanstack/react-table";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { ResourceTable } from "./ResourceTable";
import type { ResourceTableToolbar } from "./ResourceTable";
import { DEFAULT_VIRTUAL_WINDOW, virtualizerMock } from "~/test/react-virtual-mock";

interface Row {
    id: string;
    name: string;
    selectable?: boolean;
}

// ─── Virtualization / infinite-mode fixtures ────────────────────────────────────

const columns: ColumnDef<Row>[] = [{ accessorKey: "name", header: "Name" }];

function makeRows(n: number): Row[] {
    return Array.from({ length: n }, (_, i) => ({ id: String(i), name: `Row ${i}` }));
}

function renderInfiniteTable(props: {
    data: Row[];
    onLoadMore?: () => void;
    hasNextPage?: boolean;
    isFetchingNextPage?: boolean;
    isError?: boolean;
    onRetry?: () => void;
    scrollRegionLabel?: string;
}) {
    render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <ResourceTable<Row>
                    data={props.data}
                    columns={columns}
                    getRowId={(r) => r.id}
                    onLoadMore={props.onLoadMore}
                    hasNextPage={props.hasNextPage}
                    isFetchingNextPage={props.isFetchingNextPage}
                    isError={props.isError}
                    onRetry={props.onRetry}
                    scrollRegionLabel={props.scrollRegionLabel}
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

// ─── Shift-click selection fixtures ─────────────────────────────────────────────

const ROWS: Row[] = [
    { id: "r0", name: "Alpha" },
    { id: "r1", name: "Bravo" },
    { id: "r2", name: "Charlie" },
    { id: "r3", name: "Delta" },
    { id: "r4", name: "Echo" },
];

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- ColumnDef is invariant in the value type; matches ResourceTableProps
const COLUMNS: ColumnDef<Row, any>[] = [{ id: "name", accessorKey: "name", header: "Name" }];

function renderTable(opts: { rows?: Row[]; initial?: RowSelectionState } = {}) {
    const rows = opts.rows ?? ROWS;
    // Mirror of the controlled selection, updated only from the change handler
    // (never during render) so the tests can read the latest state.
    const state = { selection: opts.initial ?? ({} as RowSelectionState) };

    function Wrapper() {
        const [selection, setSelection] = useState<RowSelectionState>(state.selection);
        return (
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable
                        data={rows}
                        columns={COLUMNS}
                        getRowId={(r) => r.id}
                        enableRowSelection={(r) => r.selectable !== false}
                        rowSelection={selection}
                        onRowSelectionChange={(updater) => {
                            const next =
                                typeof updater === "function" ? updater(selection) : updater;
                            state.selection = next;
                            setSelection(next);
                        }}
                    />
                </OcThemeProvider>
            </I18nextProvider>
        );
    }

    render(<Wrapper />);
    return {
        user: userEvent.setup(),
        // Bare Object.keys — mirrors how the app's consumers read selection
        // (selectedCount, getChangeRoleRows, canRevokeFor). A stale `false` key
        // left by a buggy deselect is caught here rather than filtered away.
        selectedIds: () => Object.keys(state.selection),
    };
}

/**
 * The row checkboxes in display order — index 0 is the header select-all, so a
 * row at display position `i` is `getAllByRole("checkbox")[i + 1]`.
 */
function rowCheckbox(index: number) {
    return screen.getAllByRole("checkbox")[index + 1];
}

/** Click a row checkbox with Shift held (Ark toggles on pointer events userEvent fires). */
async function shiftClick(user: ReturnType<typeof userEvent.setup>, index: number) {
    await user.keyboard("{Shift>}");
    await user.click(rowCheckbox(index));
    await user.keyboard("{/Shift}");
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("ResourceTable virtualization (infinite mode)", () => {
    it("windows the body — renders only the virtual window, not every loaded row", () => {
        renderInfiniteTable({ data: makeRows(1000), onLoadMore: vi.fn(), hasNextPage: true });
        const bodyRows = screen.getAllByTestId("resource-table-row");
        expect(bodyRows).toHaveLength(DEFAULT_VIRTUAL_WINDOW);
        expect(bodyRows.length).toBeLessThan(1000);
    });

    it("fetches the next page when the window reaches the end and a page is available", async () => {
        const onLoadMore = vi.fn();
        renderInfiniteTable({
            data: makeRows(10),
            onLoadMore,
            hasNextPage: true,
            isFetchingNextPage: false,
        });
        await waitFor(() => expect(onLoadMore).toHaveBeenCalled());
    });

    it("keeps requesting pages when the rendered window is empty but more exist", async () => {
        // A client-side filter (e.g. the drawer pickers excluding already-assigned users) can empty
        // a whole server page, leaving rows.length === 0 while hasNextPage is still true. The picker
        // must keep paging rather than dead-end on an empty window.
        const onLoadMore = vi.fn();
        renderInfiniteTable({ data: [], onLoadMore, hasNextPage: true, isFetchingNextPage: false });
        await waitFor(() => expect(onLoadMore).toHaveBeenCalled());
    });

    it("does not auto-fetch after a failed fetch (isError stops the re-fire loop)", async () => {
        const onLoadMore = vi.fn();
        renderInfiniteTable({ data: [], onLoadMore, hasNextPage: true, isError: true });
        // Surfaces the error state instead of silently re-firing the failed request.
        await waitFor(() => expect(screen.getByText("Couldn't load data")).toBeTruthy());
        expect(onLoadMore).not.toHaveBeenCalled();
    });

    it("renders a retry affordance on error and calls onRetry when clicked", async () => {
        const onRetry = vi.fn();
        renderInfiniteTable({
            data: [],
            onLoadMore: vi.fn(),
            hasNextPage: true,
            isError: true,
            onRetry,
        });
        fireEvent.click(await screen.findByRole("button", { name: "Retry" }));
        expect(onRetry).toHaveBeenCalledTimes(1);
    });

    it("does not fetch when there is no next page", async () => {
        const onLoadMore = vi.fn();
        renderInfiniteTable({ data: makeRows(10), onLoadMore, hasNextPage: false });
        await waitFor(() =>
            expect(screen.getAllByTestId("resource-table-row").length).toBeGreaterThan(0),
        );
        expect(onLoadMore).not.toHaveBeenCalled();
    });

    it("does not fetch while a page is already loading", async () => {
        const onLoadMore = vi.fn();
        renderInfiniteTable({
            data: makeRows(10),
            onLoadMore,
            hasNextPage: true,
            isFetchingNextPage: true,
        });
        await waitFor(() =>
            expect(screen.getAllByTestId("resource-table-row").length).toBeGreaterThan(0),
        );
        expect(onLoadMore).not.toHaveBeenCalled();
    });

    it("renders every row (no windowing) in client-side mode without onLoadMore", () => {
        renderInfiniteTable({ data: makeRows(20) });
        // Non-virtual rows are not tagged; assert the full range is present and the windowed
        // rows are absent (proving the plain client-side render path is untouched).
        expect(screen.getByText("Row 0")).toBeTruthy();
        expect(screen.getByText("Row 19")).toBeTruthy();
        expect(screen.queryAllByTestId("resource-table-row")).toHaveLength(0);
    });

    it("makes the scrolling rows a named, focusable region only when asked", () => {
        renderInfiniteTable({ data: makeRows(3), scrollRegionLabel: "History rows" });

        expect(screen.getByRole("region", { name: "History rows" })).toHaveAttribute(
            "tabindex",
            "0",
        );
    });

    it("leaves the scroller out of the tab order by default", () => {
        renderInfiniteTable({ data: makeRows(3) });

        expect(screen.queryByRole("region")).toBeNull();
    });

    it("no longer renders the removed IntersectionObserver load-more sentinel", () => {
        renderInfiniteTable({ data: makeRows(50), onLoadMore: vi.fn(), hasNextPage: true });
        expect(screen.queryByTestId("resource-table-load-more-sentinel")).toBeNull();
    });
});

// These cover the range *computation* (anchor tracking, inclusive range, skipping
// unselectable rows) once onCheckedChange fires. They can't cover the browser's
// text-selection-vs-activation gate that decides whether the gesture fires at all —
// that's covered by a real-mouse Playwright e2e (see e2e/shift-select.spec.ts).
describe("ResourceTable — shift-click range computation (unit)", () => {
    it("plain click selects a single row", async () => {
        const { user, selectedIds } = renderTable();
        await user.click(rowCheckbox(1));
        expect(selectedIds()).toEqual(["r1"]);
    });

    it("shift-click selects the inclusive range up to the clicked row", async () => {
        const { user, selectedIds } = renderTable();
        await user.click(rowCheckbox(0)); // anchor r0
        await shiftClick(user, 3); // extend to r3
        expect(selectedIds().sort()).toEqual(["r0", "r1", "r2", "r3"]);
    });

    it("shift-click works when the anchor is below the clicked row", async () => {
        const { user, selectedIds } = renderTable();
        await user.click(rowCheckbox(4)); // anchor r4
        await shiftClick(user, 1); // extend up to r1
        expect(selectedIds().sort()).toEqual(["r1", "r2", "r3", "r4"]);
    });

    it("shift-click applies the clicked row's new state — deselecting a range", async () => {
        const { user, selectedIds } = renderTable({
            initial: { r0: true, r1: true, r2: true, r3: true, r4: true },
        });
        await user.click(rowCheckbox(4)); // anchor r4, toggles it off
        await shiftClick(user, 1); // r1 toggles off → range [r1..r4] off
        // Deselected rows must be *removed* from selection, not left as `false`
        // (else bare Object.keys consumers would still act on them).
        expect(selectedIds().sort()).toEqual(["r0"]);
    });

    it("re-anchors on the last clicked row for a subsequent shift-click", async () => {
        const { user, selectedIds } = renderTable();
        await user.click(rowCheckbox(1)); // anchor r1
        await shiftClick(user, 3); // r1..r3
        await shiftClick(user, 4); // re-anchor r3 → r3..r4
        expect(selectedIds().sort()).toEqual(["r1", "r2", "r3", "r4"]);
    });

    it("skips rows that can't be selected when filling the range", async () => {
        const rows: Row[] = [
            { id: "r0", name: "Alpha" },
            { id: "r1", name: "Bravo", selectable: false },
            { id: "r2", name: "Charlie" },
        ];
        const { user, selectedIds } = renderTable({ rows });
        await user.click(rowCheckbox(0)); // anchor r0
        await shiftClick(user, 2); // range [r0..r2], r1 not selectable
        expect(selectedIds().sort()).toEqual(["r0", "r2"]);
    });
});

// ─── Empty state vs error state ─────────────────────────────────────────────────

function renderEmptyTable(props: { emptyMessage?: string; isError?: boolean } = {}) {
    render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <ResourceTable<Row>
                    data={[]}
                    columns={columns}
                    getRowId={(r) => r.id}
                    emptyMessage={props.emptyMessage}
                    isError={props.isError}
                    onRetry={() => {}}
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

describe("ResourceTable — empty state", () => {
    it("renders the default copy when no emptyMessage is given", () => {
        renderEmptyTable();

        expect(screen.getByText("No items to display")).toBeDefined();
    });

    it("renders emptyMessage instead, so 'no matches' reads differently from 'list is empty'", () => {
        renderEmptyTable({ emptyMessage: "No matches for the current filters" });

        expect(screen.getByText("No matches for the current filters")).toBeDefined();
        expect(screen.queryByText("No items to display")).toBeNull();
    });

    it("still shows the error state, not emptyMessage, when the fetch failed", () => {
        // A failed load must never be mistaken for an empty result set.
        renderEmptyTable({ emptyMessage: "No matches for the current filters", isError: true });

        expect(screen.getByText("Couldn't load data")).toBeDefined();
        expect(screen.getByRole("button", { name: /retry/i })).toBeDefined();
        expect(screen.queryByText("No matches for the current filters")).toBeNull();
    });
});

// ─── Scroll reset ───────────────────────────────────────────────────────────────

function renderWithResetKey(resetScrollKey: string) {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <ResourceTable<Row>
                    data={makeRows(50)}
                    columns={columns}
                    getRowId={(r) => r.id}
                    onLoadMore={() => {}}
                    resetScrollKey={resetScrollKey}
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

describe("ResourceTable — resetScrollKey", () => {
    // The stub's spies are reset globally in test/setup.ts, so no local beforeEach.
    it("does not scroll on the initial render", () => {
        renderWithResetKey("search=|sort=");

        expect(virtualizerMock.scrollToOffset).not.toHaveBeenCalled();
    });

    it("returns to the first row when the query identity changes", () => {
        const { rerender } = renderWithResetKey("search=|sort=");

        rerender(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable<Row>
                        data={makeRows(50)}
                        columns={columns}
                        getRowId={(r) => r.id}
                        onLoadMore={() => {}}
                        resetScrollKey="search=|sort=|email,contains,jo"
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );

        expect(virtualizerMock.scrollToOffset).toHaveBeenCalledWith(0);
    });

    it("does not scroll when the key is unchanged across a re-render", () => {
        const { rerender } = renderWithResetKey("search=|sort=");

        rerender(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable<Row>
                        data={makeRows(60)}
                        columns={columns}
                        getRowId={(r) => r.id}
                        onLoadMore={() => {}}
                        resetScrollKey="search=|sort="
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );

        // Loading the next page must not yank the user back to the top.
        expect(virtualizerMock.scrollToOffset).not.toHaveBeenCalled();
    });
});

describe("ResourceTable — selection count disclosure", () => {
    /** Renders with a toolbar (the count lives there) and a fixed, pre-set selection. */
    function renderWithSelection(opts: { selection: RowSelectionState; totalCount?: number }) {
        render(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable<Row>
                        data={ROWS}
                        columns={COLUMNS}
                        getRowId={(r) => r.id}
                        enableRowSelection
                        rowSelection={opts.selection}
                        onRowSelectionChange={() => {}}
                        totalCount={opts.totalCount}
                        toolbar={{}}
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );
    }

    it("reports the selection against the filter total, so select-all reads as partial", () => {
        // The point of the disclosure: 5 loaded rows all selected, but 2002 match the filter —
        // without the total, "select all" looks like it captured everything.
        renderWithSelection({
            selection: { r0: true, r1: true, r2: true, r3: true, r4: true },
            totalCount: 2002,
        });
        expect(screen.getByText("5 of 2002 selected")).toBeInTheDocument();
    });

    it("omits the total in client mode, where every row is already loaded", () => {
        renderWithSelection({ selection: { r0: true, r1: true } });
        expect(screen.getByText("2 selected")).toBeInTheDocument();
    });

    it("shows no count until something is selected", () => {
        renderWithSelection({ selection: {}, totalCount: 2002 });
        expect(screen.queryByText(/selected$/)).not.toBeInTheDocument();
    });
});

describe("ResourceTable — sortable column headers", () => {
    // Sortable headers used to override the <th> role with "button", which dropped them out of
    // the table's semantics and left aria-sort (valid only on columnheader/rowheader) inert —
    // so the same header was a columnheader or not depending on whether its column could sort.
    it("stays a columnheader and reports its sort direction there", async () => {
        const { user } = renderTable();

        const header = screen.getByRole("columnheader", { name: "Name" });
        expect(header).toHaveAttribute("aria-sort", "none");

        await user.click(header);
        expect(header).toHaveAttribute("aria-sort", "ascending");
    });

    it("sorts from the keyboard, so the affordance is not mouse-only", async () => {
        const { user } = renderTable();

        const header = screen.getByRole("columnheader", { name: "Name" });
        header.focus();
        await user.keyboard("{Enter}");

        expect(header).toHaveAttribute("aria-sort", "ascending");
    });
});

describe("ResourceTable — disabled toolbar action reason", () => {
    function renderWithDisabledAction(disabledReason?: string) {
        render(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable<Row>
                        data={makeRows(2)}
                        columns={columns}
                        getRowId={(r) => r.id}
                        toolbar={{
                            actions: [
                                {
                                    label: "Assign",
                                    icon: <span />,
                                    showLabel: true,
                                    disabled: true,
                                    disabledReason,
                                    onClick: () => {},
                                },
                            ],
                        }}
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );
        return screen.getByRole("button", { name: "Assign" });
    }

    it("explains why a disabled toolbar action is unavailable", async () => {
        const user = userEvent.setup();
        const button = renderWithDisabledAction("Clear the selection to assign");

        // A disabled control emits no pointer events, so the reason hangs on a focusable wrapper.
        await user.hover(button.parentElement!);

        expect(await screen.findByText("Clear the selection to assign")).toBeVisible();
    });

    it("leaves an action with no reason unwrapped", () => {
        const button = renderWithDisabledAction(undefined);

        expect(button.parentElement?.getAttribute("tabindex")).toBeNull();
    });
});

describe("ResourceTable — selection action tooltip", () => {
    // jsdom measures every element as 0 wide, which collapses the selection actions into the
    // overflow menu on the next frame. Tests that need them inline report a wide toolbar.
    function widenToolbar() {
        vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(2000);
    }
    afterEach(() => vi.restoreAllMocks());

    function renderWithSelectionActions(
        actions: NonNullable<ResourceTableToolbar["selectionActions"]>,
    ) {
        return render(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable<Row>
                        data={ROWS}
                        columns={COLUMNS}
                        getRowId={(r) => r.id}
                        enableRowSelection
                        rowSelection={{ r0: true }}
                        onRowSelectionChange={() => {}}
                        toolbar={{ selectionActions: actions }}
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );
    }

    function renderWithSelectionAction(overrides: { disabled?: boolean; disabledReason?: string }) {
        renderWithSelectionActions([
            {
                label: "Disable",
                tooltip: "Hide selected from the panel",
                icon: <span />,
                onClick: () => {},
                ...overrides,
            },
        ]);
        return screen.getByRole("button", { name: "Disable" });
    }

    it("keeps the visible label as the name and exposes the tooltip as the description", async () => {
        widenToolbar();
        const user = userEvent.setup();
        const button = renderWithSelectionAction({});

        expect(button).toHaveAccessibleDescription("Hide selected from the panel");
        await user.hover(button);

        expect(await screen.findByText("Hide selected from the panel")).toBeVisible();
    });

    it("renders no toolbar separator when selection actions are the only toolbar content", () => {
        render(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable<Row>
                        data={ROWS}
                        columns={COLUMNS}
                        getRowId={(r) => r.id}
                        enableRowSelection
                        toolbar={{
                            selectionActions: [
                                { label: "Disable", icon: <span />, onClick: () => {} },
                            ],
                        }}
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );

        // With nothing selected the selected-count block, which draws its own separator, is absent.
        expect(screen.queryByRole("separator")).not.toBeInTheDocument();
    });

    it("keeps the tooltip reachable when the action is disabled", async () => {
        widenToolbar();
        const user = userEvent.setup();
        const button = renderWithSelectionAction({ disabled: true });

        await user.hover(button.parentElement!);

        expect(await screen.findByText("Hide selected from the panel")).toBeVisible();
    });

    it("prefers the disabled reason over the tooltip while disabled", async () => {
        widenToolbar();
        const user = userEvent.setup();
        const button = renderWithSelectionAction({
            disabled: true,
            disabledReason: "Add it back first",
        });

        await user.hover(button.parentElement!);

        expect(await screen.findByText("Add it back first")).toBeVisible();
    });

    it("keeps the tooltip and disabled reason on overflow menu items when the toolbar collapses", async () => {
        const user = userEvent.setup();
        renderWithSelectionActions([
            {
                label: "Disable",
                tooltip: "Hide selected from the panel",
                icon: <span />,
                onClick: () => {},
            },
            {
                label: "Export",
                icon: <span />,
                onClick: () => {},
                disabled: true,
                disabledReason: "Not exportable",
            },
        ]);

        // Collapsed: the actions leave the toolbar and the unnamed ellipsis trigger replaces them.
        const trigger = await waitFor(() => {
            expect(screen.queryByRole("button", { name: "Disable" })).not.toBeInTheDocument();
            const found = screen
                .getAllByRole("button")
                .find((b) => !b.textContent && !b.getAttribute("aria-label"));
            expect(found).toBeDefined();
            return found!;
        });
        await user.click(trigger);

        expect(await screen.findByRole("menuitem", { name: "Disable" })).toHaveAttribute(
            "title",
            "Hide selected from the panel",
        );
        expect(screen.getByRole("menuitem", { name: "Export" })).toHaveAttribute(
            "title",
            "Not exportable",
        );
    });
});

describe("ResourceTable — selection action identity", () => {
    beforeEach(() => {
        vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(2000);
    });
    afterEach(() => vi.restoreAllMocks());

    function ToggleHarness({ stableId }: { stableId: boolean }) {
        const [on, setOn] = useState(false);
        return (
            <ResourceTable<Row>
                data={ROWS}
                columns={COLUMNS}
                getRowId={(r) => r.id}
                enableRowSelection
                rowSelection={{ r0: true }}
                onRowSelectionChange={() => {}}
                toolbar={{
                    selectionActions: [
                        {
                            id: stableId ? "toggle" : undefined,
                            label: on ? "Enable" : "Disable",
                            icon: <span />,
                            onClick: () => setOn((v) => !v),
                        },
                    ],
                }}
            />
        );
    }

    function renderHarness(stableId: boolean) {
        return render(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ToggleHarness stableId={stableId} />
                </OcThemeProvider>
            </I18nextProvider>,
        );
    }

    it("keeps keyboard focus on a toggling action that has a stable id", async () => {
        const user = userEvent.setup();
        renderHarness(true);
        const button = screen.getByRole("button", { name: "Disable" });
        button.focus();

        await user.keyboard("{Enter}");

        const toggled = await screen.findByRole("button", { name: "Enable" });
        expect(toggled).toBe(button);
        expect(toggled).toHaveFocus();
    });

    it("remounts a toggling action that has no id, which drops focus", async () => {
        const user = userEvent.setup();
        renderHarness(false);
        const button = screen.getByRole("button", { name: "Disable" });
        button.focus();

        await user.keyboard("{Enter}");

        const toggled = await screen.findByRole("button", { name: "Enable" });
        expect(toggled).not.toBe(button);
        expect(toggled).not.toHaveFocus();
    });
});

describe("ResourceTable — column visibility", () => {
    interface WideRow {
        id: string;
        name: string;
        status: string;
        owner: string;
    }

    const wideColumns: ColumnDef<WideRow>[] = [
        { accessorKey: "name", header: "Name", enableHiding: false },
        { accessorKey: "status", header: "Status" },
        { accessorKey: "owner", header: () => "Owner!", meta: { columnLabel: "Owner" } },
    ];
    const wideRows: WideRow[] = [{ id: "1", name: "Alpha", status: "Live", owner: "Ada" }];

    function renderWide(props: {
        rows?: WideRow[];
        columnVisibility?: Record<string, boolean>;
        onColumnVisibilityChange?: OnChangeFn<VisibilityState>;
        onResetColumnVisibility?: () => void;
    }) {
        render(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable<WideRow>
                        data={props.rows ?? wideRows}
                        columns={wideColumns}
                        getRowId={(r) => r.id}
                        toolbar={{}}
                        columnVisibility={props.columnVisibility}
                        onColumnVisibilityChange={props.onColumnVisibilityChange}
                        onResetColumnVisibility={props.onResetColumnVisibility}
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );
    }

    it("renders no column picker unless the table opts in", () => {
        renderWide({});
        expect(screen.queryByRole("button", { name: /choose columns/i })).not.toBeInTheDocument();
    });

    it("hides the columns the controlled state marks hidden", () => {
        renderWide({ columnVisibility: { status: false }, onColumnVisibilityChange: vi.fn() });
        expect(screen.queryByRole("columnheader", { name: "Status" })).not.toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: "Owner!" })).toBeInTheDocument();
        expect(screen.queryByRole("cell", { name: "Live" })).not.toBeInTheDocument();
        expect(screen.getByRole("cell", { name: "Ada" })).toBeInTheDocument();
    });

    it("lists hideable columns by their columnLabel and leaves out enableHiding: false", async () => {
        const user = userEvent.setup();
        renderWide({ columnVisibility: {}, onColumnVisibilityChange: vi.fn() });
        await user.click(screen.getByRole("button", { name: /choose columns/i }));

        expect(await screen.findByRole("checkbox", { name: "Status" })).toBeChecked();
        expect(screen.getByRole("checkbox", { name: "Owner" })).toBeChecked();
        expect(screen.queryByRole("checkbox", { name: "Name" })).not.toBeInTheDocument();
    });

    it("reports a toggled column through onColumnVisibilityChange", async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        renderWide({ columnVisibility: {}, onColumnVisibilityChange: onChange });
        await user.click(screen.getByRole("button", { name: /choose columns/i }));
        await user.click(await screen.findByRole("checkbox", { name: "Status" }));

        expect(onChange).toHaveBeenCalledWith({ status: false });
    });

    it("offers a reset only when the caller supplies one", async () => {
        const user = userEvent.setup();
        const onReset = vi.fn();
        renderWide({
            columnVisibility: { status: false },
            onColumnVisibilityChange: vi.fn(),
            onResetColumnVisibility: onReset,
        });
        await user.click(screen.getByRole("button", { name: /choose columns/i }));
        await user.click(await screen.findByRole("button", { name: /select all/i }));

        expect(onReset).toHaveBeenCalledTimes(1);
    });

    it("omits Select all when no reset is supplied", async () => {
        const user = userEvent.setup();
        renderWide({ columnVisibility: { status: false }, onColumnVisibilityChange: vi.fn() });
        await user.click(screen.getByRole("button", { name: /choose columns/i }));
        await screen.findByRole("checkbox", { name: "Owner" });

        expect(screen.queryByRole("button", { name: /select all/i })).not.toBeInTheDocument();
    });

    it("spans the empty-state cell across the visible columns only", () => {
        renderWide({
            rows: [],
            columnVisibility: { status: false },
            onColumnVisibilityChange: vi.fn(),
        });

        expect(screen.getByText("No items to display").closest("td")).toHaveAttribute(
            "colspan",
            "2",
        );
    });

    it("drops a column's sort when that column is hidden", async () => {
        const user = userEvent.setup();
        const onSortingChange = vi.fn();
        render(
            <I18nextProvider i18n={i18n}>
                <OcThemeProvider>
                    <ResourceTable<WideRow>
                        data={wideRows}
                        columns={wideColumns}
                        getRowId={(r) => r.id}
                        toolbar={{}}
                        sorting={[{ id: "status", desc: false }]}
                        onSortingChange={onSortingChange}
                        columnVisibility={{}}
                        onColumnVisibilityChange={vi.fn()}
                    />
                </OcThemeProvider>
            </I18nextProvider>,
        );
        await user.click(screen.getByRole("button", { name: /choose columns/i }));
        await user.click(await screen.findByRole("checkbox", { name: "Status" }));

        expect(onSortingChange).toHaveBeenCalledWith([]);
    });
});

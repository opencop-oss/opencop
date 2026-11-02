// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { useState } from "react";
import { act, renderHook } from "~/test";
import type { RowSelectionState } from "@opencop/ui";
import { useSelectedRows } from "./use-selected-rows.hook";

interface Row {
    id: string;
    name: string;
}

const ALL: Row[] = [
    { id: "a", name: "alpha" },
    { id: "b", name: "bravo" },
    { id: "c", name: "charlie" },
];

const getRowId = (row: Row) => row.id;

/** Mirrors a view: the visible rows narrow while the selection state lives above them. */
function renderWithRows(initialRows: Row[]) {
    return renderHook(
        ({ rows }: { rows: Row[] }) => {
            const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
            const selection = useSelectedRows(rows, getRowId, rowSelection, setRowSelection);
            return { ...selection, rowSelection };
        },
        { initialProps: { rows: initialRows } },
    );
}

describe("useSelectedRows", () => {
    it("keeps a selected row after it is filtered out of view", () => {
        // The whole point: the row was captured when it was ticked, so narrowing cannot shrink
        // what a bulk action will act on.
        const { result, rerender } = renderWithRows(ALL);
        act(() => result.current.onRowSelectionChange({ a: true }));

        rerender({ rows: [{ id: "c", name: "charlie" }] });

        expect(result.current.selectedRows).toEqual([{ id: "a", name: "alpha" }]);
    });

    it("accumulates rows ticked under different filters", () => {
        const { result, rerender } = renderWithRows(ALL);
        act(() => result.current.onRowSelectionChange({ a: true }));

        // Search narrows to a row that wasn't visible before, and it is ticked too.
        rerender({ rows: [{ id: "c", name: "charlie" }] });
        act(() => result.current.onRowSelectionChange({ ...result.current.rowSelection, c: true }));

        expect(result.current.selectedRows).toEqual([
            { id: "a", name: "alpha" },
            { id: "c", name: "charlie" },
        ]);
    });

    it("drops a row when it is unticked", () => {
        const { result } = renderWithRows(ALL);
        act(() => result.current.onRowSelectionChange({ a: true, b: true }));

        act(() => result.current.onRowSelectionChange({ b: true }));

        expect(result.current.selectedRows).toEqual([{ id: "b", name: "bravo" }]);
    });

    it("prefers the live row over the captured copy so a refetch's data wins", () => {
        const { result, rerender } = renderWithRows(ALL);
        act(() => result.current.onRowSelectionChange({ a: true }));

        rerender({ rows: [{ id: "a", name: "alpha renamed" }] });

        expect(result.current.selectedRows).toEqual([{ id: "a", name: "alpha renamed" }]);
    });
});

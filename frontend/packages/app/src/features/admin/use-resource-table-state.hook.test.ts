// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { act, renderHook } from "~/test";
import { useResourceTableState } from "./use-resource-table-state.hook";

describe("useResourceTableState", () => {
    it("keeps the selection when the search text changes", () => {
        // Filtering changes what is on screen, not what the user picked — this is what lets a
        // selection be built up across several searches.
        const { result } = renderHook(() => useResourceTableState());
        act(() => result.current.onRowSelectionChange({ a: true, b: true }));

        act(() => result.current.onGlobalFilterChange("storm"));

        expect(result.current.rowSelection).toEqual({ a: true, b: true });
        expect(result.current.globalFilter).toBe("storm");
    });

    it("keeps the selection when a column filter changes", () => {
        const { result } = renderHook(() => useResourceTableState());
        act(() => result.current.onRowSelectionChange({ a: true }));

        act(() => result.current.onColumnFiltersChange([{ id: "name", value: "storm" }]));

        expect(result.current.rowSelection).toEqual({ a: true });
        expect(result.current.columnFilters).toEqual([{ id: "name", value: "storm" }]);
    });

    it("resetView clears the chips, the search text and the selection together", () => {
        const { result } = renderHook(() => useResourceTableState());
        act(() => result.current.onColumnFiltersChange([{ id: "name", value: "storm" }]));
        act(() => result.current.onGlobalFilterChange("storm"));
        act(() => result.current.onRowSelectionChange({ a: true }));

        act(() => result.current.resetView());

        expect(result.current.columnFilters).toEqual([]);
        expect(result.current.globalFilter).toBe("");
        expect(result.current.rowSelection).toEqual({});
    });

    it("resetSelection leaves the filters standing", () => {
        const { result } = renderHook(() => useResourceTableState());
        act(() => result.current.onGlobalFilterChange("storm"));
        act(() => result.current.onRowSelectionChange({ a: true }));

        act(() => result.current.resetSelection());

        expect(result.current.rowSelection).toEqual({});
        expect(result.current.globalFilter).toBe("storm");
    });

    it("seeds the filter bar from initialColumnFilters", () => {
        const seeded = [{ id: "status", value: "active" }];

        const { result } = renderHook(() => useResourceTableState(seeded));

        expect(result.current.columnFilters).toEqual(seeded);
        expect(result.current.rowSelection).toEqual({});
    });
});

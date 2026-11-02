// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, renderHook } from "~/test";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { TABLE_COLUMNS_STORAGE_KEY, useTableColumnsStore } from "./table-columns.store";
import { useTableColumnVisibility } from "./use-table-column-visibility.hook";

function resetStore() {
    useTableColumnsStore.setState({ hiddenColumnsByTable: {} });
    localStorage.removeItem(TABLE_COLUMNS_STORAGE_KEY);
}

beforeEach(resetStore);
afterEach(resetStore);

describe("useTableColumnVisibility", () => {
    it("starts with every column visible", () => {
        const { result } = renderHook(() => useTableColumnVisibility("my-layers"));
        expect(result.current.columnVisibility).toEqual({});
    });

    it("hides a column when the table reports it hidden, and shows it again", () => {
        const { result } = renderHook(() => useTableColumnVisibility("my-layers"));

        act(() => result.current.onColumnVisibilityChange({ updatedAt: false }));
        expect(result.current.columnVisibility).toEqual({ updatedAt: false });

        act(() => result.current.onColumnVisibilityChange({}));
        expect(result.current.columnVisibility).toEqual({});
    });

    it("accepts a functional update", () => {
        const { result } = renderHook(() => useTableColumnVisibility("my-layers"));

        act(() =>
            result.current.onColumnVisibilityChange((prev) => ({ ...prev, updatedAt: false })),
        );

        expect(result.current.columnVisibility).toEqual({ updatedAt: false });
    });

    it("does not leak a choice into another table", () => {
        const mine = renderHook(() => useTableColumnVisibility("my-layers"));
        const admin = renderHook(() => useTableColumnVisibility("admin-layers"));

        act(() => mine.result.current.onColumnVisibilityChange({ updatedAt: false }));

        expect(admin.result.current.columnVisibility).toEqual({});
    });

    it("restores the defaults", () => {
        const { result } = renderHook(() => useTableColumnVisibility("my-layers"));
        act(() => result.current.onColumnVisibilityChange({ updatedAt: false }));

        act(() => result.current.onResetColumnVisibility());

        expect(result.current.columnVisibility).toEqual({});
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { TABLE_COLUMNS_STORAGE_KEY, useTableColumnsStore } from "./table-columns.store";
import type { ColumnTableId } from "./table-columns.store";

function resetStore() {
    useTableColumnsStore.setState({ hiddenColumnsByTable: {} });
    localStorage.removeItem(TABLE_COLUMNS_STORAGE_KEY);
}

beforeEach(resetStore);
afterEach(resetStore);

const hiddenFor = (tableId: ColumnTableId) =>
    useTableColumnsStore.getState().hiddenColumnsByTable[tableId];

describe("table columns store", () => {
    it("hides columns and clears the choice when emptied", () => {
        const { setHiddenColumns } = useTableColumnsStore.getState().actions;
        setHiddenColumns("my-layers", ["lastEventAt"]);
        expect(hiddenFor("my-layers")).toEqual(["lastEventAt"]);

        setHiddenColumns("my-layers", []);
        expect(hiddenFor("my-layers")).toBeUndefined();
    });

    it("keeps each table's choice independent", () => {
        const { setHiddenColumns } = useTableColumnsStore.getState().actions;
        setHiddenColumns("my-layers", ["updatedAt"]);
        setHiddenColumns("admin-layers", ["createdAt"]);

        expect(hiddenFor("my-layers")).toEqual(["updatedAt"]);
        expect(hiddenFor("admin-layers")).toEqual(["createdAt"]);
    });

    it("does not duplicate a column listed twice", () => {
        useTableColumnsStore
            .getState()
            .actions.setHiddenColumns("my-layers", ["updatedAt", "updatedAt"]);

        expect(hiddenFor("my-layers")).toEqual(["updatedAt"]);
    });

    it("leaves state untouched when the set is unchanged", () => {
        const { setHiddenColumns } = useTableColumnsStore.getState().actions;
        setHiddenColumns("my-layers", ["updatedAt"]);
        const before = useTableColumnsStore.getState().hiddenColumnsByTable;

        setHiddenColumns("my-layers", ["updatedAt"]);

        expect(useTableColumnsStore.getState().hiddenColumnsByTable).toBe(before);
    });

    it("resets one table without touching another", () => {
        const { setHiddenColumns, resetColumns } = useTableColumnsStore.getState().actions;
        setHiddenColumns("my-layers", ["updatedAt"]);
        setHiddenColumns("admin-layers", ["updatedAt"]);

        resetColumns("my-layers");

        expect(hiddenFor("my-layers")).toBeUndefined();
        expect(hiddenFor("admin-layers")).toEqual(["updatedAt"]);
    });

    it("persists under the namespaced storage key", () => {
        useTableColumnsStore.getState().actions.setHiddenColumns("my-layers", ["updatedAt"]);

        const persisted = JSON.parse(localStorage.getItem(TABLE_COLUMNS_STORAGE_KEY)!);
        expect(persisted.state.hiddenColumnsByTable).toEqual({ "my-layers": ["updatedAt"] });
    });

    it("rehydrates a saved choice", async () => {
        localStorage.setItem(
            TABLE_COLUMNS_STORAGE_KEY,
            JSON.stringify({
                state: { hiddenColumnsByTable: { "my-layers": ["updatedAt"] } },
                version: 0,
            }),
        );

        await useTableColumnsStore.persist.rehydrate();

        expect(hiddenFor("my-layers")).toEqual(["updatedAt"]);
    });

    it("drops non-array entries and non-string ids on hydration", async () => {
        localStorage.setItem(
            TABLE_COLUMNS_STORAGE_KEY,
            JSON.stringify({
                state: {
                    hiddenColumnsByTable: {
                        "my-layers": ["updatedAt", 7, "updatedAt", null],
                        "admin-layers": "createdAt",
                    },
                },
                version: 0,
            }),
        );

        await useTableColumnsStore.persist.rehydrate();

        expect(useTableColumnsStore.getState().hiddenColumnsByTable).toEqual({
            "my-layers": ["updatedAt"],
        });
    });

    it("falls back to nothing hidden for a corrupted blob", async () => {
        localStorage.setItem(
            TABLE_COLUMNS_STORAGE_KEY,
            JSON.stringify({ state: { hiddenColumnsByTable: 12 }, version: 0 }),
        );

        await useTableColumnsStore.persist.rehydrate();

        expect(useTableColumnsStore.getState().hiddenColumnsByTable).toEqual({});
    });
});

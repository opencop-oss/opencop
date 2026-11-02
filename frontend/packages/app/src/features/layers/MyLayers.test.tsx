// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, userEvent } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { layerQueries } from "~/features/layers/layers.queries";
import { layerGroupQueries } from "~/features/layer-groups/layer-groups.queries";
import { MyLayers } from "./MyLayers";

/**
 * ResourceTable and ColumnFilterBuilder are zag.js/virtualizer surfaces that don't drive in jsdom. The
 * double flattens the toolbar, including each action's `menuItems`, into buttons, the only thing
 * these tests care about.
 */
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    interface MenuItem {
        label: string;
    }
    interface Action {
        label: string;
        disabled?: boolean;
        menuItems?: MenuItem[];
    }
    interface Row {
        id: string;
        name: string;
    }
    interface TableProps {
        "aria-label"?: string;
        data?: Row[];
        enableRowSelection?: (row: Row) => boolean;
        onRowSelectionChange?: (
            updater: (prev: Record<string, boolean>) => Record<string, boolean>,
        ) => void;
        toolbar?: { actions?: Action[]; selectionActions?: Action[] };
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        ResourceTable: (props: TableProps) =>
            createElement(
                "div",
                { "aria-label": props["aria-label"] },
                ...(props.data ?? [])
                    .filter((row) => props.enableRowSelection?.(row) ?? false)
                    .map((row) =>
                        createElement(
                            "button",
                            {
                                key: `select-${row.id}`,
                                type: "button",
                                onClick: () =>
                                    props.onRowSelectionChange?.((prev) => ({
                                        ...prev,
                                        [row.id]: true,
                                    })),
                            },
                            `select ${row.name}`,
                        ),
                    ),
                ...(props.toolbar?.selectionActions ?? []).map((action) =>
                    createElement(
                        "button",
                        { key: `sel-${action.label}`, type: "button", disabled: action.disabled },
                        action.label,
                    ),
                ),
                ...(props.toolbar?.actions ?? []).flatMap((action) => [
                    createElement("button", { key: action.label, type: "button" }, action.label),
                    ...(action.menuItems ?? []).map((item) =>
                        createElement(
                            "button",
                            { key: `${action.label}/${item.label}`, type: "button" },
                            item.label,
                        ),
                    ),
                ]),
            ),
    };
});

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

const seeded = {
    [JSON.stringify(layerQueries.myList(false).queryKey)]: [],
    [JSON.stringify(layerGroupQueries.myList().queryKey)]: [],
};

describe("MyLayers create menu", () => {
    /**
     * The only entry point for creating a layer group in the non-admin flow, and nothing else in
     * the suite would notice its removal.
     */
    it("offers New Layer Group alongside New Layer", () => {
        render(<MyLayers />, { queryData: seeded });

        expect(
            screen.getByRole("button", { name: i18n.t("adminLayerGroups.newLayerGroup") }),
        ).toBeTruthy();
        expect(screen.getByRole("button", { name: i18n.t("adminLayers.newLayer") })).toBeTruthy();
    });
});

describe("MyLayers bulk selection", () => {
    const owned = {
        layerId: "owned",
        layerName: "Owned Layer",
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    };
    const member = { ...owned, layerId: "member", layerName: "Member Layer", role: "MEMBER" };

    it("lets a mixed-role selection be disabled while role-gated actions stay unavailable", async () => {
        render(<MyLayers />, {
            queryData: {
                ...seeded,
                [JSON.stringify(layerQueries.myList(false).queryKey)]: [owned, member],
            },
        });

        await userEvent.click(screen.getByRole("button", { name: "select Owned Layer" }));
        await userEvent.click(screen.getByRole("button", { name: "select Member Layer" }));

        expect(screen.getByRole("button", { name: "Disable" })).toBeEnabled();
        const roleGated = [
            i18n.t("mergeLayers.merge"),
            i18n.t("common.export"),
            i18n.t("common.delete"),
        ];
        for (const name of roleGated) {
            expect(screen.getByRole("button", { name })).toBeDisabled();
        }
    });
});

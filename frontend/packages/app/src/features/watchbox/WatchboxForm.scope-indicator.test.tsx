// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyLayerInfo } from "@opencop/api";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, userEvent } from "~/test";
import { layerQueries } from "~/features/layers/layers.queries";
import { useCustomAttributesStore } from "~/features/filter-fields/custom-attributes.store";
import { WatchboxForm } from "./WatchboxForm";

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

// Deliberately NOT mocking "@opencop/ui" (unlike WatchboxForm.test.tsx) — this
// file exists specifically to exercise the real OcCombobox's accessible name
// for a GLOBAL layer's option, which the mocked double can't produce.
vi.mock("spin-delay", () => ({ useSpinDelay: (loading: boolean) => loading }));

const POLYGON = {
    type: "Polygon" as const,
    coordinates: [
        [
            [0, 0],
            [1, 0],
            [1, 1],
            [0, 0],
        ],
    ],
};

const GLOBAL_MANAGED_LAYER: MyLayerInfo = {
    layerId: "global-managed-layer",
    layerName: "Global Managed Layer",
    role: "OWNER",
    accessScope: "GLOBAL",
    defaultVisible: true,
};

const PRIVATE_LAYER: MyLayerInfo = {
    layerId: "private-layer",
    layerName: "Private Layer",
    role: "OWNER",
    accessScope: "PRIVATE",
    defaultVisible: true,
};

beforeEach(() => {
    act(() => useCustomAttributesStore.setState({ names: [] }));
});

describe("WatchboxForm — layer picker scope indicator accessibility", () => {
    it("announces a GLOBAL layer option's scope to screen readers, leaves a PRIVATE option's name alone", async () => {
        const user = userEvent.setup();
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: {
                [JSON.stringify(layerQueries.myList().queryKey)]: [
                    GLOBAL_MANAGED_LAYER,
                    PRIVATE_LAYER,
                ],
                [JSON.stringify(layerQueries.dataSources(GLOBAL_MANAGED_LAYER.layerId).queryKey)]: [
                    { serverId: "src-0" },
                ],
                [JSON.stringify(layerQueries.dataSources(PRIVATE_LAYER.layerId).queryKey)]: [
                    { serverId: "src-1" },
                ],
            },
        });

        await user.click(await screen.findByPlaceholderText("Select layers"));

        // The globe icon's only text alternative was a hover tooltip, which a
        // listbox option (focus stays on the combobox input; options are
        // tabIndex: -1) never triggers — so the accessible name used to be
        // exactly the layer name, identical to the PRIVATE option's.
        expect(
            await screen.findByRole("option", { name: /Global Managed Layer/ }),
        ).toHaveAccessibleName(/Global$/);
        expect(screen.getByRole("option", { name: "Private Layer" })).toHaveAccessibleName(
            "Private Layer",
        );
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { dataSourceRoleOptions } from "~/features/data-sources/data-sources.util";
import { layerQueries } from "~/features/layers/layers.queries";
import { DataSourceLayersSection } from "./DataSourceLayersSection";

const { mockUseRelationSection } = vi.hoisted(() => ({ mockUseRelationSection: vi.fn() }));

// Captured-config mock: stub the presentational relation section (surfacing `readOnly`, the one
// prop this section passes outside the hook config) and spy on the hook.
// Which flavor to pick (drive-the-real-section vs captured-config) is documented in
// docs/architecture/admin-layouts.md § Testing. Mocked by module path rather than through the
// `~/features/drawers` barrel so the spec survives removal of that barrel.
vi.mock("~/features/drawers/DrawerRelationSection", () => ({
    DrawerRelationSection: (props: { readOnly?: boolean }) => (
        <div data-testid="relation-section" data-read-only={String(!!props.readOnly)} />
    ),
}));
vi.mock("~/features/drawers/use-relation-section.hook", () => ({
    useRelationSection: mockUseRelationSection,
}));

interface Captured {
    roleOptions?: unknown;
    labels: { rolePlaceholder?: string };
    pool: { id: string; name: string }[];
}
const config = () => mockUseRelationSection.mock.calls.at(-1)?.[0] as Captured;

// Both pools are seeded so neither scope fetches; the assertion is about which one the section
// reads, not about the wire format.
const POOL_SEED = {
    queryData: {
        [JSON.stringify(layerQueries.adminList().queryKey)]: [
            { id: "admin-layer", name: "Every Layer" },
        ],
        [JSON.stringify(layerQueries.myList().queryKey)]: [
            { layerId: "my-layer", layerName: "My Layer" },
        ],
    },
};

describe("DataSourceLayersSection", () => {
    beforeEach(() => {
        mockUseRelationSection.mockReset();
        mockUseRelationSection.mockReturnValue({});
    });

    it("grants data-source roles to the assigned layers", () => {
        render(<DataSourceLayersSection serverId="s1" />, POOL_SEED);

        // A layer granted access to a source holds a *source* role (VIEWER/ADMIN/OWNER), not a
        // layer role — the two ladders don't even have the same length.
        expect(config().roleOptions).toEqual(dataSourceRoleOptions(i18n.t));
        expect(config().labels.rolePlaceholder).toBe(i18n.t("adminDialogs.rolePlaceholder"));
    });

    it("picks the assignable pool from the requested layer scope", () => {
        render(<DataSourceLayersSection serverId="s1" layerScope="admin" />, POOL_SEED);
        expect(config().pool).toEqual([{ id: "admin-layer", name: "Every Layer" }]);

        // "my" is the non-admin drawer's pool and arrives in a different shape, so the section
        // normalises it — a scope wired to the wrong query silently offers layers the caller can't file.
        render(<DataSourceLayersSection serverId="s1" layerScope="my" />, POOL_SEED);
        expect(config().pool).toEqual([{ id: "my-layer", name: "My Layer" }]);
    });

    it("forwards read-only to the relation section", () => {
        render(<DataSourceLayersSection serverId="s1" readOnly />, POOL_SEED);

        expect(screen.getByTestId("relation-section").dataset.readOnly).toBe("true");
    });
});

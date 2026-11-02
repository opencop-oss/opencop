// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { groupQueries } from "~/features/groups/groups.queries";
import { layerRoleOptions } from "~/features/layers/layers.util";
import { LayerUserGroupsSection } from "./LayerUserGroupsSection";

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
}
const config = () => mockUseRelationSection.mock.calls.at(-1)?.[0] as Captured;

// Seeded so the admin group pool resolves without a fetch (the harness pins staleTime).
const POOL_SEED = { queryData: { [JSON.stringify(groupQueries.adminList().queryKey)]: [] } };

describe("LayerUserGroupsSection", () => {
    beforeEach(() => {
        mockUseRelationSection.mockReset();
        mockUseRelationSection.mockReturnValue({});
    });

    it("grants layer roles to the assigned user groups", () => {
        render(<LayerUserGroupsSection layerId="l1" />, POOL_SEED);

        expect(config().roleOptions).toEqual(layerRoleOptions(i18n.t));
        expect(config().labels.rolePlaceholder).toBe(i18n.t("adminDialogs.rolePlaceholder"));
    });

    it("forwards read-only to the relation section", () => {
        // Group assign/revoke are admin-only server operations, so the My Layers drawer mounts this
        // view-only. Losing the flag would offer controls every request then 403s.
        render(<LayerUserGroupsSection layerId="l1" readOnly />, POOL_SEED);

        expect(screen.getByTestId("relation-section").dataset.readOnly).toBe("true");
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { layerQueries } from "~/features/layers/layers.queries";
import { layerRoleOptions } from "~/features/layers/layers.util";
import { GroupLayersSection } from "./GroupLayersSection";

const { mockUseRelationSection } = vi.hoisted(() => ({ mockUseRelationSection: vi.fn() }));

// Captured-config mock: stub the presentational relation section and spy on the hook.
// Which flavor to pick (drive-the-real-section vs captured-config) is documented in
// docs/architecture/admin-layouts.md § Testing. Mocked by module path rather than through the
// `~/features/drawers` barrel so the spec survives removal of that barrel.
vi.mock("~/features/drawers/DrawerRelationSection", () => ({
    DrawerRelationSection: () => null,
}));
vi.mock("~/features/drawers/use-relation-section.hook", () => ({
    useRelationSection: mockUseRelationSection,
}));

interface Captured {
    roleOptions?: unknown;
    labels: { rolePlaceholder?: string };
}
const config = () => mockUseRelationSection.mock.calls.at(-1)?.[0] as Captured;

describe("GroupLayersSection", () => {
    beforeEach(() => {
        mockUseRelationSection.mockReset();
        mockUseRelationSection.mockReturnValue({});
    });

    it("grants layer roles, not group roles, and labels the role select", () => {
        render(<GroupLayersSection groupId="g1" />, {
            // Seeded so the assignable pool resolves without a fetch (the harness pins staleTime).
            queryData: { [JSON.stringify(layerQueries.adminList().queryKey)]: [] },
        });

        // A group→layer assignment grants a role *on the layer*, so the ladder must be the layer
        // one — the group's own ladder here would silently offer roles the server rejects.
        expect(config().roleOptions).toEqual(layerRoleOptions(i18n.t));
        expect(config().labels.rolePlaceholder).toBe(i18n.t("adminDialogs.rolePlaceholder"));
    });
});

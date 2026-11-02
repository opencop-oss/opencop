// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { dataSourceRoleOptions } from "~/features/data-sources/data-sources.util";
import { DataSourceUsersSection } from "./DataSourceUsersSection";

const { mockUseRelationSection, serverPaging } = vi.hoisted(() => ({
    mockUseRelationSection: vi.fn(),
    serverPaging: {
        onSearchChange: () => {},
        onLoadMore: () => {},
        hasMore: false,
        isFetchingMore: false,
        isError: false,
        onRetry: () => {},
    },
}));

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

vi.mock("~/features/users/use-user-picker-pool.hook", () => ({
    useUserPickerPool: () => ({ pool: [], poolLoading: false, serverPaging }),
}));

interface Captured {
    roleOptions?: unknown;
    labels: { rolePlaceholder?: string };
    serverPaging?: unknown;
}
const config = () => mockUseRelationSection.mock.calls.at(-1)?.[0] as Captured;

describe("DataSourceUsersSection", () => {
    beforeEach(() => {
        mockUseRelationSection.mockReset();
        mockUseRelationSection.mockReturnValue({});
    });

    it("grants data-source roles to the assigned users", () => {
        render(<DataSourceUsersSection serverId="s1" />);

        expect(config().roleOptions).toEqual(dataSourceRoleOptions(i18n.t));
        expect(config().labels.rolePlaceholder).toBe(i18n.t("adminDialogs.rolePlaceholder"));
    });

    it("drives the assign picker from the server-paged user pool", () => {
        render(<DataSourceUsersSection serverId="s1" />);

        expect(config().serverPaging).toBe(serverPaging);
    });

    it("forwards read-only to the relation section", () => {
        render(<DataSourceUsersSection serverId="s1" readOnly />);

        expect(screen.getByTestId("relation-section").dataset.readOnly).toBe("true");
    });
});

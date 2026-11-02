// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { GroupMembersSection } from "./GroupMembersSection";

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

vi.mock("~/features/users/use-user-picker-pool.hook", () => ({
    useUserPickerPool: () => ({ pool: [], poolLoading: false, serverPaging }),
}));

interface Captured {
    roleOptions?: unknown;
    onChangeRole?: unknown;
    labels: { rolePlaceholder?: string };
    serverPaging?: unknown;
}
const config = () => mockUseRelationSection.mock.calls.at(-1)?.[0] as Captured;

describe("GroupMembersSection", () => {
    beforeEach(() => {
        mockUseRelationSection.mockReset();
        mockUseRelationSection.mockReturnValue({});
    });

    it("offers no role vocabulary — group membership carries no role", () => {
        render(<GroupMembersSection groupId="g1" />);

        // The only relation in the family with no role ladder at all: nothing may make the picker
        // demand a role, and no bulk change action may appear.
        expect(config().roleOptions).toBeUndefined();
        expect(config().labels.rolePlaceholder).toBeUndefined();
        expect(config().onChangeRole).toBeUndefined();
    });

    it("drives the assign picker from the server-paged user pool", () => {
        render(<GroupMembersSection groupId="g1" />);

        expect(config().serverPaging).toBe(serverPaging);
    });
});

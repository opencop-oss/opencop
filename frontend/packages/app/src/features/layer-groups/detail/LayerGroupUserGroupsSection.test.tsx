// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { layerGroupRoleOptions } from "~/features/layer-groups/layer-groups.util";
import { layerRoleOptions } from "~/features/layers/layers.util";
import { LayerGroupUserGroupsSection } from "./LayerGroupUserGroupsSection";

const { mockUseRelationSection } = vi.hoisted(() => ({ mockUseRelationSection: vi.fn() }));

// Captured-config mock: stub the presentational relation section (surfacing `readOnly`, the one
// prop this section passes outside the hook config) and spy on the hook, so a test can read the
// config it receives or drive its `onAssign` directly. The section's own data-fetching (candidate
// pool, assign mutation) still runs for real against MSW.
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

const GROUP_ID = "g1";
const POOL_URL = `${API_BASE}/user-groups/search`;
const PATCH_URL = `${API_BASE}/layer-groups/${GROUP_ID}/user-groups`;
// The section reads the assigned list directly to build the change-role rows, so this fires even
// though `useRelationSection` — normally the only fetcher — is stubbed out below.
const ASSIGNED_URL = `${API_BASE}/layer-groups/:id/user-groups`;

const EMPTY_PAGE = { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 };
const CANDIDATE = { id: "ug1", name: "Recon Team" };
const ONE_CANDIDATE_PAGE = {
    content: [CANDIDATE],
    page: 0,
    size: 25,
    totalElements: 1,
    totalPages: 1,
};

/** Resolves the picker pool empty, for the cases whose assertions are not about candidates. */
function serveEmptyPool(): void {
    server.use(http.get(POOL_URL, () => HttpResponse.json(EMPTY_PAGE)));
}

function renderSection(readOnly: boolean) {
    render(<LayerGroupUserGroupsSection groupId={GROUP_ID} readOnly={readOnly} />);
}

describe("LayerGroupUserGroupsSection", () => {
    beforeEach(() => {
        mockUseRelationSection.mockReset();
        mockUseRelationSection.mockReturnValue({});
        server.use(http.get(ASSIGNED_URL, () => HttpResponse.json([])));
    });

    it("grants layer-group roles, not layer roles", () => {
        serveEmptyPool();
        renderSection(false);

        // The two ladders differ at one rung — MAINTAINER vs EDITOR — so the wrong list still
        // typechecks against `string` and only shows up as a role the server refuses.
        expect(config().roleOptions).toEqual(layerGroupRoleOptions(i18n.t));
        expect(config().roleOptions).not.toEqual(layerRoleOptions(i18n.t));
        expect(config().labels.rolePlaceholder).toBe(i18n.t("adminDialogs.rolePlaceholder"));
    });

    it("forwards read-only to the relation section", () => {
        serveEmptyPool();
        renderSection(true);

        expect(screen.getByTestId("relation-section").dataset.readOnly).toBe("true");
    });

    it("passes the fetched candidates to the picker", async () => {
        server.use(http.get(POOL_URL, () => HttpResponse.json(ONE_CANDIDATE_PAGE)));
        renderSection(false);

        // The pool comes from the authenticated search route, so a non-admin
        // owner gets candidates instead of the 403 the admin listing returned. Asserted on what
        // the picker receives, since a fired request alone would still pass with the pool
        // disconnected from useRelationSection.
        await waitFor(() =>
            expect(mockUseRelationSection.mock.calls.at(-1)?.[0]).toMatchObject({
                pool: [CANDIDATE],
            }),
        );
    });

    it("does not load the candidate pool for a read-only section", async () => {
        let poolFetched = false;
        server.use(
            http.get(POOL_URL, () => {
                poolFetched = true;
                return HttpResponse.json(EMPTY_PAGE);
            }),
        );
        renderSection(true);

        // A read-only viewer can never reach the assign picker, so the pool is dead weight.
        await screen.findByTestId("relation-section");
        expect(poolFetched).toBe(false);
    });

    it("sends the selected role with each assigned user group", async () => {
        let patchBody: unknown;
        server.use(
            http.get(POOL_URL, () => HttpResponse.json(EMPTY_PAGE)),
            http.patch(PATCH_URL, async ({ request }) => {
                patchBody = await request.json();
                return new HttpResponse(null, { status: 200 });
            }),
        );
        renderSection(false);
        await screen.findByTestId("relation-section");

        const relationOptions = mockUseRelationSection.mock.calls.at(-1)?.[0] as {
            onAssign: (ids: string[], role?: string) => Promise<unknown>;
        };
        await relationOptions.onAssign(["ug1", "ug2"], "MAINTAINER");

        // The server applies a role ceiling against the caller's own role, so the role has to
        // travel per-group in the payload rather than being inferred anywhere downstream.
        expect(patchBody).toEqual({
            add: [
                { userGroupId: "ug1", role: "MAINTAINER" },
                { userGroupId: "ug2", role: "MAINTAINER" },
            ],
        });
    });

    it.each([
        // An OWNER may grant every rung, including OWNER.
        ["OWNER", ["MEMBER", "MAINTAINER", "ADMIN", "OWNER"]],
        // An ADMIN may not grant OWNER: patchUserGroups rejects anything above the actor's own
        // role with an opaque 403, so the option must not be offered at all.
        ["ADMIN", ["MEMBER", "MAINTAINER", "ADMIN"]],
    ])("offers a %s only the roles that actor may actually grant", async (actorRole, expected) => {
        serveEmptyPool();
        render(
            <LayerGroupUserGroupsSection
                groupId={GROUP_ID}
                actorRole={actorRole as "OWNER" | "ADMIN"}
                readOnly={false}
            />,
        );
        await screen.findByTestId("relation-section");

        const options = mockUseRelationSection.mock.calls.at(-1)?.[0] as {
            roleOptions: { value: string }[];
        };
        expect(options.roleOptions.map((o) => o.value)).toEqual(expected);
    });

    it("leaves the roles unclamped when no actor role is given", async () => {
        // The admin drawer omits actorRole; global admins are exempt from the ceiling server-side.
        serveEmptyPool();
        renderSection(false);
        await screen.findByTestId("relation-section");

        const options = mockUseRelationSection.mock.calls.at(-1)?.[0] as {
            roleOptions: { value: string }[];
        };
        expect(options.roleOptions.map((o) => o.value)).toEqual([
            "MEMBER",
            "MAINTAINER",
            "ADMIN",
            "OWNER",
        ]);
    });

    it("changes an assigned group's role through the same upsert call", async () => {
        let patchBody: unknown;
        server.use(
            http.get(POOL_URL, () => HttpResponse.json(EMPTY_PAGE)),
            http.patch(PATCH_URL, async ({ request }) => {
                patchBody = await request.json();
                return new HttpResponse(null, { status: 200 });
            }),
        );
        renderSection(false);
        await screen.findByTestId("relation-section");

        const relationOptions = mockUseRelationSection.mock.calls.at(-1)?.[0] as {
            onChangeRole: (ids: string[], role: string) => Promise<unknown>;
        };
        await relationOptions.onChangeRole(["ug1"], "ADMIN");

        // applyUserGroupPatch upserts on an existing row, so a role change is an `add`.
        expect(patchBody).toEqual({ add: [{ userGroupId: "ug1", role: "ADMIN" }] });
    });

    it("marks the provisioned one when two candidates share a name", async () => {
        server.use(
            http.get(POOL_URL, () =>
                HttpResponse.json({
                    content: [
                        { id: "ug1", name: "Operators", source: "OPENCOP" },
                        { id: "ug2", name: "Operators", source: "SCIM" },
                    ],
                    page: 0,
                    size: 25,
                    totalElements: 2,
                    totalPages: 1,
                }),
            ),
        );
        renderSection(false);

        // Two identical names would otherwise be indistinguishable on an access grant.
        await waitFor(() =>
            expect(mockUseRelationSection.mock.calls.at(-1)?.[0]).toMatchObject({
                pool: [
                    { id: "ug1", source: "OPENCOP" },
                    { id: "ug2", source: "SCIM" },
                ],
            }),
        );
    });

    it("does not call the server when no role was chosen", async () => {
        let patchCalled = false;
        server.use(
            http.get(POOL_URL, () => HttpResponse.json(EMPTY_PAGE)),
            http.patch(PATCH_URL, () => {
                patchCalled = true;
                return new HttpResponse(null, { status: 200 });
            }),
        );
        renderSection(false);
        await screen.findByTestId("relation-section");

        const relationOptions = mockUseRelationSection.mock.calls.at(-1)?.[0] as {
            onAssign: (ids: string[], role?: string) => Promise<unknown>;
        };
        await relationOptions.onAssign(["ug1"], undefined);

        expect(patchCalled).toBe(false);
    });
});

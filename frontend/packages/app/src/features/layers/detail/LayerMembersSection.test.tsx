// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import {
    render,
    screen,
    waitFor,
    within,
    userEvent,
    server,
    http,
    HttpResponse,
    API_BASE,
} from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { LayerRole, LayerUser } from "@opencop/api";
import { i18n } from "~/i18n/config";
import { layerQueries } from "../layers.queries";
import { LayerMembersSection } from "./LayerMembersSection";

// Network is mocked at the HTTP boundary (MSW): the section, hook, mutation, the real
// updateLayerMembership (which builds the role-bucketed PATCH body), and DrawerRelationSection
// all run for real. Reads are seeded into the cache for an instant first render; the PATCH is
// captured in `patchRequests` so tests assert the actual wire contract.

// ResourceTable/OcSelect are Chakra v3 (zag.js) widgets that don't drive in jsdom. Replace them with
// minimal doubles: one button per row that toggles selection, the toolbar's selection actions as
// buttons, and OcSelect as a native <select>. The bulk "Change role" flow (selection action → dialog
// → role select → Apply) is exercised here; the inline per-row dropdown lives in a table cell the
// double does not render, and is covered by manual verification.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    type Row = Record<string, unknown>;
    interface TableProps {
        "aria-label"?: string;
        data: Row[];
        getRowId: (row: Row) => string;
        rowSelection?: Record<string, boolean>;
        onRowSelectionChange?: (next: Record<string, boolean>) => void;
        toolbar?: {
            leading?: ReactNode;
            actions?: { label: string; onClick: () => void }[];
            selectionActions?: { label: string; onClick: () => void; disabled?: boolean }[];
        };
    }
    interface SelectProps {
        items: { label: string; value: string }[];
        value?: string;
        onValueChange: (value: string) => void;
        placeholder?: string;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        ResourceTable: (props: TableProps) =>
            createElement(
                "div",
                { "aria-label": props["aria-label"] },
                props.toolbar?.leading,
                ...(props.toolbar?.actions ?? []).map((a) =>
                    createElement(
                        "button",
                        { key: a.label, type: "button", onClick: a.onClick },
                        a.label,
                    ),
                ),
                ...(props.toolbar?.selectionActions ?? []).map((a) =>
                    createElement(
                        "button",
                        { key: a.label, type: "button", onClick: a.onClick, disabled: a.disabled },
                        a.label,
                    ),
                ),
                ...props.data.map((row) => {
                    const id = props.getRowId(row);
                    const selected = !!props.rowSelection?.[id];
                    return createElement(
                        "div",
                        { key: id },
                        createElement(
                            "button",
                            {
                                type: "button",
                                onClick: () => {
                                    const next = { ...(props.rowSelection ?? {}) };
                                    if (selected) delete next[id];
                                    else next[id] = true;
                                    props.onRowSelectionChange?.(next);
                                },
                            },
                            `row ${id}`,
                        ),
                        // Surface the row's live role so tests can assert the list reflects updates.
                        createElement("span", { "data-testid": `role-${id}` }, String(row.role)),
                    );
                }),
            ),
        OcSelect: (props: SelectProps) =>
            createElement(
                "select",
                {
                    "aria-label": "role-select",
                    value: props.value ?? "",
                    onChange: (e: { target: { value: string } }) =>
                        props.onValueChange(e.target.value),
                },
                createElement("option", { value: "" }, props.placeholder ?? ""),
                ...props.items.map((it) =>
                    createElement("option", { key: it.value, value: it.value }, it.label),
                ),
            ),
    };
});

// The toast surface is asserted directly via this spy — no need to mount the notification stack.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

// The assign-user pool is server-paged (useUserPickerPool); these tests exercise the members
// list, not the pool, so stub it empty.
vi.mock("~/features/users/use-user-picker-pool.hook", () => ({
    useUserPickerPool: () => ({
        pool: [],
        poolLoading: false,
        serverPaging: {
            onSearchChange: () => {},
            onLoadMore: () => {},
            hasMore: false,
            isFetchingMore: false,
            isError: false,
            onRetry: () => {},
        },
    }),
}));

const LAYER_ID = "l1";
const MEMBERS_URL = `${API_BASE}/layers/:id/members`;

// Captured PATCH bodies — the role-bucketed contract updateLayerMembership sends.
let patchRequests: { layerId: string; body: unknown }[] = [];

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** The role-bucketed PATCH body updateLayerMembership builds for a userId→role map. */
function bucket(roles: Record<string, LayerRole>) {
    const body = {
        members: [] as string[],
        editors: [] as string[],
        admins: [] as string[],
        owners: [] as string[],
    };
    for (const [id, role] of Object.entries(roles)) {
        if (role === "MEMBER") body.members.push(id);
        else if (role === "EDITOR") body.editors.push(id);
        else if (role === "ADMIN") body.admins.push(id);
        else if (role === "OWNER") body.owners.push(id);
    }
    return body;
}
/** Expected captured request for a userId→role change (asserted against patchRequests). */
const patchFor = (roles: Record<string, LayerRole>) => ({ layerId: LAYER_ID, body: bucket(roles) });

function member(userId: string, role: LayerRole): LayerUser {
    return {
        layerId: LAYER_ID,
        userId,
        role,
        joinedAt: "2026-01-01T00:00:00Z",
        username: userId,
        firstName: "",
        lastName: "",
        email: `${userId}@example.com`,
    };
}

function renderSection(opts: { canAssignOwner?: boolean; members?: LayerUser[] } = {}) {
    const members = opts.members ?? [member("u1", "MEMBER"), member("u2", "MEMBER")];
    // Seed the cache so the first render paints immediately; the harness client's
    // staleTime: Infinity keeps these fresh (no on-mount refetch). The PATCH and the
    // post-mutation members refetch go through the MSW handlers below.
    render(
        <LayerMembersSection layerId={LAYER_ID} canAssignOwner={opts.canAssignOwner ?? false} />,
        {
            queryData: {
                [JSON.stringify(layerQueries.members(LAYER_ID).queryKey)]: members,
            },
        },
    );
}

/** Select the given member rows, then open the bulk change-role dialog. */
async function openChangeRoleDialog(user: ReturnType<typeof userEvent.setup>, rowIds: string[]) {
    for (const id of rowIds) {
        await user.click(screen.getByRole("button", { name: `row ${id}` }));
    }
    await user.click(screen.getByRole("button", { name: i18n.t("adminDialogs.changeRole") }));
    return screen.findByLabelText("role-select");
}

const applyButton = () =>
    screen.getByRole("button", { name: i18n.t("common.apply") }) as HTMLButtonElement;

const changeRoleButton = () =>
    screen.getByRole("button", { name: i18n.t("adminDialogs.changeRole") }) as HTMLButtonElement;

const revokeButton = () =>
    screen.getByRole("button", { name: i18n.t("adminDialogs.revokeAccess") }) as HTMLButtonElement;

describe("LayerMembersSection — change role", () => {
    beforeEach(() => {
        patchRequests = [];
        server.use(
            // Reads are seeded into the cache for the first render; these satisfy the
            // no-network guard for the post-mutation refetch (onSettled invalidates members).
            http.get(MEMBERS_URL, () => HttpResponse.json([])),
            http.get(`${API_BASE}/users`, () => HttpResponse.json([])),
            // Capture the role-bucketed PATCH body so tests assert the wire contract.
            http.patch(MEMBERS_URL, async ({ request, params }) => {
                patchRequests.push({ layerId: String(params.id), body: await request.json() });
                return new HttpResponse(null, { status: 204 });
            }),
        );
        showToast.mockReset();
    });

    it("opens the simple dialog (no member checkboxes) for a single selection", async () => {
        const user = userEvent.setup();
        renderSection();

        // Select just one member, then click Change role.
        await user.click(screen.getByRole("button", { name: "row u1" }));
        await user.click(screen.getByRole("button", { name: i18n.t("adminDialogs.changeRole") }));

        await screen.findByLabelText("role-select");
        // The bulk panel splits members into Selected / Excluded lists; the simple dialog doesn't.
        expect(screen.queryByText(/Selected users/)).toBeNull();
    });

    it("shows the owner-downgrade caution in the inline dialog for a single owner", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "OWNER"), member("u3", "MEMBER")],
        });

        // Open the inline dialog for one owner (two exist, so it isn't locked) and
        // pick a non-owner role — the same caution as the bulk path should appear.
        await user.click(screen.getByRole("button", { name: "row u1" }));
        await user.click(screen.getByRole("button", { name: i18n.t("adminDialogs.changeRole") }));
        const select = await screen.findByLabelText("role-select");
        await user.selectOptions(select, "MEMBER");

        expect(
            screen.getByText(i18n.t("layers.ownerDowngradeWarning", { count: 1, role: "Member" })),
        ).toBeTruthy();
        // A second owner remains, so Apply stays enabled.
        expect(applyButton().disabled).toBe(false);
    });

    it("PATCHes the selected members to one role, bucketed by role", async () => {
        const user = userEvent.setup();
        renderSection();

        const select = await openChangeRoleDialog(user, ["u1", "u2"]);
        expect(applyButton().disabled).toBe(true); // no role chosen yet
        await user.selectOptions(select, "EDITOR");
        await user.click(applyButton());

        await waitFor(() => {
            expect(patchRequests).toContainEqual(patchFor({ u1: "EDITOR", u2: "EDITOR" }));
        });
    });

    it("labels the selected-users count in the bulk change-role panel", async () => {
        const user = userEvent.setup();
        renderSection();

        await openChangeRoleDialog(user, ["u1", "u2"]);

        // Both selected members start in the "Selected users" list.
        await screen.findByText("Selected users (2)");
    });

    it("closes the bulk panel via the header X button", async () => {
        const user = userEvent.setup();
        renderSection();

        await openChangeRoleDialog(user, ["u1", "u2"]);
        expect(screen.getByText("Selected users (2)")).toBeTruthy();

        await user.click(screen.getByRole("button", { name: i18n.t("common.close") }));

        // Back in the list: the panel is gone and the member rows are shown again.
        expect(screen.queryByText("Selected users (2)")).toBeNull();
        expect(screen.getByRole("button", { name: "row u1" })).toBeTruthy();
    });

    it("reflects the new role immediately, without waiting for a refetch", async () => {
        const user = userEvent.setup();
        // Leave the reconciling refetch pending so only the optimistic cache update drives the UI —
        // this reproduces the "doesn't update until I hit refresh" report and guards the fix.
        server.use(http.get(MEMBERS_URL, () => new Promise<Response>(() => {})));
        renderSection();
        expect(screen.getByTestId("role-u1").textContent).toBe("MEMBER");

        const select = await openChangeRoleDialog(user, ["u1"]);
        await user.selectOptions(select, "ADMIN");
        await user.click(applyButton());

        await waitFor(() => expect(screen.getByTestId("role-u1").textContent).toBe("ADMIN"));
    });

    it("rolls back the optimistic role when the change fails", async () => {
        const user = userEvent.setup();
        // Keep the reconciling refetch pending so the cache — optimistic, then rolled back —
        // is the only thing driving the list's role.
        server.use(http.get(MEMBERS_URL, () => new Promise<Response>(() => {})));
        // Delay the failure so the optimistic value is observable before it lands.
        server.use(
            http.patch(MEMBERS_URL, async () => {
                await wait(80);
                return new HttpResponse(null, { status: 500 });
            }),
        );
        renderSection();
        expect(screen.getByTestId("role-u1").textContent).toBe("MEMBER");

        const select = await openChangeRoleDialog(user, ["u1"]);
        await user.selectOptions(select, "ADMIN");
        await user.click(applyButton());

        // The optimistic update flips the row before the server answers.
        await waitFor(() => expect(screen.getByTestId("role-u1").textContent).toBe("ADMIN"));

        // The delayed 500 lands → the row reverts to its original role and an error toast fires.
        await waitFor(() => expect(screen.getByTestId("role-u1").textContent).toBe("MEMBER"));
        expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ type: "error" }));
    });

    it("hides the Owner role unless the viewer may assign owners", async () => {
        const user = userEvent.setup();

        renderSection({ canAssignOwner: false });
        const restricted = await openChangeRoleDialog(user, ["u1"]);
        expect(
            within(restricted).queryByRole("option", { name: i18n.t("roles.owner") }),
        ).toBeNull();
        expect(
            within(restricted).getByRole("option", { name: i18n.t("roles.admin") }),
        ).toBeTruthy();
    });

    it("offers the Owner role when the viewer may assign owners", async () => {
        const user = userEvent.setup();

        renderSection({ canAssignOwner: true });
        const select = await openChangeRoleDialog(user, ["u1"]);
        expect(within(select).getByRole("option", { name: i18n.t("roles.owner") })).toBeTruthy();
    });

    it("surfaces an error toast when the role change fails", async () => {
        const user = userEvent.setup();
        server.use(http.patch(MEMBERS_URL, () => new HttpResponse(null, { status: 500 })));
        renderSection();

        const select = await openChangeRoleDialog(user, ["u1"]);
        await user.selectOptions(select, "ADMIN");
        await user.click(applyButton());

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ type: "error" })),
        );
    });

    it("warns, flags the owners, and blocks Apply until an owner is kept", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "MEMBER")],
        });

        const select = await openChangeRoleDialog(user, ["u1", "u2"]);
        await user.selectOptions(select, "MEMBER");

        // The downgrade warning shows, the only owner is flagged, and Apply is blocked.
        await screen.findByText(
            i18n.t("layers.ownerDowngradeWarning", { count: 1, role: "Member" }),
        );
        expect(screen.getByLabelText(i18n.t("layers.ownerRequiredWarning"))).toBeTruthy();
        expect(applyButton().disabled).toBe(true);
        expect(patchRequests).toHaveLength(0);

        // Move the owner to the Excluded list — the flag and warning clear.
        await user.click(
            screen.getByRole("button", {
                name: i18n.t("adminDialogs.excludeUser", { name: "u1" }),
            }),
        );
        expect(screen.queryByLabelText(i18n.t("layers.ownerRequiredWarning"))).toBeNull();
        expect(applyButton().disabled).toBe(false);
        await user.click(applyButton());

        await waitFor(() => expect(patchRequests).toContainEqual(patchFor({ u2: "MEMBER" })));
    });

    it("clears the owner flag once one of several owners is kept", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "OWNER"), member("u3", "MEMBER")],
        });

        const select = await openChangeRoleDialog(user, ["u1", "u2", "u3"]);
        await user.selectOptions(select, "MEMBER");

        // Both owners checked → both flagged, Apply blocked.
        await screen.findByText(
            i18n.t("layers.ownerDowngradeWarning", { count: 2, role: "Member" }),
        );
        expect(screen.getAllByLabelText(i18n.t("layers.ownerRequiredWarning"))).toHaveLength(2);
        expect(applyButton().disabled).toBe(true);

        // Exclude u1 from the update: flags clear, warning stays (u2 is still being downgraded).
        await user.click(
            screen.getByRole("button", {
                name: i18n.t("adminDialogs.excludeUser", { name: "u1" }),
            }),
        );
        expect(screen.queryByLabelText(i18n.t("layers.ownerRequiredWarning"))).toBeNull();
        await screen.findByText(
            i18n.t("layers.ownerDowngradeWarning", { count: 1, role: "Member" }),
        );
        expect(applyButton().disabled).toBe(false);
        await user.click(applyButton());

        await waitFor(() =>
            expect(patchRequests).toContainEqual(patchFor({ u2: "MEMBER", u3: "MEMBER" })),
        );
    });

    it("warns but allows demoting one of several owners", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "OWNER"), member("u3", "MEMBER")],
        });

        // One owner selected, the other (u2) not → an owner remains → warning only, no flag.
        const select = await openChangeRoleDialog(user, ["u1", "u3"]);
        await user.selectOptions(select, "MEMBER");

        await screen.findByText(
            i18n.t("layers.ownerDowngradeWarning", { count: 1, role: "Member" }),
        );
        expect(screen.queryByLabelText(i18n.t("layers.ownerRequiredWarning"))).toBeNull();
        expect(applyButton().disabled).toBe(false);
        await user.click(applyButton());

        await waitFor(() =>
            expect(patchRequests).toContainEqual(patchFor({ u1: "MEMBER", u3: "MEMBER" })),
        );
    });

    it("blocks a non-owner admin from changing an owner's role in the bulk flow", async () => {
        const user = userEvent.setup();
        // canAssignOwner=false → a layer admin who is not an owner. The server rejects
        // an admin's owner change (403), so the UI must block it up front. Two owners
        // exist, so this is the admin gate, not the last-owner guard.
        renderSection({
            canAssignOwner: false,
            members: [member("u1", "OWNER"), member("u2", "OWNER"), member("u3", "MEMBER")],
        });

        const select = await openChangeRoleDialog(user, ["u1", "u3"]);
        await user.selectOptions(select, "MEMBER");

        // The owner is flagged and Apply is blocked — only an owner can change owner roles.
        expect(screen.getByLabelText(i18n.t("layers.ownerChangeRequiresOwner"))).toBeTruthy();
        expect(applyButton().disabled).toBe(true);
        expect(patchRequests).toHaveLength(0);

        // Excluding the owner lets the member change through, owner untouched.
        await user.click(
            screen.getByRole("button", {
                name: i18n.t("adminDialogs.excludeUser", { name: "u1" }),
            }),
        );
        expect(screen.queryByLabelText(i18n.t("layers.ownerChangeRequiresOwner"))).toBeNull();
        expect(applyButton().disabled).toBe(false);
        await user.click(applyButton());

        await waitFor(() => expect(patchRequests).toContainEqual(patchFor({ u3: "MEMBER" })));
    });

    it("allows a bulk change to Owner even when the sole owner is selected", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "MEMBER")],
        });

        const select = await openChangeRoleDialog(user, ["u1", "u2"]);
        await user.selectOptions(select, "OWNER");
        await user.click(applyButton());

        await waitFor(() =>
            expect(patchRequests).toContainEqual(patchFor({ u1: "OWNER", u2: "OWNER" })),
        );
    });

    it("allows a bulk change that does not touch the sole owner", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "MEMBER"), member("u3", "MEMBER")],
        });

        const select = await openChangeRoleDialog(user, ["u2", "u3"]);
        await user.selectOptions(select, "ADMIN");
        await user.click(applyButton());

        await waitFor(() =>
            expect(patchRequests).toContainEqual(patchFor({ u2: "ADMIN", u3: "ADMIN" })),
        );
    });

    it("disables Change role when only the sole owner is selected", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "MEMBER")],
        });

        // Only the sole owner selected — nothing changeable.
        await user.click(screen.getByRole("button", { name: "row u1" }));
        expect(changeRoleButton().disabled).toBe(true);

        // Selecting another member makes it actionable again.
        await user.click(screen.getByRole("button", { name: "row u2" }));
        expect(changeRoleButton().disabled).toBe(false);
    });

    it("disables Change role for a non-owner admin when only owners are selected", async () => {
        const user = userEvent.setup();
        // Two owners → not the sole-owner guard; this is the admin-can't-touch-owners gate.
        // Also guards the single-select path, which would otherwise open the inline dialog.
        renderSection({
            canAssignOwner: false,
            members: [member("u1", "OWNER"), member("u2", "OWNER"), member("u3", "MEMBER")],
        });

        await user.click(screen.getByRole("button", { name: "row u1" }));
        expect(changeRoleButton().disabled).toBe(true);

        // Adding a changeable member re-enables the action.
        await user.click(screen.getByRole("button", { name: "row u3" }));
        expect(changeRoleButton().disabled).toBe(false);
    });

    it("keeps Change role enabled for a lone owner when the layer has multiple owners", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "OWNER"), member("u3", "MEMBER")],
        });

        await user.click(screen.getByRole("button", { name: "row u1" }));
        expect(changeRoleButton().disabled).toBe(false);
    });

    it("disables Revoke access when only the sole owner is selected", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "MEMBER")],
        });

        // Revoking the sole owner would leave the layer ownerless — the server
        // rejects it, so the action is disabled rather than failing silently.
        await user.click(screen.getByRole("button", { name: "row u1" }));
        expect(revokeButton().disabled).toBe(true);

        // Selecting only the non-owner keeps the owner → revoke is allowed.
        await user.click(screen.getByRole("button", { name: "row u1" })); // deselect u1
        await user.click(screen.getByRole("button", { name: "row u2" }));
        expect(revokeButton().disabled).toBe(false);
    });

    it("disables Revoke access when the selection would remove every owner", async () => {
        const user = userEvent.setup();
        renderSection({
            canAssignOwner: true,
            members: [member("u1", "OWNER"), member("u2", "OWNER"), member("u3", "MEMBER")],
        });

        // One of two owners can be revoked — an owner remains.
        await user.click(screen.getByRole("button", { name: "row u1" }));
        expect(revokeButton().disabled).toBe(false);

        // Both owners selected — revoking them all would leave no owner.
        await user.click(screen.getByRole("button", { name: "row u2" }));
        expect(revokeButton().disabled).toBe(true);
    });
});

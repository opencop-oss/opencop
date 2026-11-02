// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { RuntimeFeature } from "@opencop/api";
import { QueryClient, useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
    API_BASE,
    createTestUser,
    http,
    HttpResponse,
    render,
    screen,
    server,
    userEvent,
    waitFor,
    within,
} from "~/test";
import { appMetadataQueries } from "~/features/admin/app-metadata.queries";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { buildRuntimeFeatureRows } from "../runtime-features.rows";
import { RuntimeFeatureDrawerContent } from "./RuntimeFeatureDrawerContent";

// Three narrow doubles for jsdom, in the spirit of LayerLayerGroupsSection.test.tsx: OcSelect and
// OcCombobox are Chakra v3 (zag.js) widgets that do not drive here, and DrawerSideNavLayout requires
// a `Drawer.Root` ancestor the real view supplies. None is under test — the drawer's own
// save/conflict wiring is.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    interface SideNavProps {
        sections: { key: string; content: ReactNode }[];
    }
    interface SelectProps {
        items: { label: string; value: string }[];
        value?: string;
        onValueChange: (value: string) => void;
        disabled?: boolean;
        /**
         * The real `OcSelect` names its trigger through this bag and drops residual props, so the
         * double reads the name from the same place. `AudienceModeSelect.test.tsx` covers that the
         * production control actually ends up with the name; here it only has to be findable.
         */
        triggerProps?: { "aria-label"?: string };
    }
    interface ComboboxProps {
        items: { label: string; value: string }[];
        value?: string[];
        onValueChange: (value: string[]) => void;
        disabled?: boolean;
        placeholder?: string;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        // Only the Overview section, which is what the real layout shows on open.
        DrawerSideNavLayout: (props: SideNavProps) =>
            createElement("div", null, props.sections[0]?.content),
        OcSelect: (props: SelectProps) =>
            createElement(
                "select",
                {
                    "aria-label": props.triggerProps?.["aria-label"],
                    value: props.value,
                    disabled: props.disabled,
                    onChange: (event: { target: { value: string } }) =>
                        props.onValueChange(event.target.value),
                },
                ...props.items.map((item) =>
                    createElement("option", { key: item.value, value: item.value }, item.label),
                ),
            ),
        // A checkbox per item rather than a `<select multiple>`: the real control renders each
        // selection as an independently removable tag, and a controlled multi-select cannot be
        // driven one option at a time the way that is.
        OcCombobox: (props: ComboboxProps) =>
            createElement(
                "div",
                null,
                ...props.items.map((item) => {
                    const selected = props.value?.includes(item.value) ?? false;
                    return createElement(
                        "label",
                        { key: item.value },
                        createElement("input", {
                            type: "checkbox",
                            checked: selected,
                            disabled: props.disabled,
                            onChange: () =>
                                props.onValueChange(
                                    selected
                                        ? (props.value ?? []).filter((id) => id !== item.value)
                                        : [...(props.value ?? []), item.value],
                                ),
                        }),
                        item.label,
                    );
                }),
            ),
    };
});

// The toast surface is asserted through this spy rather than by mounting the notification stack,
// matching LayerMembersSection.test.tsx.
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

const FLAG = "themes-page";
const NOW = "2026-08-11T00:00:00Z";

/** Server-side record, mutated between requests to stand in for a concurrent admin. */
let stored: RuntimeFeature;
let getCount = 0;
let patchBodies: {
    audience: { mode: string; userGroupIds?: string[] };
    expectedRevision?: number;
}[] = [];

/**
 * Owns the metadata query and derives the row exactly as `AdminRuntimeFeaturesView` does, so a
 * refetch reaches the drawer through the same path it does in the app. Rendering the drawer with a
 * static `row` prop would make the reread untestable — that data flow *is* the fix.
 */
function Host() {
    const { data } = useQuery(appMetadataQueries.detail());
    const row = buildRuntimeFeatureRows(data?.admin?.runtimeFeatures).find(
        (candidate) => candidate.name === stored.name,
    );
    return row ? <RuntimeFeatureDrawerContent row={row} /> : null;
}

/** A client that really fetches; the harness default pins `staleTime: Infinity` for seeded caches. */
function fetchingClient(): QueryClient {
    return new QueryClient({
        defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
    });
}

function renderDrawer() {
    return render(<Host />, {
        user: createTestUser({ authorities: ["ROLE_admin"] }),
        queryClient: fetchingClient(),
    });
}

beforeEach(() => {
    stored = {
        name: FLAG,
        consumerClass: "FRONTEND_ONLY",
        audience: { mode: "NONE" },
        revision: 3,
        updatedAt: "2026-08-11T00:00:00Z",
    };
    getCount = 0;
    patchBodies = [];
    showToast.mockClear();

    server.use(
        http.get(`${API_BASE}/app-metadata`, () => {
            getCount += 1;
            return HttpResponse.json({
                features: [
                    {
                        name: stored.name,
                        enabled: stored.audience.mode !== "NONE",
                    },
                ],
                admin: { runtimeFeatures: [stored] },
            });
        }),
        http.get(`${API_BASE}/admin/user-groups`, () =>
            HttpResponse.json([
                { id: "g1", name: "Operations", description: null, createdAt: NOW },
                { id: "g2", name: "Analysts", description: null, createdAt: NOW },
            ]),
        ),
    );
});

describe("RuntimeFeatureDrawerContent audience conflicts", () => {
    it("rereads the record, keeps the attempted audience, and resubmits against the new revision", async () => {
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, async ({ request }) => {
                patchBodies.push((await request.json()) as (typeof patchBodies)[number]);
                if (patchBodies.length === 1) {
                    // Another admin got there first and reverted it, bumping the revision the
                    // drawer is holding.
                    stored = { ...stored, revision: 4, audience: { mode: "NONE" } };
                    return HttpResponse.json(
                        { code: "RUNTIME_FEATURE_REVISION_CONFLICT", message: "Revision conflict" },
                        { status: 409 },
                    );
                }
                stored = { ...stored, revision: 5, audience: { mode: "ALL" } };
                return HttpResponse.json(stored);
            }),
        );

        renderDrawer();
        const select = await screen.findByLabelText("Audience");

        await userEvent.selectOptions(select, "ALL");

        // The reread happened and the draft survived it.
        expect(await screen.findByText("Someone else changed this feature")).toBeInTheDocument();
        await waitFor(() => expect(getCount).toBe(2));
        expect(select).toHaveValue("ALL");
        expect(
            screen.getByText(/Its audience is now Nobody, so your change to Everyone/),
        ).toBeInTheDocument();
        expect(patchBodies[0]?.expectedRevision).toBe(3);

        await userEvent.click(screen.getByRole("button", { name: "Retry" }));

        // The resubmit carried the revision the reread loaded, not the rejected one.
        await waitFor(() => expect(patchBodies).toHaveLength(2));
        expect(patchBodies[1]?.expectedRevision).toBe(4);
        expect(patchBodies[1]?.audience.mode).toBe("ALL");
        await waitFor(() =>
            expect(screen.queryByText("Someone else changed this feature")).not.toBeInTheDocument(),
        );
        // One metadata read per settled attempt: initial load, conflict recovery, successful retry.
        expect(getCount).toBe(3);
    });

    /**
     * A rejection is not proof that nothing was committed — the server may have applied the write
     * and then failed to report it readably. So the catalog is reread either way; what does not
     * appear is the conflict affordance, because nothing here says the caller's revision is stale.
     */
    it("rereads canonical state after a non-conflict rejection without showing a conflict", async () => {
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, async ({ request }) => {
                patchBodies.push((await request.json()) as (typeof patchBodies)[number]);
                return HttpResponse.json(
                    { code: "RUNTIME_FEATURE_INVALID_REQUEST", message: "Invalid request" },
                    { status: 400 },
                );
            }),
        );

        renderDrawer();
        const select = await screen.findByLabelText("Audience");
        await waitFor(() => expect(getCount).toBe(1));

        await userEvent.selectOptions(select, "ALL");

        // A 400 itself is definitive, but the shared mutation policy intentionally rereads after
        // every settled outcome rather than trying to classify which proxy/transport failures may
        // have hidden a commit. The low-frequency extra read is accepted; conflict UI is not.
        await waitFor(() => expect(patchBodies).toHaveLength(1));
        await waitFor(() => expect(getCount).toBe(2));
        expect(screen.queryByText("Someone else changed this feature")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
        expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ type: "error" }));
    });

    /**
     * The dialog stays mounted through its own save so it can show the pending state. If a
     * conflict left it there, it would cover the alert that explains what changed, and confirming
     * again would resubmit the original intent — under copy computed from the audience that is no
     * longer stored — without the admin ever seeing it.
     */
    it("closes the confirmation onto the conflict rather than leaving it over the alert", async () => {
        stored = { ...stored, audience: { mode: "ALL" } };
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, async ({ request }) => {
                patchBodies.push((await request.json()) as (typeof patchBodies)[number]);
                stored = {
                    ...stored,
                    revision: 4,
                    audience: { mode: "USER_GROUPS", userGroupIds: ["g1"] },
                };
                return HttpResponse.json(
                    { code: "RUNTIME_FEATURE_REVISION_CONFLICT", message: "Revision conflict" },
                    { status: 409 },
                );
            }),
        );

        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "NONE");
        await userEvent.click(await screen.findByRole("button", { name: "Turn off" }));

        expect(await screen.findByText("Someone else changed this feature")).toBeInTheDocument();
        expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
        expect(screen.queryByText("Turn off themes-page?")).not.toBeInTheDocument();
        expect(patchBodies).toHaveLength(1);
    });

    /**
     * Undo pins the revision its own write returned, so a rejected Undo can never succeed by being
     * clicked again — it would resend the revision the server just refused. Retry is the
     * affordance that carries the same audience against what the reread loaded.
     */
    it("retires a rejected Undo instead of leaving a button that can never succeed", async () => {
        stored = { ...stored, audience: { mode: "USER_GROUPS", userGroupIds: ["g1", "g2"] } };
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, async ({ request }) => {
                const body = (await request.json()) as (typeof patchBodies)[number];
                patchBodies.push(body);
                if (patchBodies.length === 1) {
                    stored = { ...stored, audience: { mode: "ALL" } };
                    return HttpResponse.json({ ...stored, revision: 4 });
                }
                if (patchBodies.length === 2) {
                    // Another admin moved it again while the Undo was in flight.
                    stored = { ...stored, revision: 6, audience: { mode: "NONE" } };
                    return HttpResponse.json(
                        { code: "RUNTIME_FEATURE_REVISION_CONFLICT", message: "Revision conflict" },
                        { status: 409 },
                    );
                }
                stored = {
                    ...stored,
                    revision: 7,
                    audience: body.audience as RuntimeFeature["audience"],
                };
                return HttpResponse.json(stored);
            }),
        );

        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "ALL");

        await userEvent.click(await screen.findByRole("button", { name: "Undo" }));

        expect(await screen.findByText("Someone else changed this feature")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Undo" })).not.toBeInTheDocument();
        expect(patchBodies[1]?.expectedRevision).toBe(4);

        // Retry finishes what Undo started, against the revision the reread loaded.
        await userEvent.click(screen.getByRole("button", { name: "Retry" }));

        await waitFor(() => expect(patchBodies).toHaveLength(3));
        expect(patchBodies[2]?.expectedRevision).toBe(6);
        expect(patchBodies[2]?.audience).toEqual({
            mode: "USER_GROUPS",
            userGroupIds: ["g1", "g2"],
        });
    });

    /**
     * Nobody and Everyone commit the moment the select fires, so a change event the user did not
     * cause is a write to a live flag. A remount re-emitting the control's current value is exactly
     * that, and the audience it would land on — Nobody — is the destructive one.
     */
    it("does not write when the selected audience is the one already stored", async () => {
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, async ({ request }) => {
                patchBodies.push((await request.json()) as (typeof patchBodies)[number]);
                return HttpResponse.json(stored);
            }),
        );

        const { unmount } = renderDrawer();
        const select = await screen.findByLabelText("Audience");
        await waitFor(() => expect(getCount).toBe(1));
        expect(select).toHaveValue("NONE");

        // Re-selecting the stored audience is not an edit.
        await userEvent.selectOptions(select, "NONE");
        expect(patchBodies).toHaveLength(0);

        // Nor is remounting and having the control re-emit that same value.
        unmount();
        renderDrawer();
        const remounted = await screen.findByLabelText("Audience");
        await userEvent.selectOptions(remounted, "NONE");

        expect(patchBodies).toHaveLength(0);
        expect(stored.revision).toBe(3);
    });
});

describe("RuntimeFeatureDrawerContent group audience", () => {
    /** Selecting the mode reveals the picker; the audience is only written once a group is chosen. */
    async function pickGroupsMode() {
        renderDrawer();
        const select = await screen.findByLabelText("Audience");
        await userEvent.selectOptions(select, "USER_GROUPS");
        // The directory is its own request, so the options arrive after the picker does.
        await screen.findByRole("checkbox", { name: "Operations" });
    }

    beforeEach(() => {
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, async ({ request }) => {
                patchBodies.push((await request.json()) as (typeof patchBodies)[number]);
                stored = {
                    ...stored,
                    revision: stored.revision + 1,
                    audience: { mode: "USER_GROUPS", userGroupIds: ["g1"] },
                };
                return HttpResponse.json(stored);
            }),
        );
    });

    it("blocks the save until a group is chosen, then patches the chosen ids", async () => {
        await pickGroupsMode();

        // Switching the mode is not itself a write — the server rejects USER_GROUPS with no
        // targets, so the submit is blocked here rather than surfacing an error about it.
        expect(patchBodies).toHaveLength(0);
        expect(screen.getByText("Select at least one group.")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();

        await userEvent.click(screen.getByRole("checkbox", { name: "Operations" }));

        const save = screen.getByRole("button", { name: "Save" });
        expect(save).toBeEnabled();
        await userEvent.click(save);

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(patchBodies[0]?.audience).toEqual({ mode: "USER_GROUPS", userGroupIds: ["g1"] });
        expect(patchBodies[0]?.expectedRevision).toBe(3);
    });

    it("carries every selected group through the save", async () => {
        await pickGroupsMode();

        await userEvent.click(screen.getByRole("checkbox", { name: "Operations" }));
        await userEvent.click(screen.getByRole("checkbox", { name: "Analysts" }));
        await userEvent.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(patchBodies[0]?.audience).toEqual({
            mode: "USER_GROUPS",
            userGroupIds: ["g1", "g2"],
        });
    });

    it("re-enables the save only while the selection differs from nothing chosen", async () => {
        await pickGroupsMode();

        await userEvent.click(screen.getByRole("checkbox", { name: "Operations" }));
        expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();

        // Unchecking the only selection puts it back in the state the server would reject.
        await userEvent.click(screen.getByRole("checkbox", { name: "Operations" }));
        expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
        expect(patchBodies).toHaveLength(0);
    });

    /**
     * The stored id belongs to a group the directory no longer returns. Dropping it would let this
     * unrelated edit quietly revoke access for whoever that group still covers server-side, so it
     * has to survive as a selectable, removable target.
     */
    it("keeps a stored group the directory no longer resolves", async () => {
        stored = {
            ...stored,
            audience: { mode: "USER_GROUPS", userGroupIds: ["gone"] },
        };

        renderDrawer();

        // Offered back as its own option, labelled so the admin can tell what it is, and already
        // checked because it is still a stored target.
        const unresolved = await screen.findByRole("checkbox", {
            name: "Unknown user group (gone)",
        });
        expect(unresolved).toBeChecked();

        // An unrelated edit — adding a group — must not quietly drop it.
        await userEvent.click(await screen.findByRole("checkbox", { name: "Operations" }));
        await userEvent.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(patchBodies[0]?.audience).toEqual({
            mode: "USER_GROUPS",
            userGroupIds: ["gone", "g1"],
        });
    });

    it("restores the stored audience when the edit is cancelled", async () => {
        await pickGroupsMode();
        await userEvent.click(screen.getByRole("checkbox", { name: "Operations" }));

        await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

        expect(patchBodies).toHaveLength(0);
        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
        expect(await screen.findByLabelText("Audience")).toHaveValue("NONE");
    });
});

describe("RuntimeFeatureDrawerContent audience guardrails", () => {
    /** Applies whatever audience is patched, so the drawer sees the change it asked for. */
    function acceptPatches() {
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/:name`, async ({ request }) => {
                const body = (await request.json()) as (typeof patchBodies)[number];
                patchBodies.push(body);
                stored = { ...stored, audience: body.audience as RuntimeFeature["audience"] };
                // The response is the authority on the new revision. The catalog read deliberately
                // lags it here — `stored.revision` is left alone — so a revert that reused the
                // cached revision would be visible as the wrong `expectedRevision`.
                return HttpResponse.json({ ...stored, revision: stored.revision + 1 });
            }),
        );
    }

    it("asks before turning a live feature off, and writes nothing when the admin declines", async () => {
        stored = { ...stored, audience: { mode: "ALL" } };
        acceptPatches();

        renderDrawer();
        const select = await screen.findByLabelText("Audience");
        expect(select).toHaveValue("ALL");

        await userEvent.selectOptions(select, "NONE");

        // The consequence, not the mechanism.
        expect(await screen.findByText("Turn off themes-page?")).toBeInTheDocument();
        expect(
            screen.getByText("Everyone loses access to this feature right away."),
        ).toBeInTheDocument();

        await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

        // Nothing was written, and the control is back on the audience that is actually stored —
        // leaving it on Nobody would show an audience the feature does not have.
        expect(patchBodies).toHaveLength(0);
        await waitFor(() => expect(select).toHaveValue("ALL"));
    });

    it("writes the change once the admin confirms it", async () => {
        stored = { ...stored, audience: { mode: "ALL" } };
        acceptPatches();

        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "NONE");
        await userEvent.click(await screen.findByRole("button", { name: "Turn off" }));

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(patchBodies[0]?.audience.mode).toBe("NONE");
        expect(patchBodies[0]?.expectedRevision).toBe(3);
        await waitFor(() =>
            expect(screen.queryByText("Turn off themes-page?")).not.toBeInTheDocument(),
        );
    });

    /** Enabling a feature that reached nobody is the common action and stays one click. */
    it("does not ask when the change only widens access", async () => {
        acceptPatches();

        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "ALL");

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    });

    /**
     * `rtsp` ships as `ALL`, so scoping it to a group list is the realistic way a live feature
     * loses reach without ever passing through Nobody. It still reaches someone afterwards, which
     * is why it is not "turning off" — but everyone outside the chosen groups loses access.
     */
    it("asks before narrowing a feature from everyone to a group list", async () => {
        stored = { ...stored, audience: { mode: "ALL" } };
        acceptPatches();

        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "USER_GROUPS");
        await userEvent.click(await screen.findByRole("checkbox", { name: "Operations" }));
        await userEvent.click(screen.getByRole("button", { name: "Save" }));

        expect(await screen.findByText("Reduce who themes-page reaches?")).toBeInTheDocument();
        expect(patchBodies).toHaveLength(0);

        await userEvent.click(screen.getByRole("button", { name: "Reduce access" }));

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(patchBodies[0]?.audience).toEqual({ mode: "USER_GROUPS", userGroupIds: ["g1"] });
    });

    /**
     * The audience being reverted to is a group list, which an audience write replaces rather than
     * merges — and the history endpoint is global and capped, so it may not be recoverable there.
     * The revert has to come from what the client held before the write.
     */
    it("reverts to the exact audience it replaced, carrying the revision the write returned", async () => {
        stored = { ...stored, audience: { mode: "USER_GROUPS", userGroupIds: ["g1", "g2"] } };
        acceptPatches();

        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "ALL");

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(
            await screen.findByText("It reached Selected user groups before this change."),
        ).toBeInTheDocument();

        await userEvent.click(screen.getByRole("button", { name: "Undo" }));

        await waitFor(() => expect(patchBodies).toHaveLength(2));
        expect(patchBodies[1]?.audience).toEqual({
            mode: "USER_GROUPS",
            userGroupIds: ["g1", "g2"],
        });
        // 4, from the first write's response — not the 3 the catalog read still reports.
        expect(patchBodies[1]?.expectedRevision).toBe(4);
        // Reverting a revert would be a redo wearing an Undo label.
        await waitFor(() =>
            expect(screen.queryByRole("button", { name: "Undo" })).not.toBeInTheDocument(),
        );
    });

    /**
     * Declining a group narrowing is a request to look again, not to throw the work away — the
     * picker has its own Cancel for that. Clearing the draft here would make the safety pause cost
     * every selection the admin had made.
     */
    it("keeps the chosen groups when the narrowing confirmation is declined", async () => {
        stored = { ...stored, audience: { mode: "ALL" } };
        acceptPatches();

        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "USER_GROUPS");
        await userEvent.click(await screen.findByRole("checkbox", { name: "Operations" }));
        await userEvent.click(screen.getByRole("checkbox", { name: "Analysts" }));
        await userEvent.click(screen.getByRole("button", { name: "Save" }));

        const dialog = await screen.findByRole("alertdialog");
        await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

        expect(patchBodies).toHaveLength(0);
        expect(screen.getByRole("checkbox", { name: "Operations" })).toBeChecked();
        expect(screen.getByRole("checkbox", { name: "Analysts" })).toBeChecked();

        // The picker's own Cancel is still how the draft is abandoned.
        await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
        expect(patchBodies).toHaveLength(0);
        await waitFor(() => expect(screen.getByLabelText("Audience")).toHaveValue("ALL"));
    });
});

describe("RuntimeFeatureDrawerContent backend-enforced features", () => {
    /**
     * `watchbox` is the shipped backend-enforced feature and is deliberately absent from the
     * frontend manifest — the manifest is for frontend gates, so a row for it arrives from the
     * catalog alone.
     */
    beforeEach(() => {
        stored = {
            name: "watchbox",
            consumerClass: "BACKEND_ENFORCED",
            audience: { mode: "NONE" },
            revision: 7,
            updatedAt: NOW,
        };
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/:name`, async ({ request }) => {
                const body = (await request.json()) as (typeof patchBodies)[number];
                patchBodies.push(body);
                stored = {
                    ...stored,
                    revision: stored.revision + 1,
                    audience: body.audience as RuntimeFeature["audience"],
                };
                return HttpResponse.json(stored);
            }),
        );
    });

    it("offers the audience editor rather than read-only text", async () => {
        renderDrawer();

        expect(await screen.findByLabelText("Audience")).toBeInTheDocument();
        expect(
            screen.queryByText(/Its audience is managed outside this page/),
        ).not.toBeInTheDocument();
    });

    it("confirms, then patches the chosen audience against the record's revision", async () => {
        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "ALL");

        // Backend-enforced changes always ask, and the dialog names what Watchbox actually does
        // rather than talking about audiences.
        expect(await screen.findByText("Change who watchbox reaches?")).toBeInTheDocument();
        await userEvent.click(screen.getByRole("button", { name: "Change audience" }));

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(patchBodies[0]?.audience).toEqual({ mode: "ALL" });
        expect(patchBodies[0]?.expectedRevision).toBe(7);
    });

    it("names the Watchbox consequence when the feature is being turned off", async () => {
        stored = { ...stored, audience: { mode: "ALL" } };

        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "NONE");

        expect(
            await screen.findByText(/Watchbox detection stops for everyone\./),
        ).toBeInTheDocument();
    });

    it("patches the chosen group ids", async () => {
        renderDrawer();
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "USER_GROUPS");
        await userEvent.click(await screen.findByRole("checkbox", { name: "Operations" }));
        await userEvent.click(screen.getByRole("button", { name: "Save" }));

        await userEvent.click(await screen.findByRole("button", { name: "Change audience" }));

        await waitFor(() => expect(patchBodies).toHaveLength(1));
        expect(patchBodies[0]?.audience).toEqual({ mode: "USER_GROUPS", userGroupIds: ["g1"] });
    });

    /**
     * Consumer class is bound in lockstep to the existence of Java enforcement code. A row that
     * claimed the wrong class would make every gated call fail at HTTP 500, so the unlock covers
     * the audience only and this pins that.
     */
    it.each([
        ["BACKEND_ENFORCED", "Backend"],
        ["FRONTEND_ONLY", "Frontend"],
    ] as const)("renders consumer class %s as plain text with no control", async (cls, label) => {
        stored = { ...stored, consumerClass: cls };

        renderDrawer();

        expect(await screen.findByText(label)).toBeInTheDocument();
        // The audience picker is the only control in the overview, so nothing offers the class.
        expect(screen.getAllByRole("combobox")).toEqual([screen.getByLabelText("Audience")]);
    });
});

describe("RuntimeFeatureDrawerContent drawer lifecycle", () => {
    /**
     * Success, error, and conflict all land in mutate-time callbacks this component owns, so an
     * admin who dismisses the drawer mid-write loses the result — including a conflict's reread.
     * The shared drawer contract blocks the close trigger, Escape, and outside interaction while
     * this is reported.
     */
    it("reports the in-flight write so the drawer refuses to close", async () => {
        const setBusy = vi.fn();
        let settle: () => void = () => {};
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, async () => {
                await new Promise<void>((resolve) => {
                    settle = resolve;
                });
                stored = { ...stored, revision: 4, audience: { mode: "ALL" } };
                return HttpResponse.json(stored);
            }),
        );

        render(
            <DrawerContextProvider onClose={vi.fn()} setBusy={setBusy}>
                <Host />
            </DrawerContextProvider>,
            {
                user: createTestUser({ authorities: ["ROLE_admin"] }),
                queryClient: fetchingClient(),
            },
        );
        await userEvent.selectOptions(await screen.findByLabelText("Audience"), "ALL");

        await waitFor(() => expect(setBusy).toHaveBeenCalledWith(true));

        settle();

        await waitFor(() => expect(setBusy).toHaveBeenLastCalledWith(false));
    });
});

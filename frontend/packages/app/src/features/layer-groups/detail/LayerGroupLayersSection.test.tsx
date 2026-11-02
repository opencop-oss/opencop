// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AccessScope, MyLayerInfo } from "@opencop/api";
import { i18n } from "~/i18n/config";
import { layerQueries } from "~/features/layers/layers.queries";
import { LayerGroupLayersSection } from "./LayerGroupLayersSection";
import { layerGroupQueries } from "../layer-groups.queries";
import type { LayerGroup, LayerGroupLayer } from "../layer-groups.queries";

// ResourceTable/OcSelect are Chakra v3 (zag.js) widgets that don't drive in jsdom. Replace them
// with minimal doubles that expose the section's own wiring: one button per row that toggles
// selection (a deselect removes the key, matching TanStack's RowSelectionState semantics, which
// DrawerRelationSection relies on via `Object.keys(selection)`), plus the toolbar actions.
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
            selectionActions?: { label: string; onClick: () => void }[];
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
                        { key: a.label, type: "button", onClick: a.onClick },
                        a.label,
                    ),
                ),
                ...props.data.map((row) => {
                    const id = props.getRowId(row);
                    const selected = !!props.rowSelection?.[id];
                    return createElement(
                        "button",
                        {
                            key: id,
                            type: "button",
                            onClick: () => {
                                const next = { ...(props.rowSelection ?? {}) };
                                if (selected) delete next[id];
                                else next[id] = true;
                                props.onRowSelectionChange?.(next);
                            },
                        },
                        `row ${id}`,
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

const GROUP: LayerGroup = {
    id: "g1",
    name: "Recon",
    description: null,
    accessScope: "PRIVATE",
    createdAt: "2026-01-01T00:00:00Z",
};

function layer(id: string, name: string, accessScope: AccessScope): MyLayerInfo {
    return {
        layerId: id,
        layerName: name,
        role: "OWNER",
        accessScope,
        createdAt: "2026-01-01T00:00:00Z",
        defaultVisible: true,
    };
}

function assigned(layerId: string): LayerGroupLayer {
    return {
        layerGroupId: GROUP.id,
        layerGroupName: GROUP.name,
        layerId,
        layerName: layerId,
        role: "MEMBER",
        assignedAt: "2026-01-01T00:00:00Z",
    };
}

// GROUP is PRIVATE, which accepts a layer of any scope — so A, C and D are all assignable and only
// the already-assigned B is filtered out.
const MY_LAYERS = [
    layer("la", "Layer A", "PRIVATE"),
    layer("lb", "Layer B", "PRIVATE"),
    layer("lc", "Layer C", "GLOBAL"),
    layer("ld", "Layer D", "PRIVATE"),
];
const ASSIGNED = [assigned("lb")];

const MY_LAYERS_URL = `${API_BASE}/me/layers`;
const GROUP_LAYERS_URL = `${API_BASE}/layer-groups/${GROUP.id}/layers`;

function renderSection() {
    // Seed the cache for a synchronous first paint. The shared test client uses
    // `staleTime: Infinity`, so the seed stays fresh and no refetch fires on mount;
    // the MSW handlers in beforeEach serve only requests a later invalidation
    // or mutation actually triggers.
    render(
        <LayerGroupLayersSection
            groupId={GROUP.id}
            groupName={GROUP.name}
            accessScope={GROUP.accessScope}
            layerScope="my"
        />,
        {
            queryData: {
                [JSON.stringify(layerQueries.myList().queryKey)]: MY_LAYERS,
                [JSON.stringify(layerGroupQueries.layers(GROUP.id).queryKey)]: ASSIGNED,
            },
        },
    );
}

/** Enter the assign picker so the unassigned rows / role select / Add button are shown. */
async function openAssignPicker(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole("button", { name: i18n.t("adminDialogs.assign") }));
}

// The label grows with the selection ("Add" → "Add (2)" → "Add as Member (2)"), so match the
// leading verb rather than a fixed string.
const addButton = () =>
    screen.getByRole("button", {
        name: new RegExp(`^${i18n.t("adminDialogs.add")}`),
    }) as HTMLButtonElement;

describe("LayerGroupLayersSection", () => {
    beforeEach(() => {
        showToast.mockReset();
        // apiFetch runs for real against these; tests that care about the write
        // override the POST handler to capture the body or force a failure.
        server.use(
            http.get(MY_LAYERS_URL, () => HttpResponse.json(MY_LAYERS)),
            http.get(GROUP_LAYERS_URL, () => HttpResponse.json(ASSIGNED)),
        );
    });

    it("offers a non-admin the unassigned PRIVATE layers they can edit", async () => {
        const user = userEvent.setup();
        renderSection();
        await openAssignPicker(user);

        expect(screen.getByRole("button", { name: "row la" })).toBeTruthy();
        expect(screen.getByRole("button", { name: "row ld" })).toBeTruthy();
        // Every user resolves to MEMBER on a GLOBAL layer (the server's access-scope fallback), so
        // offering layer C would let a non-admin redistribute it through a group they own.
        expect(screen.queryByRole("button", { name: "row lc" })).toBeNull();
        // The already-assigned layer is filtered out as before.
        expect(screen.queryByRole("button", { name: "row lb" })).toBeNull();
    });

    it("withholds a PRIVATE layer the non-admin only holds MEMBER on", async () => {
        const user = userEvent.setup();
        render(
            <LayerGroupLayersSection
                groupId={GROUP.id}
                groupName={GROUP.name}
                accessScope="PRIVATE"
                layerScope="my"
            />,
            {
                queryData: {
                    [JSON.stringify(layerQueries.myList().queryKey)]: [
                        { ...layer("la", "Layer A", "PRIVATE"), role: "MEMBER" as const },
                        layer("ld", "Layer D", "PRIVATE"),
                    ],
                    [JSON.stringify(layerGroupQueries.layers(GROUP.id).queryKey)]: [],
                },
            },
        );
        await openAssignPicker(user);

        // EDITOR is the floor the server enforces; MEMBER is below it.
        expect(screen.queryByRole("button", { name: "row la" })).toBeNull();
        expect(screen.getByRole("button", { name: "row ld" })).toBeTruthy();
    });

    /**
     * The two rules have no overlap: a GLOBAL group takes only GLOBAL layers and a non-admin may
     * file only PRIVATE ones. Admins are unaffected because their pool is the admin list.
     */
    it("offers a non-admin nothing for a GLOBAL group", async () => {
        const user = userEvent.setup();
        render(
            <LayerGroupLayersSection
                groupId={GROUP.id}
                groupName={GROUP.name}
                accessScope="GLOBAL"
                layerScope="my"
            />,
            {
                queryData: {
                    [JSON.stringify(layerQueries.myList().queryKey)]: MY_LAYERS,
                    [JSON.stringify(layerGroupQueries.layers(GROUP.id).queryKey)]: [],
                },
            },
        );
        await openAssignPicker(user);

        expect(screen.queryByRole("button", { name: "row lc" })).toBeNull();
        expect(screen.queryByRole("button", { name: "row la" })).toBeNull();
        expect(screen.queryByRole("button", { name: "row ld" })).toBeNull();
    });

    /** Admins keep seeing every layer a PRIVATE group can legally take, GLOBAL included. */
    it("still offers a GLOBAL layer from the admin pool", async () => {
        const user = userEvent.setup();
        render(
            <LayerGroupLayersSection
                groupId={GROUP.id}
                groupName={GROUP.name}
                accessScope="PRIVATE"
                layerScope="admin"
            />,
            {
                queryData: {
                    [JSON.stringify(layerQueries.adminList().queryKey)]: [
                        { id: "la", name: "Layer A", accessScope: "PRIVATE" },
                        { id: "lc", name: "Layer C", accessScope: "GLOBAL" },
                    ],
                    [JSON.stringify(layerGroupQueries.layers(GROUP.id).queryKey)]: [],
                },
            },
        );
        await openAssignPicker(user);

        expect(screen.getByRole("button", { name: "row la" })).toBeTruthy();
        expect(screen.getByRole("button", { name: "row lc" })).toBeTruthy();
    });

    // The server 403s on an assignment role above the actor's own role on any layer in the batch,
    // so the picker must not offer one.

    /** Reads the role select's options, minus the leading placeholder. */
    const offeredRoles = () =>
        Array.from(screen.getByLabelText("role-select").querySelectorAll("option"))
            .map((o) => (o as HTMLOptionElement).value)
            .filter((v) => v !== "");

    function renderWithRoles(roles: Record<string, "OWNER" | "ADMIN" | "EDITOR" | "MEMBER">) {
        render(
            <LayerGroupLayersSection
                groupId={GROUP.id}
                groupName={GROUP.name}
                accessScope="PRIVATE"
                layerScope="my"
            />,
            {
                queryData: {
                    [JSON.stringify(layerQueries.myList().queryKey)]: Object.entries(roles).map(
                        ([id, role]) => ({ ...layer(id, id, "PRIVATE"), role }),
                    ),
                    [JSON.stringify(layerGroupQueries.layers(GROUP.id).queryKey)]: [],
                },
            },
        );
    }

    it("offers every role before anything is selected", async () => {
        const user = userEvent.setup();
        renderWithRoles({ la: "OWNER", ld: "EDITOR" });
        await openAssignPicker(user);

        expect(offeredRoles()).toEqual(["MEMBER", "EDITOR", "ADMIN", "OWNER"]);
    });

    it("caps the role at the caller's own role on the selected layer", async () => {
        const user = userEvent.setup();
        renderWithRoles({ la: "OWNER", ld: "EDITOR" });
        await openAssignPicker(user);

        await user.click(screen.getByRole("button", { name: "row ld" }));

        expect(offeredRoles()).toEqual(["MEMBER", "EDITOR"]);
    });

    it("caps at the weakest role across a mixed selection", async () => {
        const user = userEvent.setup();
        renderWithRoles({ la: "OWNER", ld: "EDITOR" });
        await openAssignPicker(user);

        await user.click(screen.getByRole("button", { name: "row la" }));
        expect(offeredRoles()).toEqual(["MEMBER", "EDITOR", "ADMIN", "OWNER"]);

        // Adding the EDITOR layer pulls the ceiling down for the whole batch.
        await user.click(screen.getByRole("button", { name: "row ld" }));
        expect(offeredRoles()).toEqual(["MEMBER", "EDITOR"]);
    });

    /**
     * Picking a role and then widening the selection can withdraw it. The role is derived rather
     * than stored, so the stale choice cannot survive into the request.
     */
    it("drops a chosen role once the selection withdraws it", async () => {
        const user = userEvent.setup();
        renderWithRoles({ la: "OWNER", ld: "EDITOR" });
        await openAssignPicker(user);

        await user.click(screen.getByRole("button", { name: "row la" }));
        await user.selectOptions(screen.getByLabelText("role-select"), "OWNER");
        expect((screen.getByLabelText("role-select") as HTMLSelectElement).value).toBe("OWNER");

        await user.click(screen.getByRole("button", { name: "row ld" }));

        expect((screen.getByLabelText("role-select") as HTMLSelectElement).value).toBe("");
        // Add stays blocked until a still-legal role is chosen.
        expect(addButton().disabled).toBe(true);
    });

    it("POSTs all selected layers with one shared role and returns to the list on success", async () => {
        const user = userEvent.setup();
        let postBody: unknown;
        server.use(
            http.post(GROUP_LAYERS_URL, async ({ request }) => {
                postBody = await request.json();
                return HttpResponse.json([]);
            }),
        );
        renderSection();
        await openAssignPicker(user);

        await user.click(screen.getByRole("button", { name: "row la" }));
        await user.click(screen.getByRole("button", { name: "row ld" }));
        await user.selectOptions(screen.getByLabelText("role-select"), "ADMIN");
        await user.click(addButton());

        await waitFor(() => {
            expect(postBody).toEqual({ layerIds: ["la", "ld"], role: "ADMIN" });
        });
        // Back in list mode: the Assign toolbar action is shown again.
        await screen.findByRole("button", { name: i18n.t("adminDialogs.assign") });
    });

    it("keeps Add disabled until both a layer and a role are chosen", async () => {
        const user = userEvent.setup();
        let posted = false;
        server.use(
            http.post(GROUP_LAYERS_URL, () => {
                posted = true;
                return HttpResponse.json([]);
            }),
        );
        renderSection();
        await openAssignPicker(user);

        expect(addButton().disabled).toBe(true);

        await user.click(screen.getByRole("button", { name: "row la" }));
        // A role is still required, so Add stays disabled.
        expect(addButton().disabled).toBe(true);

        await user.selectOptions(screen.getByLabelText("role-select"), "ADMIN");
        expect(addButton().disabled).toBe(false);
        expect(posted).toBe(false);
    });

    it("surfaces an error toast and stays in the assign picker when the request fails", async () => {
        const user = userEvent.setup();
        server.use(http.post(GROUP_LAYERS_URL, () => new HttpResponse(null, { status: 500 })));
        renderSection();
        await openAssignPicker(user);

        await user.click(screen.getByRole("button", { name: "row la" }));
        await user.selectOptions(screen.getByLabelText("role-select"), "ADMIN");
        await user.click(addButton());

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ type: "error" })),
        );
        // Still in the picker (did not return to list) so the user can retry.
        expect(screen.getByLabelText("role-select")).toBeTruthy();
    });
});

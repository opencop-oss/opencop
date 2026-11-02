// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { LayerLayerGroupsSection } from "./LayerLayerGroupsSection";
import { layerGroupQueries } from "~/features/layer-groups/layer-groups.queries";
import type { LayerGroup, LayerGroupLayer } from "~/features/layer-groups/layer-groups.queries";

// ResourceTable/OcSelect are Chakra v3 (zag.js) widgets that don't drive in jsdom. Same minimal
// doubles as LayerGroupLayersSection.test.tsx: one button per row that toggles selection, plus the
// toolbar actions, so the section's own wiring is what's under test.
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

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

const LAYER_ID = "layer-1";

function group(id: string, name: string, accessScope: LayerGroup["accessScope"]): LayerGroup {
    return { id, name, description: null, accessScope, createdAt: "2026-01-01T00:00:00Z" };
}

function assigned(groupId: string, groupName: string): LayerGroupLayer {
    return {
        layerGroupId: groupId,
        layerGroupName: groupName,
        layerId: LAYER_ID,
        layerName: "Ops",
        role: "MEMBER",
        assignedAt: "2026-01-01T00:00:00Z",
    };
}

// ga and gd (PRIVATE, unassigned) are assignable for a PRIVATE layer; gb is already assigned;
// gc is GLOBAL, which the server refuses for a non-GLOBAL layer.
const GROUPS = [
    group("ga", "Alpha", "PRIVATE"),
    group("gb", "Bravo", "PRIVATE"),
    group("gc", "Charlie", "GLOBAL"),
    group("gd", "Delta", "PRIVATE"),
];
const ASSIGNED = [assigned("gb", "Bravo")];

const GROUPS_URL = `${API_BASE}/admin/layer-groups`;
const FOR_LAYER_URL = `${API_BASE}/layers/${LAYER_ID}/layer-groups`;

function renderSection(props: { readOnly?: boolean } = {}) {
    render(<LayerLayerGroupsSection layerId={LAYER_ID} accessScope="PRIVATE" {...props} />, {
        queryData: {
            [JSON.stringify(layerGroupQueries.adminList().queryKey)]: GROUPS,
            [JSON.stringify(layerGroupQueries.forLayer(LAYER_ID).queryKey)]: ASSIGNED,
        },
    });
}

const assignButton = () => screen.getByRole("button", { name: i18n.t("adminDialogs.assign") });

describe("LayerLayerGroupsSection", () => {
    beforeEach(() => {
        showToast.mockReset();
        server.use(
            http.get(GROUPS_URL, () => HttpResponse.json(GROUPS)),
            http.get(FOR_LAYER_URL, () => HttpResponse.json(ASSIGNED)),
        );
    });

    it("offers only unassigned groups that can legally take this layer", async () => {
        const user = userEvent.setup();
        renderSection();
        await waitFor(() => expect(assignButton()).toBeInTheDocument());
        await user.click(assignButton());

        // Alpha and Delta only: Bravo is already assigned, Charlie is GLOBAL and would 409.
        await waitFor(() => expect(screen.getByText("row ga")).toBeInTheDocument());
        expect(screen.getByText("row gd")).toBeInTheDocument();
        expect(screen.queryByText("row gb")).not.toBeInTheDocument();
        expect(screen.queryByText("row gc")).not.toBeInTheDocument();
    });

    it("assigns this layer to each selected group with the chosen role", async () => {
        const user = userEvent.setup();
        const posted: { url: string; body: unknown }[] = [];
        server.use(
            http.post(`${API_BASE}/layer-groups/:groupId/layers`, async ({ request }) => {
                posted.push({ url: request.url, body: await request.json() });
                return HttpResponse.json([]);
            }),
        );

        renderSection();
        await waitFor(() => expect(assignButton()).toBeInTheDocument());
        await user.click(assignButton());
        await waitFor(() => expect(screen.getByText("row ga")).toBeInTheDocument());

        await user.click(screen.getByText("row ga"));
        await user.click(screen.getByText("row gd"));
        await user.selectOptions(screen.getByLabelText("role-select"), "EDITOR");
        await user.click(screen.getByRole("button", { name: /Add/ }));

        // One request per group, each carrying just this layer — the endpoint is keyed by group.
        await waitFor(() => expect(posted).toHaveLength(2));
        expect(posted.map((p) => p.url).sort()).toEqual([
            `${API_BASE}/layer-groups/ga/layers`,
            `${API_BASE}/layer-groups/gd/layers`,
        ]);
        for (const { body } of posted) {
            expect(body).toEqual({ layerIds: [LAYER_ID], role: "EDITOR" });
        }
    });

    it("drops the groups that already committed before a retry", async () => {
        const user = userEvent.setup();
        const posted: string[] = [];
        server.use(
            http.post(`${API_BASE}/layer-groups/ga/layers`, ({ request }) => {
                posted.push(request.url);
                return HttpResponse.json([]);
            }),
            http.post(`${API_BASE}/layer-groups/gd/layers`, ({ request }) => {
                posted.push(request.url);
                return HttpResponse.json({ message: "denied" }, { status: 403 });
            }),
        );

        renderSection();
        await waitFor(() => expect(assignButton()).toBeInTheDocument());
        await user.click(assignButton());
        await waitFor(() => expect(screen.getByText("row ga")).toBeInTheDocument());

        await user.click(screen.getByText("row ga"));
        await user.click(screen.getByText("row gd"));
        await user.selectOptions(screen.getByLabelText("role-select"), "EDITOR");
        await user.click(screen.getByRole("button", { name: /Add/ }));

        await waitFor(() => expect(showToast).toHaveBeenCalled());
        posted.length = 0;

        // Alpha committed; the picker stays open for a retry, but re-sending Alpha would rewrite
        // the role that already landed there, since the endpoint upserts.
        await user.selectOptions(screen.getByLabelText("role-select"), "ADMIN");
        await user.click(screen.getByRole("button", { name: /Add/ }));

        await waitFor(() => expect(posted).toHaveLength(1));
        expect(posted[0]).toBe(`${API_BASE}/layer-groups/gd/layers`);
    });

    it("renders no assign action when readOnly", async () => {
        renderSection({ readOnly: true });

        // The assigned rows still render — the section is view-only, not hidden.
        await waitFor(() => expect(screen.getByText("row gb")).toBeInTheDocument());
        expect(
            screen.queryByRole("button", { name: i18n.t("adminDialogs.assign") }),
        ).not.toBeInTheDocument();
    });

    it("does not fetch the admin-only group pool when readOnly", async () => {
        let poolFetches = 0;
        server.use(
            http.get(GROUPS_URL, () => {
                poolFetches += 1;
                return HttpResponse.json(GROUPS);
            }),
        );

        renderSection({ readOnly: true });
        await waitFor(() => expect(screen.getByText("row gb")).toBeInTheDocument());

        // /admin/layer-groups 403s for a non-admin, so the read-only My Layers view must not call it.
        expect(poolFetches).toBe(0);
    });
});

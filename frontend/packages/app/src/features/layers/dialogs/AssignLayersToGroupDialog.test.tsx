// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { AssignLayersToGroupDialog } from "./AssignLayersToGroupDialog";
import type { AssignLayerSubject } from "./AssignLayersToGroupDialog";
import { layerGroupQueries } from "~/features/layer-groups/layer-groups.queries";
import type { LayerGroup } from "~/features/layer-groups/layer-groups.queries";

// ResourceTable/OcSelect are zag.js widgets that don't drive in jsdom; FormDialog renders through a
// portal with focus management that fights the test renderer. Replace all three with the smallest
// doubles that expose this dialog's own wiring — row selection, the role select, and submit.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    type Row = Record<string, unknown>;
    interface TableProps {
        "aria-label"?: string;
        data: Row[];
        getRowId: (row: Row) => string;
        rowSelection?: Record<string, boolean>;
        onRowSelectionChange?: (next: Record<string, boolean>) => void;
    }
    interface SelectProps {
        items: { label: string; value: string }[];
        value?: string;
        onValueChange: (value: string) => void;
        placeholder?: string;
    }
    interface DialogProps {
        open?: boolean;
        children?: unknown;
        onSubmit: () => void;
        submitLabel: string;
        submitDisabled?: boolean;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        FormDialog: (props: DialogProps) =>
            props.open === false
                ? null
                : createElement(
                      "div",
                      null,
                      props.children as never,
                      createElement(
                          "button",
                          {
                              type: "button",
                              disabled: !!props.submitDisabled,
                              onClick: props.onSubmit,
                          },
                          props.submitLabel,
                      ),
                  ),
        ResourceTable: (props: TableProps) =>
            createElement(
                "div",
                { "aria-label": props["aria-label"] },
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

function group(id: string, name: string, accessScope: LayerGroup["accessScope"]): LayerGroup {
    return { id, name, description: null, accessScope, createdAt: "2026-01-01T00:00:00Z" };
}

const GROUPS = [
    group("ga", "Alpha", "PRIVATE"),
    group("gb", "Bravo", "GLOBAL"),
    group("gc", "Charlie", "PRIVATE"),
];

const GROUPS_URL = `${API_BASE}/admin/layer-groups`;

function subject(id: string, accessScope: AssignLayerSubject["accessScope"]): AssignLayerSubject {
    return { id, name: `Layer ${id}`, accessScope };
}

function renderDialog(layers: AssignLayerSubject[], onClose = vi.fn()) {
    render(<AssignLayersToGroupDialog isOpen onClose={onClose} layers={layers} />, {
        queryData: {
            [JSON.stringify(layerGroupQueries.adminList().queryKey)]: GROUPS,
        },
    });
    return { onClose };
}

const submitButton = () =>
    screen.getByRole("button", { name: i18n.t("common.add") }) as HTMLButtonElement;

describe("AssignLayersToGroupDialog", () => {
    beforeEach(() => {
        showToast.mockReset();
        server.use(http.get(GROUPS_URL, () => HttpResponse.json(GROUPS)));
    });

    it("assigns every selected layer to every selected group with one shared role", async () => {
        const user = userEvent.setup();
        const posted: { url: string; body: unknown }[] = [];
        server.use(
            http.post(`${API_BASE}/layer-groups/:groupId/layers`, async ({ request }) => {
                posted.push({ url: request.url, body: await request.json() });
                return HttpResponse.json([]);
            }),
        );

        const { onClose } = renderDialog([subject("l1", "GLOBAL"), subject("l2", "GLOBAL")]);
        await waitFor(() => expect(screen.getByText("row ga")).toBeInTheDocument());

        await user.click(screen.getByText("row ga"));
        await user.click(screen.getByText("row gc"));
        await user.selectOptions(screen.getByLabelText("role-select"), "EDITOR");
        await user.click(submitButton());

        // One request per group, each carrying the full layer batch.
        await waitFor(() => expect(posted).toHaveLength(2));
        expect(posted.map((p) => p.url).sort()).toEqual([
            `${API_BASE}/layer-groups/ga/layers`,
            `${API_BASE}/layer-groups/gc/layers`,
        ]);
        for (const { body } of posted) {
            expect(body).toEqual({ layerIds: ["l1", "l2"], role: "EDITOR" });
        }
        await waitFor(() => expect(onClose).toHaveBeenCalled());
    });

    it("keeps submit disabled until both a group and a role are chosen", async () => {
        const user = userEvent.setup();
        renderDialog([subject("l1", "GLOBAL")]);
        await waitFor(() => expect(screen.getByText("row ga")).toBeInTheDocument());

        expect(submitButton()).toBeDisabled();

        await user.click(screen.getByText("row ga"));
        // A group without a role is still not submittable — the endpoint requires a role.
        expect(submitButton()).toBeDisabled();

        await user.selectOptions(screen.getByLabelText("role-select"), "MEMBER");
        expect(submitButton()).toBeEnabled();
    });

    it("names the groups that failed and stays open on partial failure", async () => {
        const user = userEvent.setup();
        server.use(
            http.post(`${API_BASE}/layer-groups/ga/layers`, () => HttpResponse.json([])),
            http.post(`${API_BASE}/layer-groups/gc/layers`, () =>
                HttpResponse.json({ message: "denied" }, { status: 403 }),
            ),
        );

        const { onClose } = renderDialog([subject("l1", "GLOBAL")]);
        await waitFor(() => expect(screen.getByText("row ga")).toBeInTheDocument());

        await user.click(screen.getByText("row ga"));
        await user.click(screen.getByText("row gc"));
        await user.selectOptions(screen.getByLabelText("role-select"), "MEMBER");
        await user.click(submitButton());

        // The name matters: a bare count wouldn't tell the user which group to retry.
        await waitFor(() => expect(showToast).toHaveBeenCalled());
        expect(showToast.mock.calls[0][0]).toMatchObject({
            type: "error",
            description: "Charlie",
        });
        expect(onClose).not.toHaveBeenCalled();

        // Alpha already committed, so it must be dropped from the selection — otherwise submitting
        // again (the natural retry) re-POSTs it, and the endpoint's upsert would rewrite the role
        // that already landed there.
        const posted: string[] = [];
        server.use(
            http.post(`${API_BASE}/layer-groups/:groupId/layers`, ({ request }) => {
                posted.push(request.url);
                return HttpResponse.json([]);
            }),
        );
        await user.selectOptions(screen.getByLabelText("role-select"), "EDITOR");
        await user.click(submitButton());

        await waitFor(() => expect(posted).toHaveLength(1));
        expect(posted[0]).toBe(`${API_BASE}/layer-groups/gc/layers`);
    });

    it("excludes global groups when any selected layer is private", async () => {
        renderDialog([subject("l1", "GLOBAL"), subject("l2", "PRIVATE")]);

        await waitFor(() => expect(screen.getByText("row ga")).toBeInTheDocument());
        expect(screen.getByText("row gc")).toBeInTheDocument();
        // Bravo is GLOBAL; the private layer would 409 against it.
        expect(screen.queryByText("row gb")).not.toBeInTheDocument();
    });

    it("explains the empty state when no group can take the selection", async () => {
        server.use(http.get(GROUPS_URL, () => HttpResponse.json([group("gb", "Bravo", "GLOBAL")])));
        render(
            <AssignLayersToGroupDialog
                isOpen
                onClose={vi.fn()}
                layers={[subject("l1", "PRIVATE")]}
            />,
            {
                queryData: {
                    [JSON.stringify(layerGroupQueries.adminList().queryKey)]: [
                        group("gb", "Bravo", "GLOBAL"),
                    ],
                },
            },
        );

        // An empty table would read as "no groups exist"; this says why there are none to pick.
        await waitFor(() =>
            expect(
                screen.getByText(i18n.t("adminLayers.noEligibleLayerGroups")),
            ).toBeInTheDocument(),
        );
        expect(screen.queryByText("row gb")).not.toBeInTheDocument();
    });
});

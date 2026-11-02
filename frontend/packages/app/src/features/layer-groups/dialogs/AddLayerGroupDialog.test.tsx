// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    render,
    screen,
    waitFor,
    userEvent,
    server,
    http,
    HttpResponse,
    API_BASE,
    createTestUser,
} from "~/test";
import { describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { AddLayerGroupDialog } from "./AddLayerGroupDialog";

// FormDialog's portal focus management fights the test renderer and OcTabSelect is a zag.js
// widget that doesn't drive in jsdom, so both are replaced with minimal doubles.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    interface DialogProps {
        open?: boolean;
        children?: unknown;
        onSubmit: () => void;
        submitLabel: string;
        submitDisabled?: boolean;
    }
    interface TabSelectProps {
        options: { label: string; value: string }[];
        defaultValue?: string;
        disabled?: boolean;
        disabledReason?: string;
        onChange: (value: string) => void;
    }
    interface TooltipProps {
        content: string;
        children?: unknown;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        // The real tooltip mounts content only on hover, so render it inline for assertions.
        Tooltip: (props: TooltipProps) =>
            createElement("div", null, props.children as never, props.content),
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
        // Renders the disabled reason inline under the same gate as the real component, which
        // surfaces it through a Tooltip only when the control is actually disabled.
        OcTabSelect: (props: TabSelectProps) =>
            createElement(
                "div",
                null,
                createElement(
                    "select",
                    {
                        "aria-label": "access-scope",
                        disabled: !!props.disabled,
                        defaultValue: props.defaultValue,
                        onChange: (e: { target: { value: string } }) =>
                            props.onChange(e.target.value),
                    },
                    ...props.options.map((o) =>
                        createElement("option", { key: o.value, value: o.value }, o.label),
                    ),
                ),
                props.disabled && props.disabledReason != null ? props.disabledReason : null,
            ),
    };
});

const adminUser = createTestUser({ authorities: ["ROLE_admin"] });

describe("AddLayerGroupDialog", () => {
    it("creates through the non-admin route, defaulting to PRIVATE scope", async () => {
        const requests: { url: string; body: unknown }[] = [];
        server.use(
            http.post(`${API_BASE}/layer-groups`, async ({ request }) => {
                requests.push({ url: request.url, body: await request.json() });
                return HttpResponse.json({
                    id: "g1",
                    name: "Recon",
                    description: null,
                    accessScope: "PRIVATE",
                    createdAt: "2026-01-01T00:00:00Z",
                });
            }),
        );
        const onClose = vi.fn();
        render(<AddLayerGroupDialog isOpen onClose={onClose} />);

        await userEvent.type(screen.getByRole("textbox", { name: /name/i }), "Recon");
        await userEvent.click(screen.getByRole("button", { name: /create/i }));

        await waitFor(() => expect(requests).toHaveLength(1));
        // A normal user would get a 403 from /admin/layer-groups, so this must not hit it.
        expect(requests[0].url).toContain("/layer-groups");
        expect(requests[0].url).not.toContain("/admin/");
        expect(requests[0].body).toMatchObject({ name: "Recon", accessScope: "PRIVATE" });
        await waitFor(() => expect(onClose).toHaveBeenCalled());
    });

    it("disables the scope control for a non-admin and explains why", () => {
        render(<AddLayerGroupDialog isOpen onClose={vi.fn()} />);

        expect(screen.getByLabelText("access-scope")).toBeDisabled();
        expect(screen.getByText(i18n.t("adminLayerGroups.adminOnlyGlobalScope"))).toBeTruthy();
    });

    it("offers an enabled scope selector to a global admin, with no disabled reason", () => {
        render(<AddLayerGroupDialog isOpen onClose={vi.fn()} />, { user: adminUser });

        expect(screen.getByLabelText("access-scope")).toBeEnabled();
        expect(screen.queryByText(i18n.t("adminLayerGroups.adminOnlyGlobalScope"))).toBeNull();
    });

    it("keeps submit disabled until a name is entered", async () => {
        render(<AddLayerGroupDialog isOpen onClose={vi.fn()} />);

        const submit = screen.getByRole("button", { name: /create/i });
        expect(submit).toBeDisabled();

        await userEvent.type(screen.getByRole("textbox", { name: /name/i }), "Recon");
        expect(submit).toBeEnabled();
    });
});

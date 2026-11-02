// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, createTestUser } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { profileQueries } from "~/features/data-sources/data-source-profiles.queries";
import { AddLayerDialog } from "./AddLayerDialog";

// FormDialog's portal focus management fights the test renderer and OcTabSelect/OcSelect are
// zag.js widgets that don't drive in jsdom, so all three are replaced with minimal doubles.
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
    interface SelectProps {
        items: { label: string; value: string }[];
        onValueChange: (value: string) => void;
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
                          { type: "button", disabled: !!props.submitDisabled },
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
        OcSelect: (props: SelectProps) =>
            createElement(
                "select",
                { "aria-label": "mapper-select", onChange: () => props.onValueChange("") },
                ...props.items.map((it) =>
                    createElement("option", { key: it.value, value: it.value }, it.label),
                ),
            ),
    };
});

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

const adminUser = createTestUser({ authorities: ["ROLE_admin"] });
const seededMappers = { [JSON.stringify(profileQueries.mapperProfileList().queryKey)]: [] };

describe("AddLayerDialog access scope", () => {
    it("disables the scope control for a non-admin and explains why", () => {
        render(<AddLayerDialog isOpen onClose={vi.fn()} />, { queryData: seededMappers });

        expect(screen.getByLabelText("access-scope")).toBeDisabled();
        expect(screen.getByText(i18n.t("layers.adminOnlyGlobalScope"))).toBeTruthy();
    });

    it("offers an enabled scope selector to a global admin, with no disabled reason", () => {
        render(<AddLayerDialog isOpen onClose={vi.fn()} />, {
            user: adminUser,
            queryData: seededMappers,
        });

        expect(screen.getByLabelText("access-scope")).toBeEnabled();
        expect(screen.queryByText(i18n.t("layers.adminOnlyGlobalScope"))).toBeNull();
    });
});

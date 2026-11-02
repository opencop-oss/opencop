// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, createTestUser } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import type { MyLayerGroup } from "../layer-groups.queries";
import { MyLayerGroupDrawerContent } from "./MyLayerGroupDrawerContent";

/**
 * DrawerSideNavLayout is a resizable nav surface that doesn't drive in jsdom. The double renders
 * only the section labels, so these tests see which sections are offered without mounting section
 * bodies or firing their queries.
 */
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    interface Section {
        key: string;
        label: string;
    }
    interface LayoutProps {
        name: string;
        sections: Section[];
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        DrawerSideNavLayout: (props: LayoutProps) =>
            createElement(
                "div",
                null,
                createElement("h2", null, props.name),
                ...props.sections.map((s) =>
                    createElement("button", { key: s.key, type: "button" }, s.label),
                ),
            ),
    };
});

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

const adminUser = createTestUser({ authorities: ["ROLE_admin"] });

function group(): MyLayerGroup {
    return {
        id: "g1",
        name: "Recon",
        description: null,
        containerRole: "OWNER",
        accessScope: "PRIVATE",
        createdAt: "2026-01-01T00:00:00Z",
        layers: [],
    };
}

describe("MyLayerGroupDrawerContent sections", () => {
    /**
     * A non-admin owner passes the ADMIN/OWNER gate that makes this section editable, and its pool
     * now comes from the authenticated GET /user-groups/search rather than the admin listing, so
     * the section is offered to them rather than withheld.
     */
    it("offers the User Groups section to a non-admin owner", () => {
        render(<MyLayerGroupDrawerContent group={group()} onOpenModal={vi.fn()} />);

        expect(
            screen.getByRole("button", { name: i18n.t("adminLayerGroups.userGroups") }),
        ).toBeVisible();
        expect(
            screen.getByRole("button", { name: i18n.t("adminLayerGroups.members") }),
        ).toBeTruthy();
        expect(
            screen.getByRole("button", { name: i18n.t("adminLayerGroups.layers") }),
        ).toBeTruthy();
    });

    // Visibility only. This suite's DrawerSideNavLayout double renders section labels and never
    // section content, so it cannot observe `readOnly`. The edit gate is proved in
    // LayerGroupUserGroupsSection.test.tsx.
    it("still offers the User Groups section to a group MEMBER", () => {
        render(
            <MyLayerGroupDrawerContent
                group={{ ...group(), containerRole: "MEMBER" }}
                onOpenModal={vi.fn()}
            />,
        );

        expect(
            screen.getByRole("button", { name: i18n.t("adminLayerGroups.userGroups") }),
        ).toBeVisible();
    });

    it("offers the User Groups section to a global admin", () => {
        render(<MyLayerGroupDrawerContent group={group()} onOpenModal={vi.fn()} />, {
            user: adminUser,
        });

        expect(
            screen.getByRole("button", { name: i18n.t("adminLayerGroups.userGroups") }),
        ).toBeTruthy();
    });
});

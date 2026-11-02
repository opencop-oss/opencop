// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { Drawer } from "@chakra-ui/react";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { DrawerSectionHeader } from "./DrawerSectionHeader";
import { DrawerSideNavLayout } from "../surfaces/DrawerSideNavLayout";
import type { DrawerSideNavSection } from "../surfaces/DrawerSideNavLayout";

const RAIL_LABEL = "Members";
const SECTION_TITLE = "Layer Members";
const SECTION_DESCRIPTION = "Users with explicit permissions granted directly to this layer.";
const ASSIGN_TITLE = "Assign Members to Layer";
const BACK_LABEL = "Back to Members";

function membersSection(content: ReactNode): DrawerSideNavSection[] {
    return [{ key: "members", label: RAIL_LABEL, content }];
}

function Harness({ sections }: { sections: DrawerSideNavSection[] }) {
    return (
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <Drawer.Root open>
                    <DrawerSideNavLayout name="E2E - Filter Test Layer" sections={sections} />
                </Drawer.Root>
            </OcThemeProvider>
        </I18nextProvider>
    );
}

describe("DrawerSectionHeader", () => {
    it("names the enclosing panel, so entering it announces the section not the rail label", () => {
        render(
            <Harness
                sections={membersSection(
                    <DrawerSectionHeader title={SECTION_TITLE} description={SECTION_DESCRIPTION} />,
                )}
            />,
        );

        expect(screen.getByRole("tabpanel")).toHaveAccessibleName(SECTION_TITLE);
    });

    it("discloses the description from an info affordance named for the section", async () => {
        const user = userEvent.setup();
        render(
            <Harness
                sections={membersSection(
                    <DrawerSectionHeader title={SECTION_TITLE} description={SECTION_DESCRIPTION} />,
                )}
            />,
        );

        // Named for what it discloses rather than repeating the heading verbatim,
        // so a screen reader does not announce the same string twice in a row.
        const info = screen.getByRole("button", { name: `About ${SECTION_TITLE}` });
        await user.hover(info);

        expect(await screen.findByText(SECTION_DESCRIPTION)).toBeInTheDocument();
    });

    it("omits the info affordance when a section has no description", () => {
        render(
            <Harness sections={membersSection(<DrawerSectionHeader title={SECTION_TITLE} />)} />,
        );

        expect(
            screen.queryByRole("button", { name: `About ${SECTION_TITLE}` }),
        ).not.toBeInTheDocument();
    });

    // The drawer's own Drawer.Title is the h2, so a panel heading sits under it.
    it("renders the title as an h3 beneath the drawer's own title", () => {
        render(
            <Harness sections={membersSection(<DrawerSectionHeader title={SECTION_TITLE} />)} />,
        );

        expect(screen.getByRole("heading", { level: 3 })).toHaveTextContent(SECTION_TITLE);
    });

    it("falls back to the rail tab when a panel renders no header", () => {
        render(<Harness sections={membersSection(<div data-testid="bare" />)} />);

        expect(screen.getByRole("tabpanel")).toHaveAccessibleName(RAIL_LABEL);
    });

    it("renames the panel when a sub-flow takes it over", () => {
        const { rerender } = render(
            <Harness sections={membersSection(<DrawerSectionHeader title={SECTION_TITLE} />)} />,
        );
        expect(screen.getByRole("tabpanel")).toHaveAccessibleName(SECTION_TITLE);

        rerender(
            <Harness
                sections={membersSection(
                    <DrawerSectionHeader
                        title={ASSIGN_TITLE}
                        back={{ label: BACK_LABEL, onBack: vi.fn() }}
                    />,
                )}
            />,
        );

        expect(screen.getByRole("tabpanel")).toHaveAccessibleName(ASSIGN_TITLE);
    });

    it("routes the back control to onBack", async () => {
        const user = userEvent.setup();
        const onBack = vi.fn();

        render(
            <Harness
                sections={membersSection(
                    <DrawerSectionHeader
                        title={ASSIGN_TITLE}
                        back={{ label: BACK_LABEL, onBack }}
                    />,
                )}
            />,
        );

        await user.click(screen.getByRole("button", { name: BACK_LABEL }));

        expect(onBack).toHaveBeenCalledOnce();
    });
});

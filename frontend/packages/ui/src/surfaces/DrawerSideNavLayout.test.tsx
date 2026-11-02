// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { Drawer } from "@chakra-ui/react";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { DrawerSideNavLayout } from "./DrawerSideNavLayout";
import type { DrawerSideNavSection } from "./DrawerSideNavLayout";

// A focusable control that stays mounted through the lock. This exercises the still-mounted
// half of the focus contract in the abstract. No current caller behaves this way: the real
// "Change role" button unmounts when the sub-flow opens, which UnmountingOpenerHarness covers.
const SECTIONS: DrawerSideNavSection[] = [
    {
        key: "members",
        label: "Members",
        content: (
            <button type="button" data-testid="opener">
                open sub-flow
            </button>
        ),
    },
    { key: "groups", label: "Groups", content: <div>groups</div> },
];

// Drawer.Body/Header read the Chakra drawer slot context, so the layout must render
// inside a Drawer.Root (open, so the content mounts).
function Harness({ locked, editContent }: { locked?: boolean; editContent?: ReactNode }) {
    return (
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <Drawer.Root open>
                    <DrawerSideNavLayout
                        name="Layer 1"
                        sections={SECTIONS}
                        locked={locked}
                        editContent={editContent}
                    />
                </Drawer.Root>
            </OcThemeProvider>
        </I18nextProvider>
    );
}

describe("DrawerSideNavLayout — chrome-lock focus management", () => {
    // Guards `wasChromeLockedRef`: without it the restore branch fires on a first mount and
    // every drawer pulls focus into its panel on open. The other tests cannot catch that, as
    // each focuses another element right after mount, overwriting the stolen focus.
    it("does not move focus on an ordinary mount", () => {
        render(<Harness locked={false} />);

        expect(document.activeElement).not.toBe(screen.getByRole("tabpanel"));
    });

    it("moves focus to the content panel when a locked sub-flow opens", () => {
        const { rerender } = render(<Harness locked={false} />);

        // The user's focus starts on the control that opens the sub-flow.
        const opener = screen.getByTestId("opener");
        opener.focus();
        expect(document.activeElement).toBe(opener);

        // Locking dims and disables the rail, so focus must move off it, not fall to the body.
        rerender(<Harness locked />);

        expect(document.activeElement).toBe(screen.getByRole("tabpanel"));
    });

    it("restores focus to the opener when the locked sub-flow closes", () => {
        const { rerender } = render(<Harness locked={false} />);
        screen.getByTestId("opener").focus();

        rerender(<Harness locked />);
        expect(document.activeElement).toBe(screen.getByRole("tabpanel"));

        // Unlocking returns focus to the still-mounted control that opened the flow.
        rerender(<Harness locked={false} />);
        expect(document.activeElement).toBe(screen.getByTestId("opener"));
    });

    it("moves focus to the content panel when the inline edit form opens", () => {
        // Regression guard: the edit flow shares the same focus effect as `locked`.
        const { rerender } = render(<Harness />);
        screen.getByTestId("opener").focus();

        rerender(<Harness editContent={<div>edit form</div>} />);

        expect(document.activeElement).toBe(screen.getByRole("tabpanel"));
    });
});

// Mirrors the real callers: they hide the header `actions` while chrome-locked, and a
// locked sub-flow replaces the section content that holds its own opener. Either way the
// opener unmounts in the same commit that sets the lock, so focus must land on the panel.
function UnmountingOpenerHarness({
    locked = false,
    editing = false,
}: {
    locked?: boolean;
    editing?: boolean;
}) {
    const chromeLocked = locked || editing;
    return (
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <Drawer.Root open>
                    <DrawerSideNavLayout
                        name="Layer 1"
                        // Mirrors every caller: the header opener is hidden while chrome-locked.
                        actions={
                            chromeLocked ? undefined : (
                                <button type="button" data-testid="header-opener">
                                    edit
                                </button>
                            )
                        }
                        sections={[
                            {
                                key: "members",
                                label: "Members",
                                content: locked ? (
                                    <div>change role panel</div>
                                ) : (
                                    <button type="button" data-testid="section-opener">
                                        open sub-flow
                                    </button>
                                ),
                            },
                        ]}
                        locked={locked}
                        editContent={editing ? <div>edit form</div> : undefined}
                    />
                </Drawer.Root>
            </OcThemeProvider>
        </I18nextProvider>
    );
}

describe("DrawerSideNavLayout — chrome-lock focus fallback", () => {
    it("falls back to the content panel when a locked sub-flow unmounted its opener", () => {
        const { rerender } = render(<UnmountingOpenerHarness />);
        screen.getByTestId("section-opener").focus();

        rerender(<UnmountingOpenerHarness locked />);
        expect(document.activeElement).toBe(screen.getByRole("tabpanel"));

        rerender(<UnmountingOpenerHarness />);

        expect(document.activeElement).not.toBe(document.body);
        expect(document.activeElement).toBe(screen.getByRole("tabpanel"));
    });

    it("falls back to the content panel when the inline edit form closes", () => {
        const { rerender } = render(<UnmountingOpenerHarness />);
        screen.getByTestId("header-opener").focus();

        rerender(<UnmountingOpenerHarness editing />);
        expect(document.activeElement).toBe(screen.getByRole("tabpanel"));

        rerender(<UnmountingOpenerHarness />);

        expect(document.activeElement).not.toBe(document.body);
        expect(document.activeElement).toBe(screen.getByRole("tabpanel"));
    });
});

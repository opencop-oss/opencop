// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Drawer } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";
import { createContext, useEffect, useId, useMemo, useRef, useState } from "react";
import { DrawerSideNav } from "../navigation/DrawerSideNav.tsx";
import { EntityDrawerHeader } from "./EntityDrawerHeader.tsx";
import type { EntityDrawerHeaderProps } from "./EntityDrawerHeader.tsx";

export interface DrawerPanelHeadingContextValue {
    /** Id the mounted header must carry so the panel can be labelled by it. */
    headingId: string;
    /** Tells the panel whether a header is currently mounted. */
    setHasHeading: (hasHeading: boolean) => void;
}

/**
 * Lets a drawer content panel be named by whatever {@link DrawerSectionHeader} is
 * mounted inside it rather than by its rail tab, so the panel's accessible name
 * tracks the sub-flow the user is actually in. Optional: a header rendered outside
 * a layout falls back to a local id, so the raw context is safe to export.
 * Rendered directly as a provider (`<DrawerPanelHeadingContext value={…}>`) per React 19.
 */
export const DrawerPanelHeadingContext = createContext<DrawerPanelHeadingContextValue | null>(null);

export interface DrawerSideNavSection {
    /** Stable identifier for the section, used as the active key. */
    key: string;
    /** Rail label. */
    label: ReactNode;
    /** Optional leading icon shown in the rail. */
    icon?: ReactNode;
    /** Panel content rendered when this section is active. */
    content: ReactNode;
}

export interface DrawerSideNavLayoutProps extends EntityDrawerHeaderProps {
    sections: DrawerSideNavSection[];
    /** Key of the section shown initially. Defaults to the first section. */
    defaultSection?: string;
    /** Accessible label for the navigation rail. */
    navLabel?: string;
    /**
     * When set, the content panel renders this (an inline edit form) instead of
     * the active section, and the side-nav rail is dimmed and disabled. The
     * header stays; callers typically hide the header `actions` while editing.
     */
    editContent?: ReactNode;
    /**
     * Locks the drawer chrome like `editContent` (dims + disables the nav rail)
     * but keeps rendering the active section for a focused sub-flow that
     * lives inside a section (e.g. a bulk change dialog).
     * Callers should also hide the header `actions` while locked.
     */
    locked?: boolean;
}

/**
 * Entity drawer shell with a vertical side-nav. Renders the shared
 * {@link EntityDrawerHeader}, a {@link DrawerSideNav} rail on the left, and the
 * active section's content in a scrollable panel on the right. Only the active
 * section is mounted, so each section's data fetching and local state start
 * fresh when it is revisited.
 *
 * Focus contract for the two chrome-locked flows (`editContent` and `locked`):
 * opening one focuses the content panel, and closing it focuses the content
 * panel again. Focus is never left on the document body. Closing returns focus
 * to the control that opened the flow instead, when that control is still
 * mounted, but no caller produces that case today.
 */
export function DrawerSideNavLayout({
    name,
    badgeLabel,
    icon,
    actions,
    sections,
    defaultSection,
    navLabel,
    editContent,
    locked = false,
}: DrawerSideNavLayoutProps): ReactElement {
    const [active, setActive] = useState(defaultSection ?? sections[0]?.key);
    const activeSection = sections.find((s) => s.key === active) ?? sections[0];
    const editing = editContent != null;

    // The panel is named by whatever `DrawerSectionHeader` is currently mounted in
    // it, so its accessible name tracks the sub-flow the user is actually in
    // ("Assign Members to Layer") rather than the rail label they arrived from. A
    // panel that renders no header falls back to its rail tab.
    const headingId = useId();
    const [hasHeading, setHasHeading] = useState(false);
    const panelHeading = useMemo(() => ({ headingId, setHasHeading }), [headingId, setHasHeading]);
    // The nav rail is dimmed/disabled for both the edit form and any locked
    // sub-flow; only editing swaps out the section content.
    const chromeLocked = editing || locked;

    // Focus handling for the chrome-locked flows; see the focus contract above.
    const panelRef = useRef<HTMLDivElement>(null);
    const chromeReturnRef = useRef<HTMLElement | SVGElement | null>(null);
    const wasChromeLockedRef = useRef(false);

    useEffect(() => {
        if (chromeLocked) {
            const focused = document.activeElement;
            // SVGElement is admitted alongside HTMLElement because both carry the
            // HTMLOrSVGElement mixin that supplies focus(), so an SVG-rooted control is just as
            // restorable. The body is always connected but never focusable, so restoring to it
            // would silently drop focus; treat it as nothing to return to.
            chromeReturnRef.current =
                (focused instanceof HTMLElement || focused instanceof SVGElement) &&
                focused !== document.body
                    ? focused
                    : null;
            panelRef.current?.focus();
        } else if (wasChromeLockedRef.current) {
            // Defensive: restores the opener if it is still mounted when the lock releases.
            // Unreachable on main today; kept in case a future caller keeps its opener
            // mounted through the lock.
            const opener = chromeReturnRef.current;
            if (opener?.isConnected) opener.focus();
            else panelRef.current?.focus();
            chromeReturnRef.current = null;
        }
        wasChromeLockedRef.current = chromeLocked;
    }, [chromeLocked]);

    return (
        <>
            <EntityDrawerHeader name={name} badgeLabel={badgeLabel} icon={icon} actions={actions} />
            <Drawer.Body flex="1" minH={0} p={0} display="flex" overflow="hidden">
                <Box
                    as="nav"
                    flexShrink={0}
                    w="180px"
                    py={3}
                    px={2}
                    borderRightWidth="1px"
                    borderRightColor="border.muted"
                    overflowY="auto"
                    opacity={chromeLocked ? 0.4 : 1}
                    inert={chromeLocked || undefined}
                    transition="opacity 0.15s ease"
                >
                    <DrawerSideNav
                        aria-label={navLabel}
                        items={sections.map((s) => ({
                            value: s.key,
                            label: s.label,
                            icon: s.icon,
                        }))}
                        value={activeSection?.key ?? ""}
                        onChange={setActive}
                    />
                </Box>
                <Box
                    ref={panelRef}
                    flex={1}
                    minW={0}
                    p={4}
                    overflowY="auto"
                    display="flex"
                    flexDirection="column"
                    role="tabpanel"
                    id={activeSection ? `panel-${activeSection.key}` : undefined}
                    /* Departs from the APG Tabs pattern, which labels a tabpanel by its own
                       tab. "Assign Members to Layer" is a more useful panel name than the rail's
                       "Members", and a panel with no header still falls back to its tab — so the
                       panel always has an accessible name and 4.1.2 holds. */
                    aria-labelledby={
                        hasHeading
                            ? headingId
                            : activeSection
                              ? `tab-${activeSection.key}`
                              : undefined
                    }
                    tabIndex={0}
                >
                    <DrawerPanelHeadingContext value={panelHeading}>
                        {editing && editContent}
                        {!editing && activeSection?.content}
                    </DrawerPanelHeadingContext>
                </Box>
            </Drawer.Body>
        </>
    );
}

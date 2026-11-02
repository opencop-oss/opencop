// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MdPlaylistAdd, MdPlaylistRemove } from "react-icons/md";

/**
 * Material glyphs sit inset in their box, so at the 1rem Chakra gives every button icon they read
 * smaller than the Font Awesome icons beside them. An inline size beats that rule.
 */
const ICON_SIZE = { width: "1.3rem", height: "1.3rem" };

/** Toolbar icon for hiding a layer or data source from the map panel, or showing it again. */
export function PanelExclusionIcon({ excluded }: { excluded: boolean }) {
    const Icon = excluded ? MdPlaylistAdd : MdPlaylistRemove;
    return <Icon style={ICON_SIZE} aria-hidden />;
}

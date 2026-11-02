// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { MdPlaylistRemove } from "react-icons/md";

/** Row marker for a layer or data source the user removed from the map panel. */
export function PanelExclusionIndicator({ excluded }: { excluded: boolean }) {
    const { t } = useTranslation();
    if (!excluded) return null;
    const label = t("filters.panelExclusion.indicator");
    return (
        <Box
            as="span"
            role="img"
            display="inline-flex"
            color="fg.muted"
            title={label}
            aria-label={label}
        >
            <MdPlaylistRemove size={13} aria-hidden />
        </Box>
    );
}

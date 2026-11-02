// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { OcSelect } from "@opencop/ui";
import { type ReactElement, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { type AudienceMode, AUDIENCE_MODES } from "./audience.util";

interface AudienceModeSelectProps {
    value: AudienceMode;
    onChange: (mode: AudienceMode) => void;
    disabled?: boolean;
}

/**
 * Mode picker for the Audience row of the drawer overview.
 *
 * A select rather than a radio group: three mutually exclusive values sitting inside a
 * label/value detail table read as a field, and the radio stack broke the table's rhythm.
 */
export function AudienceModeSelect({
    value,
    onChange,
    disabled,
}: AudienceModeSelectProps): ReactElement {
    const { t } = useTranslation();
    const items = useMemo(
        () =>
            AUDIENCE_MODES.map((mode) => ({
                label: t(`runtimeFeatures.audienceModes.${mode}` as never) as string,
                value: mode,
            })),
        [t],
    );

    return (
        <Box maxW="220px">
            <OcSelect
                items={items}
                value={value}
                onValueChange={(next) => onChange(next as AudienceMode)}
                disabled={disabled}
                size="sm"
                // Through `triggerProps` rather than as a bare prop: `OcSelect` declares no
                // `aria-label` and does not spread residual props, so one passed directly is
                // dropped and the trigger ends up with no accessible name at all. This row's
                // label lives in the detail table, which is not associated with the control.
                triggerProps={{ "aria-label": t("runtimeFeatures.audience") }}
            />
        </Box>
    );
}

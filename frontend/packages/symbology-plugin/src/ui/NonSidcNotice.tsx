// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { ToolEditorShell } from "./ToolEditorShell";

/**
 * Shown in place of the SIDC editor when the element's code is not an SIDC.
 *
 * Symbology reaches this from its own domain — `SIDC.isSidcString` — rather than
 * by asking whether the code belongs to some other subject's scheme. The wording is
 * deliberately scheme-agnostic for the same reason: media icons are the case that
 * exists today, but symbology has no business enumerating them.
 */
export function NonSidcNotice() {
    const { t } = useTranslation(["translation", "symbology"]);

    return (
        <ToolEditorShell title={t("symbology:symbolWizard.symbolEditor")} showPreview={false}>
            <Alert.Root status="info" size="sm" w="full">
                <Alert.Indicator />
                <Alert.Content>
                    <Alert.Description fontSize="sm">
                        {t("symbology:symbolWizard.notAnSidcReadOnly")}
                    </Alert.Description>
                </Alert.Content>
            </Alert.Root>
        </ToolEditorShell>
    );
}

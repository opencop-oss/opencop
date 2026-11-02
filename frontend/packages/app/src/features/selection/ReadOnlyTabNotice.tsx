// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

/** Explains the Read-only tab: these can be selected and exported, never deleted. */
export function ReadOnlyTabNotice(): ReactNode {
    const { t } = useTranslation();

    return (
        <Alert.Root status="warning" size="sm" variant="outline">
            <Alert.Content>
                <Alert.Description>{t("selectionPanel.readOnlyTabNotice")}</Alert.Description>
            </Alert.Content>
        </Alert.Root>
    );
}

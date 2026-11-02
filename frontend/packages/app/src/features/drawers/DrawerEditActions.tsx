// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Flex } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";

interface DrawerEditActionsProps {
    /** Return to view mode without saving. */
    onCancel: () => void;
    /** Disables both buttons and shows the Save spinner while a mutation is in flight. */
    loading?: boolean;
    /** When false, Save is disabled (e.g. the form is pristine or invalid). */
    canSave?: boolean;
}

/**
 * The Save/Cancel footer shared by every inline drawer edit form. Save is a
 * `type="submit"` button, so the enclosing `<form onSubmit={handleSubmit(...)}>`
 * drives it — the form owns validation and submission, this owns the chrome.
 */
export function DrawerEditActions({
    onCancel,
    loading = false,
    canSave = true,
}: DrawerEditActionsProps): ReactElement {
    const { t } = useTranslation();
    return (
        <Flex align="center" justify="flex-end" gap={2} pt={2}>
            <Button variant="ghost" size="sm" type="button" onClick={onCancel} disabled={loading}>
                {t("common.cancel")}
            </Button>
            <Button
                size="sm"
                type="submit"
                bg="active"
                color="bg"
                loading={loading}
                disabled={loading || !canSave}
            >
                {t("common.save")}
            </Button>
        </Flex>
    );
}

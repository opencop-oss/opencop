// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";

/** Stands in for a Used By value when the data sources it is built from couldn't be loaded. */
export function UsedByLoadError(): ReactElement {
    const { t } = useTranslation();
    return (
        <Text fontSize="xs" color="fg.error">
            {t("admin.usedByLoadError")}
        </Text>
    );
}

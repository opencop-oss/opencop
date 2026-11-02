// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, Text } from "@chakra-ui/react";
import type { AccessMode, TransportType } from "@opencop/api";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { DataSourceAccessWarning } from "./DataSourceAccessWarning";
import {
    getAccessIncompatibilityNotice,
    getAccessModePresentation,
} from "./data-source-access.policy";

interface SourceSummaryAccessModeProps {
    access: AccessMode;
    transportType: TransportType;
}

export function SourceSummaryAccessMode({
    access,
    transportType,
}: SourceSummaryAccessModeProps): ReactElement {
    const { t } = useTranslation();
    const presentation = getAccessModePresentation(access, t);
    const notice = getAccessIncompatibilityNotice({ access, transportType }, t);

    return (
        <HStack gap={1}>
            <Text as="span">{presentation.label}</Text>
            {notice && <DataSourceAccessWarning message={notice} />}
        </HStack>
    );
}

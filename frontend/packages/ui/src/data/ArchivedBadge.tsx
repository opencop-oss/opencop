// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text } from "@chakra-ui/react";
import { FaArchive } from "react-icons/fa";
import { useTranslation } from "react-i18next";

export function ArchivedBadge() {
    const { t } = useTranslation();
    return (
        <Flex align="center" gap={1} color="fg.muted">
            <FaArchive size={9} aria-hidden={true} />
            <Text fontSize="2xs" letterSpacing="wider">
                {t("resourceTable.archivedBadge")}
            </Text>
        </Flex>
    );
}

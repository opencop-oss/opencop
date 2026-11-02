// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { InfoTooltip } from "@opencop/ui";
import type { AccessScope } from "@opencop/api";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { ACCESS_SCOPE_DESCRIPTIONS } from "./layer-groups.util";
import type { RoleDescriptor } from "./layer-groups.util";

const SCOPE_VALUES = new Set<string>(ACCESS_SCOPE_DESCRIPTIONS.map((d) => d.role));

export type { RoleDescriptor };

interface RoleInfoTooltipProps {
    roles: RoleDescriptor[];
    /** Distinguishes this icon's name when several share a page. */
    ariaLabel?: string;
}

export function RoleInfoTooltip({ roles, ariaLabel }: RoleInfoTooltipProps) {
    const { t } = useTranslation();

    const content = (
        <Box minW="220px">
            {roles.map((r) =>
                SCOPE_VALUES.has(r.role) ? (
                    <Flex key={r.role} align="flex-start" gap={2} py={1}>
                        <Box flexShrink={0} pt="2px">
                            <ScopeIndicator scope={r.role as AccessScope} />
                        </Box>
                        <Text fontSize="xs" color="fg.muted" lineHeight="short">
                            {t(r.descriptionKey as never)}
                        </Text>
                    </Flex>
                ) : (
                    <Text key={r.role} fontSize="xs" color="fg.muted" lineHeight="short" py={1}>
                        <Text as="span" fontWeight="semibold" color="fg">
                            {r.role}
                        </Text>
                        {" — "}
                        {t(r.descriptionKey as never)}
                    </Text>
                ),
            )}
        </Box>
    );

    return <InfoTooltip content={content} aria-label={ariaLabel ?? t("common.info")} />;
}

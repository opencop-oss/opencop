// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, VisuallyHidden } from "@chakra-ui/react";
import { FaGlobe } from "react-icons/fa6";
import { TbLock } from "react-icons/tb";
import type { AccessScope } from "@opencop/api";
import { Tooltip } from "@opencop/ui";
import { useTranslation } from "react-i18next";

interface ScopeIndicatorProps {
    scope: AccessScope;
    iconOnly?: boolean;
    iconSize?: number;
}

/**
 * Renders an access scope as an icon + label using the app's color palette tokens —
 * globe for GLOBAL, lock for PRIVATE.
 * Pass `iconOnly` to render just the GLOBAL globe (nothing for other scopes) with a
 * hover tooltip. A tooltip only exposes its text while hovered or focused, so the
 * globe also carries a visually hidden scope label: the scope stays in the accessible
 * name of whatever contains the icon, including rows that never take focus.
 * Pass `iconSize` to override the icon size in `iconOnly` mode (default 14).
 */
export function ScopeIndicator({ scope, iconOnly, iconSize = 14 }: ScopeIndicatorProps) {
    const { t } = useTranslation();
    const isGlobal = scope === "GLOBAL";

    if (iconOnly) {
        if (!isGlobal) return null;
        return (
            <Tooltip content={t("adminLayers.scopeGlobal")} uppercase={false}>
                <Flex
                    as="span"
                    color="type.success"
                    display="inline-flex"
                    alignItems="center"
                    cursor="default"
                >
                    <FaGlobe size={iconSize} />
                    <VisuallyHidden>{t("adminLayers.scopeGlobal")}</VisuallyHidden>
                </Flex>
            </Tooltip>
        );
    }

    const icon = (
        <Flex
            as="span"
            color={isGlobal ? "type.success" : "blue.500"}
            display="inline-flex"
            alignItems="center"
        >
            {isGlobal ? <FaGlobe size={11} /> : <TbLock size={11} />}
        </Flex>
    );

    return (
        <Flex as="span" align="center" gap="3px" display="inline-flex">
            {icon}
            <Text as="span" fontSize="sm" color="fg.muted">
                {isGlobal ? t("adminLayerGroups.scopeGlobal") : t("adminLayerGroups.scopePrivate")}
            </Text>
        </Flex>
    );
}

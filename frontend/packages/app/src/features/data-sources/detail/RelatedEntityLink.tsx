// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Link } from "@chakra-ui/react";
import type { MouseEventHandler, ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { Link as RouterLink } from "react-router";
import { ENTITY_BADGE_CONFIG, relatedEntityLocation } from "../data-resources.util";
import type { ResourceEntityType } from "../data-resources.util";

interface RelatedEntityLinkProps {
    id: string;
    name: string;
    type: ResourceEntityType;
    showTypePrefix?: boolean;
    /** Replaces the type's own label in the prefix, e.g. to name a mapper's direction. */
    typeLabel?: string;
    onClick?: MouseEventHandler<HTMLAnchorElement>;
}

/** A related resource's name, linked to its drawer. */
export function RelatedEntityLink({
    id,
    name,
    type,
    showTypePrefix = true,
    typeLabel,
    onClick,
}: RelatedEntityLinkProps): ReactElement {
    const { t } = useTranslation();
    const config = ENTITY_BADGE_CONFIG[type];
    const label = showTypePrefix ? `${typeLabel ?? t(config.labelKey)}: ${name}` : name;

    return (
        <Link
            asChild
            fontSize="xs"
            color={config.linkColor}
            _hover={{ textDecoration: "underline", color: config.linkColor }}
            onClick={onClick}
        >
            <RouterLink to={relatedEntityLocation({ id, type })}>{label}</RouterLink>
        </Link>
    );
}

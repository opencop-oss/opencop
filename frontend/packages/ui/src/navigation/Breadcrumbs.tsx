// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Flex, Link, Text } from "@chakra-ui/react";
import { FaChevronRight, FaHouse } from "react-icons/fa6";

export interface BreadcrumbItem {
    label: string;
    href?: string;
}

interface BreadcrumbsProps {
    items: BreadcrumbItem[];
    endElement?: ReactNode;
    px?: number | string;
    /** Custom link renderer. Receives the href and children; should return a styled link element.
     *  Defaults to a Chakra `<Link>`. In app code, use this to compose Chakra Link + React Router Link. */
    renderLink?: (props: { href: string; children: ReactNode }) => ReactNode;
}

function DefaultLink({ href, children }: { href: string; children: ReactNode }) {
    return (
        <Link
            href={href}
            fontSize="sm"
            color="active"
            cursor="pointer"
            _hover={{ textDecoration: "underline" }}
            display="inline-flex"
            alignItems="center"
            gap={1}
        >
            {children}
        </Link>
    );
}

export function Breadcrumbs({ items, endElement, px = 12, renderLink }: BreadcrumbsProps) {
    const LinkComponent = renderLink ?? DefaultLink;

    return (
        <Flex px={px} py={2} gap={1.5} align="center">
            {items.map((item, i) => {
                const isLast = i === items.length - 1;
                return (
                    <Flex key={item.label} align="center" gap={1.5}>
                        {i > 0 && (
                            <FaChevronRight size={10} color="var(--chakra-colors-fg-muted)" />
                        )}
                        {isLast || !item.href ? (
                            <Flex align="center" gap={1}>
                                {i === 0 && <FaHouse size={14} />}
                                <Text fontSize="sm" color={isLast ? "fg" : "fg.muted"}>
                                    {item.label}
                                </Text>
                            </Flex>
                        ) : (
                            <LinkComponent href={item.href}>
                                {i === 0 && <FaHouse size={14} />}
                                {item.label}
                            </LinkComponent>
                        )}
                    </Flex>
                );
            })}
            {endElement}
        </Flex>
    );
}

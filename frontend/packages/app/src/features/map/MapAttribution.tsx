// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, IconButton, Link, Popover, Portal, Stack, Text } from "@chakra-ui/react";
import { type ReactElement, Fragment, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { MdInfoOutline } from "react-icons/md";
import { PopoverPanel } from "@opencop/ui";
import type { AttributionPart } from "~/features/map/map-attribution.util";
import { useMapAttributions } from "~/features/map/use-map-attributions.hook";

type KeyedPart = AttributionPart & { key: string };

interface KeyedCredit {
    credit: string;
    parts: KeyedPart[];
}

/**
 * Credits for what the map shows now: always inline, truncated when they don't fit, with an (i)
 * that opens every credit in full. Renders nothing when no visible source carries attribution.
 */
export function MapAttribution(): ReactElement | null {
    const credits = useMapAttributions();
    const keyed = useMemo<KeyedCredit[]>(
        () => credits.map(({ credit, parts }) => ({ credit, parts: withContentKeys(parts) })),
        [credits],
    );
    if (keyed.length === 0) return null;
    return (
        <Flex align="center" gap="1" minW="0" flex="0 1 auto">
            <InlineCredits credits={keyed} />
            <CreditsPopover credits={keyed} />
        </Flex>
    );
}

function InlineCredits({ credits }: { credits: KeyedCredit[] }): ReactElement {
    return (
        <Text
            as="span"
            truncate
            fontSize="xs"
            lineHeight="1"
            color="fg.muted"
            data-testid="map-attribution-text"
        >
            {credits.map(({ credit, parts }, creditIndex) => (
                <Fragment key={credit}>
                    {creditIndex > 0 && " | "}
                    <CreditParts parts={parts} inline />
                </Fragment>
            ))}
        </Text>
    );
}

function CreditsPopover({ credits }: { credits: KeyedCredit[] }): ReactElement {
    const { t } = useTranslation();
    return (
        <Popover.Root positioning={{ placement: "top-end" }} lazyMount unmountOnExit>
            <Popover.Trigger asChild>
                <IconButton
                    aria-label={t("map.attribution.show")}
                    size="2xs"
                    variant="ghost"
                    color="fg.icon"
                    flexShrink={0}
                >
                    <MdInfoOutline />
                </IconButton>
            </Popover.Trigger>
            <Portal>
                <Popover.Positioner>
                    {/* Bounded and scrollable: a style can carry many long credits. Named
                        explicitly: the popover looks for its title only once, before this
                        lazily mounted content exists, so the title alone does not name it. */}
                    <PopoverPanel
                        aria-label={t("map.attribution.title")}
                        maxW="sm"
                        maxH="60vh"
                        overflowY="auto"
                        p="3"
                    >
                        <Popover.Title fontSize="xs" fontWeight="semibold" mb="2">
                            {t("map.attribution.title")}
                        </Popover.Title>
                        <Stack gap="1" fontSize="xs" color="fg.muted">
                            {credits.map(({ credit, parts }) => (
                                <Text key={credit}>
                                    <CreditParts parts={parts} />
                                </Text>
                            ))}
                        </Stack>
                    </PopoverPanel>
                </Popover.Positioner>
            </Portal>
        </Popover.Root>
    );
}

/**
 * Inline links leave the tab order: the truncating line clips a focus ring and can hide a link
 * behind its ellipsis or at zero width, and the popover lists the same links in full.
 */
function CreditParts({
    parts,
    inline = false,
}: {
    parts: KeyedPart[];
    inline?: boolean;
}): ReactElement {
    return (
        <>
            {parts.map((part) =>
                part.kind === "link" ? (
                    <Link
                        key={part.key}
                        href={part.href}
                        target="_blank"
                        rel="noopener noreferrer"
                        tabIndex={inline ? -1 : undefined}
                        color="inherit"
                        textDecoration="underline"
                    >
                        {part.text}
                    </Link>
                ) : (
                    <Fragment key={part.key}>{part.text}</Fragment>
                ),
            )}
        </>
    );
}

/** Keys a credit's parts by their content; a repeated part gets its occurrence number appended. */
function withContentKeys(parts: AttributionPart[]): KeyedPart[] {
    const seen = new Map<string, number>();
    return parts.map((part) => {
        const base = `${part.kind}:${part.kind === "link" ? part.href : ""}:${part.text}`;
        const occurrence = seen.get(base) ?? 0;
        seen.set(base, occurrence + 1);
        return { ...part, key: `${base}#${occurrence}` };
    });
}

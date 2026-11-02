// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Collapsible, Flex, Popover, Portal, Stack, Text } from "@chakra-ui/react";
import { PopoverPanel } from "@opencop/ui";
import { useState, type MouseEventHandler, type ReactElement } from "react";
import { TbChevronDown, TbChevronRight } from "react-icons/tb";
import { useTranslation } from "react-i18next";
import type { RelatedEntity } from "./data-resources.util";
import { RelatedEntityLink } from "./detail/RelatedEntityLink";

/** Height of one listed source, so the popover's scroll cap counts whole rows. */
const ROW_HEIGHT = "1.25rem";
/** Rows the popover shows before it scrolls. */
const MAX_VISIBLE_ROWS = 15;

interface UsedBySourcesProps {
    /** Active data sources using the record, shown as links to their drawers. */
    active: readonly RelatedEntity[];
    /**
     * Archived data sources using the record. They are named and counted but never linked, since
     * the Data Sources page doesn't open archived sources from a link.
     */
    archived: readonly RelatedEntity[];
    /**
     * Keeps a table row one line tall: only the first active source is linked inline, and a
     * "+N more" button opens a popover listing every source.
     */
    compact?: boolean;
    onLinkClick?: MouseEventHandler<HTMLAnchorElement>;
}

/**
 * The data sources using a mapper: active ones as links, archived ones collapsed
 * behind an "Archived (N)" toggle. In compact mode the full list lives in a popover.
 */
export function UsedBySources({
    active,
    archived,
    compact = false,
    onLinkClick,
}: UsedBySourcesProps): ReactElement {
    const { t } = useTranslation();

    if (!compact) {
        return <SourceList active={active} archived={archived} onLinkClick={onLinkClick} />;
    }

    const [first] = active;
    const hidden = active.length + archived.length - (first ? 1 : 0);
    return (
        <Flex align="center" gap={2} flexWrap="nowrap">
            {first && <RelatedEntityLink {...first} onClick={onLinkClick} />}
            {hidden > 0 && (
                <Popover.Root positioning={{ placement: "bottom-start" }} lazyMount unmountOnExit>
                    <Popover.Trigger asChild>
                        <Button
                            size="2xs"
                            variant="ghost"
                            color="fg.muted"
                            // The row opens its drawer on click; this opens only the list.
                            onClick={(e) => e.stopPropagation()}
                        >
                            {first
                                ? t("admin.usedByMore", { count: hidden })
                                : t("admin.usedByArchived", { count: archived.length })}
                        </Button>
                    </Popover.Trigger>
                    <Portal>
                        <Popover.Positioner>
                            {/* React events bubble through the portal to the row, so stop them here too. */}
                            <PopoverPanel
                                p={3}
                                aria-label={t("admin.usedBy")}
                                onClick={(e) => e.stopPropagation()}
                            >
                                <Text fontSize="xs" fontWeight="semibold" mb={1}>
                                    {t("admin.usedByCounts", {
                                        active: active.length,
                                        archived: archived.length,
                                    })}
                                </Text>
                                <SourceList
                                    active={active}
                                    archived={archived}
                                    onLinkClick={onLinkClick}
                                    scrollable
                                />
                            </PopoverPanel>
                        </Popover.Positioner>
                    </Portal>
                </Popover.Root>
            )}
        </Flex>
    );
}

interface SourceListProps {
    active: readonly RelatedEntity[];
    archived: readonly RelatedEntity[];
    onLinkClick?: MouseEventHandler<HTMLAnchorElement>;
    /** Caps the list at {@link MAX_VISIBLE_ROWS} rows and scrolls past that. */
    scrollable?: boolean;
}

function SourceList({ active, archived, onLinkClick, scrollable = false }: SourceListProps) {
    const { t } = useTranslation();
    const [showArchived, setShowArchived] = useState(false);

    return (
        <Box
            maxH={scrollable ? `calc(${MAX_VISIBLE_ROWS} * ${ROW_HEIGHT})` : undefined}
            overflowY={scrollable ? "auto" : undefined}
            pr={scrollable ? 1 : undefined}
        >
            <Stack gap={0} align="flex-start">
                {active.map((source) => (
                    <Box key={`${source.type}-${source.id}`} lineHeight={ROW_HEIGHT}>
                        <RelatedEntityLink {...source} onClick={onLinkClick} />
                    </Box>
                ))}
                {archived.length > 0 && (
                    <Collapsible.Root
                        open={showArchived}
                        onOpenChange={(details) => setShowArchived(details.open)}
                    >
                        <Collapsible.Trigger cursor="pointer" userSelect="none">
                            <Flex align="center" gap={1} color="fg.muted" lineHeight={ROW_HEIGHT}>
                                <Text as="span" fontSize="xs">
                                    {t("admin.usedByArchivedToggle", { count: archived.length })}
                                </Text>
                                {showArchived ? <TbChevronDown /> : <TbChevronRight />}
                            </Flex>
                        </Collapsible.Trigger>
                        <Collapsible.Content>
                            <Stack gap={0} align="flex-start">
                                {archived.map((source) => (
                                    <Text
                                        key={source.id}
                                        fontSize="xs"
                                        color="fg.muted"
                                        lineHeight={ROW_HEIGHT}
                                    >
                                        {t("admin.usedByArchivedName", { name: source.name })}
                                    </Text>
                                ))}
                            </Stack>
                        </Collapsible.Content>
                    </Collapsible.Root>
                )}
            </Stack>
        </Box>
    );
}

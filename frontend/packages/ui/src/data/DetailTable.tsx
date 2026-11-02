// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, SimpleGrid, Stack, Table, Text } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";

export interface DetailTableRow {
    key: string;
    label: ReactNode;
    value: ReactNode;
}

export interface DetailTableProps {
    rows: DetailTableRow[];
    /**
     * Render label/value pairs in a dense two-column grid instead of stacked,
     * full-width table rows. Useful where vertical space is scarce (e.g. a
     * drawer Overview panel). Defaults to the stacked table.
     */
    compact?: boolean;
}

function StackedRows({ rows }: { rows: DetailTableRow[] }): ReactElement {
    return (
        <Table.Root size="md" unstyled w="full">
            <Table.Body>
                {rows.map((row, i) => (
                    <Table.Row
                        borderBottomColor={i === rows.length - 1 ? "transparent" : "border"}
                        borderBottomWidth="1px"
                        key={row.key}
                        p={1}
                    >
                        <Table.Cell
                            w="1/3"
                            fontSize="xs"
                            fontWeight="bold"
                            letterSpacing="1px"
                            py="3"
                            px="3"
                            color="fg.muted"
                        >
                            {row.label}
                        </Table.Cell>
                        <Table.Cell py="1.5" px="3" wordBreak="break-all">
                            {row.value}
                        </Table.Cell>
                    </Table.Row>
                ))}
            </Table.Body>
        </Table.Root>
    );
}

function CompactRows({ rows }: { rows: DetailTableRow[] }): ReactElement {
    return (
        <SimpleGrid columns={{ base: 1, sm: 2 }} columnGap={6} rowGap={3} w="full">
            {rows.map((row) => (
                <Stack gap={0.5} key={row.key} minW={0}>
                    <Text fontSize="xs" fontWeight="bold" letterSpacing="1px" color="fg.muted">
                        {row.label}
                    </Text>
                    <Box fontSize="sm" wordBreak="break-word">
                        {row.value}
                    </Box>
                </Stack>
            ))}
        </SimpleGrid>
    );
}

export function DetailTable({ rows, compact = false }: DetailTableProps): ReactElement {
    return compact ? <CompactRows rows={rows} /> : <StackedRows rows={rows} />;
}

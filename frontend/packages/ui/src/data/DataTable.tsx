// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Skeleton, Stack, Table } from "@chakra-ui/react";
import { FaSort } from "react-icons/fa";
import { type ReactNode, useState } from "react";
import { useSpinDelay } from "spin-delay";

const SKELETON_ROW_COUNT = 6;

function SkeletonRows({ columnCount }: { columnCount: number }) {
    return Array.from({ length: SKELETON_ROW_COUNT }, (_, i) => (
        <Table.Row key={`skeleton-${i}`} bg="transparent">
            {Array.from({ length: columnCount }, (_, j) => (
                <Table.Cell key={`skeleton-${i}-${j}`} borderBottomColor="#384662">
                    <Skeleton
                        h="3.5"
                        borderRadius="sm"
                        variant="pulse"
                        bg="rgba(255,255,255,0.06)"
                    />
                </Table.Cell>
            ))}
        </Table.Row>
    ));
}

interface Column<T> {
    header: string;
    accessor: keyof T;
    render?: (value: T[keyof T], row: T) => ReactNode;
}

interface DataTableProps<T extends object> {
    columns: Column<T>[];
    data: T[];
    actions?: (row: T) => ReactNode;
    onRowClick?: (row: T) => void;
    sortable?: boolean;
    isLoading?: boolean;
    "aria-label"?: string;
    defaultSortColumn?: keyof T;
    defaultSortOrder?: "asc" | "desc";
}

export function DataTable<T extends object>({
    columns,
    data,
    actions,
    onRowClick,
    sortable = true,
    isLoading = false,
    "aria-label": ariaLabel,
    defaultSortColumn,
    defaultSortOrder = "asc",
}: DataTableProps<T>) {
    const [sortColumn, setSortColumn] = useState<keyof T | null>(defaultSortColumn ?? null);
    const [sortOrder, setSortOrder] = useState<"asc" | "desc">(defaultSortOrder);

    // Delay showing skeleton to avoid flash on fast requests; hold briefly to avoid flicker.
    const showSkeleton = useSpinDelay(isLoading, { delay: 500, minDuration: 200, ssr: false });

    // ---- Sorting ----
    const sorted = [...data];
    if (sortable && sortColumn) {
        sorted.sort((a, b) => {
            const valA = String(a[sortColumn] ?? "").toLowerCase();
            const valB = String(b[sortColumn] ?? "").toLowerCase();
            if (valA < valB) return sortOrder === "asc" ? -1 : 1;
            if (valA > valB) return sortOrder === "asc" ? 1 : -1;
            return 0;
        });
    }

    const toggleSort = (accessor: keyof T) => {
        if (!sortable) return;
        if (accessor === "actions") return;

        if (sortColumn === accessor) {
            setSortOrder(sortOrder === "asc" ? "desc" : "asc");
        } else {
            setSortColumn(accessor);
            setSortOrder("asc");
        }
    };

    // ===============================
    // 🖥 DESKTOP VIEW — Table.Root
    // ===============================
    return (
        <Box borderWidth=".5px" borderRadius="md" overflow="hidden">
            <Table.Root size="lg" variant="outline" aria-label={ariaLabel}>
                <Table.Header fontSize="sm">
                    <Table.Row>
                        {columns.map((col) => (
                            <Table.ColumnHeader
                                key={col.accessor as string}
                                bg="bg.surface"
                                color="fg.muted"
                                fontWeight="bold"
                                justifyContent="space-between"
                                cursor={
                                    sortable && col.accessor !== "actions" ? "pointer" : "default"
                                }
                                borderBottomColor={"#384662"}
                                onClick={() => toggleSort(col.accessor)}
                            >
                                <Stack direction="row" align="center">
                                    {col.header}
                                    {sortable && col.accessor !== "actions" && <FaSort size={12} />}
                                </Stack>
                            </Table.ColumnHeader>
                        ))}
                        {actions && (
                            <Table.ColumnHeader
                                color="fg.muted"
                                bg="bg.surface"
                                fontWeight="bold"
                                w="140px"
                                minW="140px"
                                borderBottomColor={"#384662"}
                            >
                                ACTIONS
                            </Table.ColumnHeader>
                        )}
                    </Table.Row>
                </Table.Header>
                <Table.Body>
                    {showSkeleton ? (
                        <SkeletonRows columnCount={columns.length + (actions ? 1 : 0)} />
                    ) : (
                        sorted.map((row, i) => (
                            <Table.Row
                                // eslint-disable-next-line @eslint-react/no-array-index-key -- generic T has no guaranteed key
                                key={i}
                                bg={i % 2 === 0 ? "bg.elevatedLight" : "bg.elevated"}
                                _hover={{
                                    bg: "bg.interactive",
                                }}
                                cursor={onRowClick ? "pointer" : "default"}
                                onClick={() => onRowClick?.(row)}
                            >
                                {columns.map((col, j) => (
                                    <Table.Cell
                                        color={j === 0 ? "fg" : "fg.muted"}
                                        fontSize={j === 0 ? "s" : "sm"}
                                        borderBottomColor={"#384662"}
                                        key={col.accessor as string}
                                    >
                                        {col.render
                                            ? col.render(row[col.accessor], row)
                                            : String(row[col.accessor] ?? "-")}
                                    </Table.Cell>
                                ))}
                                {actions && (
                                    <Table.Cell
                                        onClick={(e) => e.stopPropagation()}
                                        w="140px"
                                        minW="140px"
                                        borderBottomColor={"#384662"}
                                    >
                                        <Stack direction="row" gap={2}>
                                            {actions(row)}
                                        </Stack>
                                    </Table.Cell>
                                )}
                            </Table.Row>
                        ))
                    )}
                </Table.Body>
            </Table.Root>
        </Box>
    );
}

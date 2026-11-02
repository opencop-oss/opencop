// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Button,
    Checkbox,
    Flex,
    IconButton,
    Menu,
    Separator,
    Skeleton,
    Spinner,
    Table,
    Text,
} from "@chakra-ui/react";
import {
    type ColumnDef,
    type ColumnFiltersState,
    type ColumnSizingState,
    flexRender,
    getCoreRowModel,
    getFilteredRowModel,
    getSortedRowModel,
    type OnChangeFn,
    type RowSelectionState,
    type SortingState,
    useReactTable,
    type VisibilityState,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import { ocFilterFns } from "./oc-filter-fns.ts";
import {
    Fragment,
    type ReactElement,
    type ReactNode,
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import { useTranslation } from "react-i18next";
import { FaChevronDown, FaEllipsisH, FaSort, FaSortDown, FaSortUp, FaTimes } from "react-icons/fa";
import { useSpinDelay } from "spin-delay";
import { ConfirmDialog } from "../overlays/ConfirmDialog.tsx";
import { RefreshButton } from "../buttons/RefreshButton.tsx";
import { Tooltip } from "../overlays/Tooltip.tsx";
import { ColumnPicker } from "./ColumnPicker.tsx";
import { SearchInput } from "./SearchInput.tsx";

const SKELETON_ROW_COUNT = 6;
const VIRTUAL_ROW_HEIGHT = 40;
const VIRTUAL_FETCH_THRESHOLD = 8;

export interface ToolbarMenuItem {
    label: string;
    icon?: ReactElement;
    onClick: () => void;
}

export interface ToolbarActionConfirm {
    title: string;
    message: string | ReactNode;
    confirmLabel?: string;
    /** When false, the confirm button uses neutral styling instead of red. Default: true. */
    isDestructive?: boolean;
}

export interface ToolbarAction {
    /** Stable React key for an action whose `label` changes with state, so its button isn't remounted (and focus lost) on toggle. Defaults to `label`. */
    id?: string;
    label: string;
    icon: ReactElement;
    onClick?: () => void;
    colorPalette?: string;
    disabled?: boolean;
    /** When disabled, a tooltip explaining why (shown even on the disabled control). */
    disabledReason?: string;
    /** Longer hover text for a selection action whose short `label` needs explaining. */
    tooltip?: string;
    /** Show the label text next to the icon instead of icon-only with tooltip. */
    showLabel?: boolean;
    menuItems?: ToolbarMenuItem[];
    confirm?: ToolbarActionConfirm;
}

export interface ResourceTableToolbar {
    onRefresh?: () => Promise<unknown>;
    isRefreshing?: boolean;
    /**
     * Custom controls placed at the head of the toolbar row, ahead of `actions`.
     * For a control the `ToolbarAction` descriptor cannot express — a select, or a
     * button with its own loading state. Putting it here rather than above the
     * table keeps it on one line with the search box instead of costing a row.
     */
    leading?: ReactNode;
    actions?: ToolbarAction[];
    selectionActions?: ToolbarAction[];
}

export interface ResourceTableProps<T extends object> {
    data: T[];
    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- ColumnDef is invariant in the value type, so `unknown` rejects typed accessors from createColumnHelper; `any` is TanStack's recommended workaround
    columns: ColumnDef<T, any>[];
    isLoading?: boolean;
    isFetching?: boolean;
    enableRowSelection?: boolean | ((row: T) => boolean);
    enableColumnResizing?: boolean;
    enableSorting?: boolean;
    onRowClick?: (row: T) => void;
    getRowId?: (row: T) => string;
    globalFilter?: string;
    onGlobalFilterChange?: (value: string) => void;
    /** Overrides the search box placeholder (e.g. to hint how the server matches the term). */
    searchPlaceholder?: string;
    columnFilters?: ColumnFiltersState;
    onColumnFiltersChange?: OnChangeFn<ColumnFiltersState>;
    rowSelection?: RowSelectionState;
    onRowSelectionChange?: OnChangeFn<RowSelectionState>;
    /**
     * How many rows are really selected, when that differs from the key count of `rowSelection`.
     * A consumer that retains rows as they are picked knows the true figure for rows the table can
     * no longer see; without it the toolbar would report only what is currently resolvable.
     */
    selectedCount?: number;
    toolbar?: ResourceTableToolbar;
    filterBar?: ReactNode;
    detailDrawer?: ReactNode;
    "aria-label"?: string;

    // Server-side / infinite-scroll mode
    /** rows arrive already sorted by the server. */
    manualSorting?: boolean;
    manualFiltering?: boolean;
    /** Controlled sorting state. When omitted, sorting is managed internally. */
    sorting?: SortingState;
    onSortingChange?: OnChangeFn<SortingState>;
    /** Called when the scroll sentinel is reached; wire to React Query's `fetchNextPage`. */
    onLoadMore?: () => void;
    hasNextPage?: boolean;
    /** Whether the next page is currently loading (drives the footer spinner, blocks re-fetch). */
    isFetchingNextPage?: boolean;
    /** True when the (initial or next-page) fetch failed. Surfaces an error/retry state instead of a
     *  misleading empty state, and stops the scroll effect from re-firing the failed request. */
    isError?: boolean;
    /** Retry handler for the error state (wire to the query's fetchNextPage/refetch). */
    onRetry?: () => void;
    /**
     * Makes the scrolling rows a named, focusable region, so a keyboard reader can scroll them
     * without a sortable header focused. Without it the scroller is not a tab stop.
     */
    scrollRegionLabel?: string;
    /**
     * Controlled column visibility; a column absent from the map is visible. Opting in (by passing
     * both this and `onColumnVisibilityChange`) adds a **Columns** picker to the toolbar, which only
     * renders when `toolbar` is set. Columns with `enableHiding: false` are never listed. Hiding a
     * column drops it from the sort; its filters keep applying.
     */
    columnVisibility?: VisibilityState;
    onColumnVisibilityChange?: OnChangeFn<VisibilityState>;
    /** Shows every column again; the picker omits its Select all button when absent. */
    onResetColumnVisibility?: () => void;
    /** Total server-side row count. When provided, an "X of N" footer is shown. */
    totalCount?: number;
    /**
     * Overrides the empty-state text. Use it to distinguish "nothing matched your filters" from
     * "this list is empty", which otherwise read identically to someone who just applied a filter.
     */
    emptyMessage?: string;
    /**
     * Changing this value scrolls the virtualized body back to the first row. Wire it to the
     * server query's identity (search + sort + filters). Without it, `keepPreviousData` holds the
     * old scroll offset and the browser clamps it to the shorter result's maximum, stranding the
     * user near the bottom of the new list.
     */
    resetScrollKey?: string | number;
    /** Render with a transparent table surface so it blends into a colored container (e.g. a drawer). */
    transparent?: boolean;
    /**
     * Viewer's IANA zone, used to resolve the day boundaries of client-side date filters. Set it
     * wherever a date column is filterable, so a filtered day means the day the cell renders —
     * the same contract the server-paged grids get by sending `tz`. Defaults to UTC.
     */
    timezone?: string;
}

/** The props that turn the scrolling rows into a named, keyboard-reachable region. */
function scrollRegionProps(label: string) {
    return {
        role: "region",
        "aria-label": label,
        tabIndex: 0,
        _focusVisible: {
            outlineWidth: "2px",
            outlineStyle: "solid",
            outlineColor: "brand.focusRing",
            outlineOffset: "-2px",
        },
    } as const;
}

function ToolbarActionButton({ action }: { action: ToolbarAction }) {
    const directClick = action.onClick;
    const tooltip = action.label;
    const showReason = action.disabled === true && action.disabledReason != null;

    // A disabled control emits no pointer events and can't take focus, so the reason hangs on a
    // focusable wrapper — same pattern as SelectionActionButton.
    const withReason = (control: ReactElement) =>
        showReason ? (
            <Tooltip content={action.disabledReason} uppercase={false}>
                <Box as="span" tabIndex={0} display="inline-flex">
                    {control}
                </Box>
            </Tooltip>
        ) : (
            control
        );

    // action.onClick is intentionally unused when menuItems is set — the menu handles interaction.
    // Do not pass both: onClick is silently discarded and callers won't get a runtime error.
    if (action.menuItems && action.menuItems.length >= 1) {
        return withReason(
            <Menu.Root positioning={{ placement: "bottom-start" }}>
                <Menu.Trigger asChild>
                    {action.showLabel ? (
                        <Button size="xs" variant="toolbar" disabled={action.disabled}>
                            {action.icon}
                            {action.label}
                            <FaChevronDown size={10} />
                        </Button>
                    ) : (
                        <IconButton
                            aria-label={action.label}
                            size="xs"
                            variant="toolbar"
                            disabled={action.disabled}
                        >
                            {action.icon}
                        </IconButton>
                    )}
                </Menu.Trigger>
                <Menu.Positioner>
                    <Menu.Content>
                        {action.menuItems.map((item) => (
                            <Menu.Item key={item.label} value={item.label} onClick={item.onClick}>
                                {item.icon && <Box mr={2}>{item.icon}</Box>}
                                {item.label}
                            </Menu.Item>
                        ))}
                    </Menu.Content>
                </Menu.Positioner>
            </Menu.Root>,
        );
    }

    if (action.showLabel) {
        return withReason(
            <Button size="xs" variant="toolbar" onClick={directClick} disabled={action.disabled}>
                {action.icon}
                {action.label}
            </Button>,
        );
    }

    const iconButton = (
        <IconButton
            aria-label={tooltip}
            size="xs"
            variant="toolbar"
            onClick={directClick}
            disabled={action.disabled}
        >
            {action.icon}
        </IconButton>
    );

    // Icon-only: the reason displaces the label tooltip rather than nesting inside it — a disabled
    // control's "why" is more use than repeating the name already given by `aria-label`.
    return showReason ? withReason(iconButton) : <Tooltip content={tooltip}>{iconButton}</Tooltip>;
}

/**
 * A selection-toolbar action button (icon + label). When disabled with a
 * `disabledReason`, it's wrapped in a tooltip + `<span>` so the "why" still shows
 * on hover — disabled controls emit no pointer events of their own.
 */
function SelectionActionButton({
    action,
    onActionClick,
}: {
    action: ToolbarAction;
    onActionClick: (action: ToolbarAction) => void;
}) {
    const button = (
        <Button
            size="xs"
            variant="toolbar"
            aria-description={action.tooltip}
            onClick={() => onActionClick(action)}
            disabled={action.disabled}
        >
            {action.icon}
            {action.label}
        </Button>
    );
    const disabledReason = action.disabled ? action.disabledReason : undefined;
    const hint = disabledReason ?? action.tooltip;
    if (!hint) return button;
    // A disabled control emits no pointer events and can't take focus, so its tooltip hangs on a
    // focusable wrapper.
    return (
        <Tooltip content={hint} uppercase={false}>
            {action.disabled ? (
                <Box as="span" tabIndex={0} display="inline-flex">
                    {button}
                </Box>
            ) : (
                button
            )}
        </Tooltip>
    );
}

function OverflowMenuButton({
    actions,
    onItemClick,
}: {
    actions: ToolbarAction[];
    onItemClick?: (action: ToolbarAction) => void;
}) {
    return (
        <Menu.Root positioning={{ placement: "bottom-start" }}>
            <Menu.Trigger asChild>
                <Button size="xs" variant="toolbar">
                    <FaEllipsisH />
                </Button>
            </Menu.Trigger>
            <Menu.Positioner>
                <Menu.Content>
                    {actions.map((action, idx) => {
                        if (action.menuItems?.length) {
                            return (
                                <Fragment key={action.label}>
                                    {idx > 0 && <Menu.Separator />}
                                    <Menu.ItemGroup>
                                        <Menu.ItemGroupLabel>{action.label}</Menu.ItemGroupLabel>
                                        {action.menuItems.map((item) => (
                                            <Menu.Item
                                                key={item.label}
                                                value={item.label}
                                                onClick={item.onClick}
                                            >
                                                {item.icon && <Box mr={2}>{item.icon}</Box>}
                                                {item.label}
                                            </Menu.Item>
                                        ))}
                                    </Menu.ItemGroup>
                                </Fragment>
                            );
                        }
                        return (
                            <Menu.Item
                                key={action.id ?? action.label}
                                value={action.label}
                                title={
                                    (action.disabled ? action.disabledReason : undefined) ??
                                    action.tooltip
                                }
                                onClick={onItemClick ? () => onItemClick(action) : action.onClick}
                                disabled={action.disabled}
                            >
                                {action.icon && <Box mr={2}>{action.icon}</Box>}
                                {action.label}
                            </Menu.Item>
                        );
                    })}
                </Menu.Content>
            </Menu.Positioner>
        </Menu.Root>
    );
}

function ToolbarSection({
    toolbar,
    filterBar,
    selectedCount,
    totalCount,
    onActionClick,
    onClearSelection,
    globalFilter,
    onGlobalFilterChange,
    searchPlaceholder,
    columnPicker,
}: {
    toolbar: ResourceTableToolbar;
    columnPicker?: ReactNode;
    filterBar?: ReactNode;
    selectedCount: number;
    /**
     * Total rows matching the current filter (server-paged tables only). Rendered as
     * "N of M selected" so it stays visible that select-all covers the *loaded* rows,
     * not every match — with infinite scroll the two differ until everything is paged in.
     */
    totalCount?: number;
    onActionClick: (action: ToolbarAction) => void;
    onClearSelection: () => void;
    globalFilter?: string;
    onGlobalFilterChange?: (value: string) => void;
    searchPlaceholder?: string;
}) {
    const { t } = useTranslation();
    const hasPrimary =
        !!toolbar.onRefresh ||
        (toolbar.actions?.length ?? 0) > 0 ||
        toolbar.leading != null ||
        columnPicker != null;
    const hasSelectionActions = (toolbar.selectionActions?.length ?? 0) > 0;

    const toolbarRef = useRef<HTMLDivElement>(null);
    const leftSideRef = useRef<HTMLDivElement>(null);
    const selectionMeasureRef = useRef<HTMLDivElement>(null);
    const selectedCountBlockRef = useRef<HTMLDivElement>(null);
    const [isSelectionOverflowed, setIsSelectionOverflowed] = useState(false);

    // 1px separator line + mx={0.5} (2px each side) + surrounding flex gap={1.5} (6px each side) ≈ 17px; 20 adds a small buffer
    const SEPARATOR_WIDTH = 20;

    useEffect(() => {
        const toolbar = toolbarRef.current;
        if (!toolbar) return;
        let rafId = 0;
        const check = () => {
            cancelAnimationFrame(rafId);
            rafId = requestAnimationFrame(() => {
                const left = leftSideRef.current;
                const measure = selectionMeasureRef.current;
                if (!left || !measure || !toolbarRef.current) return;
                const selectedCountWidth = selectedCountBlockRef.current?.offsetWidth ?? 0;
                setIsSelectionOverflowed(
                    left.offsetWidth + SEPARATOR_WIDTH + measure.scrollWidth + selectedCountWidth >
                        toolbarRef.current.clientWidth,
                );
            });
        };
        const obs = new ResizeObserver(check);
        obs.observe(toolbar);
        check();
        return () => {
            obs.disconnect();
            cancelAnimationFrame(rafId);
        };
    }, []);

    return (
        <Box position="relative">
            {/* Hidden measurement — always renders selection actions at natural size */}
            {hasSelectionActions && (
                <Flex
                    ref={selectionMeasureRef}
                    position="absolute"
                    visibility="hidden"
                    pointerEvents="none"
                    aria-hidden="true"
                    gap={1.5}
                    top={0}
                    left={0}
                >
                    {toolbar.selectionActions!.map((action) => (
                        <Button
                            key={action.id ?? action.label}
                            size="xs"
                            variant="toolbar"
                            disabled={action.disabled}
                        >
                            {action.icon}
                            {action.label}
                        </Button>
                    ))}
                </Flex>
            )}

            <Flex ref={toolbarRef} align="center" gap={1.5} py={1.5}>
                {/* Left side — always visible, never collapses */}
                <Flex ref={leftSideRef} align="center" flexShrink={0} gap={1.5}>
                    {toolbar.leading}
                    {toolbar.actions?.map((action) => (
                        <ToolbarActionButton key={action.id ?? action.label} action={action} />
                    ))}
                    {columnPicker}
                    {toolbar.onRefresh && (
                        <RefreshButton
                            onRefresh={toolbar.onRefresh}
                            isRefreshing={toolbar.isRefreshing}
                            size="xs"
                            showLabel
                        />
                    )}
                </Flex>

                {/* Separator + right side: selection actions collapse when narrow */}
                {hasSelectionActions && (
                    <>
                        {hasPrimary && <Separator orientation="vertical" h="5" mx={0.5} />}
                        {isSelectionOverflowed ? (
                            <OverflowMenuButton
                                actions={toolbar.selectionActions!}
                                onItemClick={onActionClick}
                            />
                        ) : (
                            toolbar.selectionActions!.map((action) => (
                                <SelectionActionButton
                                    key={action.id ?? action.label}
                                    action={action}
                                    onActionClick={onActionClick}
                                />
                            ))
                        )}
                    </>
                )}

                {/* Clear selection — always outside overflow */}
                {selectedCount > 0 && (
                    <Flex ref={selectedCountBlockRef} align="center" gap={1.5}>
                        <Separator orientation="vertical" h="5" mx={0.5} />
                        <Text fontSize="xs" color="fg.muted" whiteSpace="nowrap">
                            {totalCount != null
                                ? t("resourceTable.selectedOfTotal", {
                                      selected: selectedCount,
                                      total: totalCount,
                                  })
                                : t("resourceTable.selectedCount", { selected: selectedCount })}
                        </Text>
                        <Tooltip content={t("resourceTable.clearSelection")}>
                            <IconButton
                                aria-label={t("resourceTable.clearSelection")}
                                size="2xs"
                                variant="toolbar"
                                onClick={onClearSelection}
                            >
                                <FaTimes />
                            </IconButton>
                        </Tooltip>
                    </Flex>
                )}

                {!filterBar && onGlobalFilterChange && (
                    // flex-basis 240 but shrinkable to minW so the field never
                    // pushes past the toolbar edge when selection actions fill the row.
                    <Box ms="auto" flex="0 1 240px" minW="120px">
                        <SearchInput
                            value={globalFilter ?? ""}
                            onChange={onGlobalFilterChange}
                            placeholder={searchPlaceholder}
                            size="sm"
                            fill
                        />
                    </Box>
                )}
            </Flex>
            {filterBar && (
                <>
                    <Separator />
                    <Box pt={2.5} pb={1.5}>
                        {filterBar}
                    </Box>
                </>
            )}
        </Box>
    );
}

function SkeletonRows({ columnCount }: { columnCount: number }) {
    return Array.from({ length: SKELETON_ROW_COUNT }, (_, i) => (
        <Table.Row key={`skeleton-${i}`} bg="transparent">
            {Array.from({ length: columnCount }, (_, j) => (
                <Table.Cell key={`skeleton-${i}-${j}`} py={1.5} borderBottomColor="#384662">
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

function EmptyRow({ columnCount, message }: { columnCount: number; message?: string }) {
    const { t } = useTranslation();
    return (
        <Table.Row borderBottom="none">
            <Table.Cell colSpan={columnCount} h="120px" textAlign="center" borderBottom="none">
                <Text fontSize="sm" color="fg.muted">
                    {message ?? t("resourceTable.noResults")}
                </Text>
            </Table.Cell>
        </Table.Row>
    );
}

function ErrorRow({ columnCount, onRetry }: { columnCount: number; onRetry?: () => void }) {
    const { t } = useTranslation();
    return (
        <Table.Row borderBottom="none">
            <Table.Cell colSpan={columnCount} h="120px" textAlign="center" borderBottom="none">
                <Flex direction="column" align="center" gap={2}>
                    <Text fontSize="sm" color="fg.muted">
                        {t("resourceTable.loadError")}
                    </Text>
                    {onRetry && (
                        <Button size="xs" variant="toolbar" onClick={onRetry}>
                            {t("resourceTable.retry")}
                        </Button>
                    )}
                </Flex>
            </Table.Cell>
        </Table.Row>
    );
}

function SortableColumnHeader({
    header,
    enableColumnResizing,
}: {
    header: import("@tanstack/react-table").Header<unknown, unknown>;
    enableColumnResizing: boolean;
}) {
    const canSort = header.column.getCanSort();
    const sorted = header.column.getIsSorted();
    const toggleSorting = header.column.getToggleSortingHandler();

    // A <th> is neither focusable nor click-activated by Enter/Space, so onClick alone would leave
    // sorting mouse-only; tabIndex + onKeyDown restore it. The header keeps its implicit
    // `columnheader` role on purpose — overriding it with `role="button"` drops the cell out of the
    // table's semantics and makes aria-sort (only valid on columnheader/rowheader) inert.
    return (
        <Table.ColumnHeader
            bg="bg"
            color="fg.muted"
            fontWeight="bold"
            borderBottomColor="#384662"
            position="sticky"
            top={0}
            zIndex={1}
            py={1.5}
            px={2}
            cursor={canSort ? "pointer" : "default"}
            onClick={toggleSorting}
            tabIndex={canSort ? 0 : undefined}
            aria-sort={
                canSort
                    ? sorted === "asc"
                        ? "ascending"
                        : sorted === "desc"
                          ? "descending"
                          : "none"
                    : undefined
            }
            onKeyDown={
                canSort
                    ? (event) => {
                          if (event.key !== "Enter" && event.key !== " ") return;
                          // Space would otherwise scroll the table body.
                          event.preventDefault();
                          toggleSorting?.(event);
                      }
                    : undefined
            }
            w={`${header.getSize()}px`}
        >
            <Flex align="center" gap={1}>
                {header.isPlaceholder
                    ? null
                    : flexRender(header.column.columnDef.header, header.getContext())}
                {canSort &&
                    (sorted === "asc" ? (
                        <FaSortUp size={10} />
                    ) : sorted === "desc" ? (
                        <FaSortDown size={10} />
                    ) : (
                        <FaSort size={10} />
                    ))}
            </Flex>
            {enableColumnResizing && header.column.getCanResize() && (
                <Box
                    onMouseDown={header.getResizeHandler()}
                    onTouchStart={header.getResizeHandler()}
                    onClick={(e) => e.stopPropagation()}
                    position="absolute"
                    right={0}
                    top={0}
                    bottom={0}
                    w="4px"
                    cursor="col-resize"
                    bg={header.column.getIsResizing() ? "active" : "transparent"}
                    _hover={{ bg: "border" }}
                />
            )}
        </Table.ColumnHeader>
    );
}

export function ResourceTable<T extends object>({
    data,
    columns,
    isLoading = false,
    isFetching = false,
    enableRowSelection = false,
    enableColumnResizing = true,
    enableSorting = true,
    onRowClick,
    getRowId,
    globalFilter,
    onGlobalFilterChange,
    searchPlaceholder,
    columnFilters,
    onColumnFiltersChange,
    rowSelection,
    onRowSelectionChange,
    selectedCount: selectedCountProp,
    toolbar,
    filterBar,
    detailDrawer,
    "aria-label": ariaLabel,
    manualSorting = false,
    manualFiltering = false,
    sorting,
    onSortingChange,
    onLoadMore,
    hasNextPage = false,
    isFetchingNextPage = false,
    isError = false,
    onRetry,
    totalCount,
    scrollRegionLabel,
    columnVisibility,
    onColumnVisibilityChange,
    onResetColumnVisibility,
    emptyMessage,
    resetScrollKey,
    transparent = false,
    timezone,
}: ResourceTableProps<T>) {
    // TODO: Remove "use no memo" when TanStack Table v9 stable ships with React Compiler support.
    // See https://github.com/TanStack/table/issues/6137
    "use no memo";
    const { t } = useTranslation();
    const [internalSorting, setInternalSorting] = useState<SortingState>([]);
    const sortingState = sorting ?? internalSorting;
    const handleSortingChange = onSortingChange ?? setInternalSorting;
    const [columnSizing, setColumnSizing] = useState<ColumnSizingState>({});

    // Delay showing skeleton to avoid flash on fast requests; if shown, hold briefly to avoid flicker.
    // ssr: false prevents spin-delay from bypassing the delay on initial render when isLoading starts true.
    const showSkeleton = useSpinDelay(isLoading, { delay: 500, minDuration: 200, ssr: false });

    const selectedCount =
        selectedCountProp ?? (rowSelection ? Object.keys(rowSelection).length : 0);

    // Bound to the table rather than to each column, so a date column only names "ocDateFilter"
    // and still resolves its day boundaries in the zone its cells render in.
    const filterFns = useMemo(() => ocFilterFns(timezone), [timezone]);

    const [pendingConfirmAction, setPendingConfirmAction] = useState<ToolbarAction | null>(null);

    const handleActionClick = useCallback((action: ToolbarAction) => {
        if (action.confirm) {
            setPendingConfirmAction(action);
        } else {
            action.onClick?.();
        }
    }, []);

    const handleConfirm = useCallback(() => {
        pendingConfirmAction?.onClick?.();
        setPendingConfirmAction(null);
    }, [pendingConfirmAction]);

    const handleClearSelection = useCallback(() => {
        onRowSelectionChange?.({} as RowSelectionState);
    }, [onRowSelectionChange]);

    // Scroll container for the infinite-scroll body. The row virtualizer (created after the table
    // below) both windows the rows and drives next-page fetching from scroll position.
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    // Shift-click range selection: the checkbox's onCheckedChange doesn't expose
    // the native event, so we capture shiftKey in onClick (which does) and record
    // the last-toggled row as the anchor for the next shift-click.
    const lastToggledIndexRef = useRef<number | null>(null);
    const shiftHeldRef = useRef(false);

    const allColumns: ColumnDef<T, unknown>[] = enableRowSelection
        ? [
              {
                  id: "_select",
                  enableHiding: false,
                  size: 32,
                  minSize: 32,
                  maxSize: 32,
                  enableResizing: false,
                  enableSorting: false,
                  header: ({ table }) => (
                      <Checkbox.Root
                          size="sm"
                          checked={
                              table.getIsAllRowsSelected()
                                  ? true
                                  : table.getIsSomeRowsSelected()
                                    ? "indeterminate"
                                    : false
                          }
                          onCheckedChange={() => table.toggleAllRowsSelected()}
                          onClick={(e) => e.stopPropagation()}
                      >
                          <Checkbox.HiddenInput />
                          <Checkbox.Control borderColor="slate.icon.primary" borderWidth="1.5px" />
                      </Checkbox.Root>
                  ),
                  cell: ({ row, table }) => (
                      <Checkbox.Root
                          size="sm"
                          checked={row.getIsSelected()}
                          onCheckedChange={(details) => {
                              const checked = details.checked === true;
                              const rows = table.getRowModel().rows;
                              // Display order (respects sort/filter), not row.index.
                              const idx = rows.findIndex((r) => r.id === row.id);
                              const anchor = lastToggledIndexRef.current;
                              if (shiftHeldRef.current && anchor != null && anchor !== idx) {
                                  // Apply the clicked row's new state across the range.
                                  // Delete keys on deselect rather than writing `false`:
                                  // consumers read bare Object.keys(rowSelection), so a
                                  // lingering `false` would still count the row as selected.
                                  const [lo, hi] = anchor < idx ? [anchor, idx] : [idx, anchor];
                                  table.setRowSelection((prev) => {
                                      const next = { ...prev };
                                      for (let i = lo; i <= hi; i++) {
                                          const r = rows[i];
                                          if (!r?.getCanSelect()) continue;
                                          if (checked) next[r.id] = true;
                                          else delete next[r.id];
                                      }
                                      return next;
                                  });
                              } else {
                                  row.toggleSelected(checked);
                              }
                              lastToggledIndexRef.current = idx;
                              shiftHeldRef.current = false;
                          }}
                          onClick={(e) => {
                              e.stopPropagation();
                              shiftHeldRef.current = e.shiftKey;
                          }}
                          disabled={!row.getCanSelect()}
                      >
                          <Checkbox.HiddenInput />
                          <Checkbox.Control borderColor="slate.icon.primary" borderWidth="1.5px" />
                      </Checkbox.Root>
                  ),
              },
              ...columns,
          ]
        : columns;

    // A hidden column leaves no header to clear its sort from, so the sort goes with it.
    const handleColumnVisibilityChange: OnChangeFn<VisibilityState> | undefined =
        onColumnVisibilityChange &&
        ((updater) => {
            const current = columnVisibility ?? {};
            const next = typeof updater === "function" ? updater(current) : updater;
            if (sortingState.some((s) => next[s.id] === false)) {
                handleSortingChange(sortingState.filter((s) => next[s.id] !== false));
            }
            onColumnVisibilityChange(next);
        });

    // eslint-disable-next-line react-hooks/incompatible-library -- acknowledged via "use no memo" above
    const table = useReactTable<T>({
        data,
        columns: allColumns,
        state: {
            sorting: sortingState,
            columnSizing,
            globalFilter,
            ...(rowSelection != null ? { rowSelection } : {}),
            ...(columnFilters != null ? { columnFilters } : {}),
            ...(columnVisibility != null ? { columnVisibility } : {}),
        },
        onColumnVisibilityChange: handleColumnVisibilityChange,
        columnResizeMode: enableColumnResizing ? "onChange" : undefined,
        enableRowSelection:
            typeof enableRowSelection === "function"
                ? (row) => enableRowSelection(row.original)
                : enableRowSelection,
        enableSorting,
        manualSorting,
        manualFiltering,
        onSortingChange: handleSortingChange,
        onColumnSizingChange: setColumnSizing,
        onGlobalFilterChange,
        onColumnFiltersChange,
        onRowSelectionChange,
        getRowId,
        filterFns,
        getCoreRowModel: getCoreRowModel(),
        getSortedRowModel: getSortedRowModel(),
        getFilteredRowModel: getFilteredRowModel(),
    });

    const columnPicker =
        columnVisibility != null && handleColumnVisibilityChange ? (
            <ColumnPicker
                items={table
                    .getAllLeafColumns()
                    .filter(
                        (column) => column.getCanHide() && !column.columnDef.meta?.isActionsColumn,
                    )
                    .map((column) => ({
                        id: column.id,
                        label:
                            column.columnDef.meta?.columnLabel ??
                            (typeof column.columnDef.header === "string"
                                ? column.columnDef.header
                                : column.id),
                        visible: column.getIsVisible(),
                    }))}
                onToggle={(id, visible) => table.getColumn(id)?.toggleVisibility(visible)}
                onReset={onResetColumnVisibility}
            />
        ) : undefined;

    const visibleColumnCount = table.getVisibleLeafColumns().length;

    const rows = table.getRowModel().rows;

    // Virtualize the infinite-scroll (server) path: render only a windowed subset of fixed-height
    // rows so large lists scroll smoothly. Gated to infinite mode (onLoadMore != null); client-side
    // tables keep the plain render below. Because rows are a fixed VIRTUAL_ROW_HEIGHT, getTotalSize()
    // is exact and the scrollbar grows in clean steps as pages load (no re-jump). Windowing uses top/
    // bottom spacer rows so the native Chakra <table> — sticky header, column widths, selection — is
    // preserved; table-layout:fixed makes the widths authoritative and lets long cells truncate.
    const virtualized = onLoadMore != null;
    const rowVirtualizer = useVirtualizer({
        count: virtualized ? rows.length : 0,
        getScrollElement: () => scrollContainerRef.current,
        estimateSize: () => VIRTUAL_ROW_HEIGHT,
        overscan: 8,
    });
    // Seeded with the current key so mount is a no-op and never fights a restored scroll position.
    const previousResetScrollKeyRef = useRef(resetScrollKey);
    useEffect(() => {
        if (previousResetScrollKeyRef.current === resetScrollKey) return;
        previousResetScrollKeyRef.current = resetScrollKey;
        rowVirtualizer.scrollToOffset(0);
    }, [resetScrollKey, rowVirtualizer]);

    const virtualItems = rowVirtualizer.getVirtualItems();
    const virtualPadTop = virtualItems.length > 0 ? virtualItems[0].start : 0;
    const virtualPadBottom =
        virtualItems.length > 0
            ? rowVirtualizer.getTotalSize() - virtualItems[virtualItems.length - 1].end
            : 0;

    // Virtualizer-driven infinite fetch: request the next page when the window nears the end of the
    // loaded rows. hasNextPage + isFetchingNextPage prevent duplicate/at-end fetches; a short first
    // page keeps fetching until the viewport fills (each loaded page grows rows.length and re-arms).
    useEffect(() => {
        if (!onLoadMore) return;
        // Don't auto-refire after a failed fetch — hasNextPage stays true (derived from the last
        // successful page), so without this guard the scroll effect would loop on the failing
        // request. The error state surfaces a manual Retry instead.
        if (isError) return;
        // An empty window (rows.length === 0) has no last item to anchor on, so the check below
        // never fires even while hasNextPage is true. That state is reachable in normal use: the
        // drawer pickers filter already-assigned users out of each server page client-side, so a
        // fully-assigned first page arrives empty. Without this guard the picker dead-ends with
        // more pages available — keep paging until a displayable row appears or the server runs out.
        if (rows.length === 0) {
            if (hasNextPage && !isFetchingNextPage) onLoadMore();
            return;
        }
        const last = virtualItems[virtualItems.length - 1];
        if (
            last &&
            last.index >= rows.length - 1 - VIRTUAL_FETCH_THRESHOLD &&
            hasNextPage &&
            !isFetchingNextPage
        ) {
            onLoadMore();
        }
    }, [virtualItems, rows.length, hasNextPage, isFetchingNextPage, isError, onLoadMore]);

    return (
        <>
            <Flex direction="column" flex="1" minH={0}>
                {toolbar && (
                    <ToolbarSection
                        toolbar={toolbar}
                        filterBar={filterBar}
                        selectedCount={selectedCount}
                        totalCount={totalCount}
                        onActionClick={handleActionClick}
                        onClearSelection={handleClearSelection}
                        globalFilter={globalFilter}
                        onGlobalFilterChange={onGlobalFilterChange}
                        searchPlaceholder={searchPlaceholder}
                        columnPicker={columnPicker}
                    />
                )}

                <Box
                    ref={scrollContainerRef}
                    flex="1"
                    position="relative"
                    overflowY="auto"
                    minH={0}
                    {...(scrollRegionLabel != null && scrollRegionProps(scrollRegionLabel))}
                >
                    {isFetching && !isLoading && (
                        <Box
                            position="absolute"
                            top={0}
                            left={0}
                            right={0}
                            h="2px"
                            bg="brand.500"
                            zIndex={2}
                            animation="pulse 1.5s ease-in-out infinite"
                        />
                    )}
                    <Table.Root
                        size="sm"
                        variant="outline"
                        borderBottomWidth="0"
                        borderCollapse="separate"
                        borderSpacing={0}
                        bg={transparent ? "transparent" : undefined}
                        // With row selection, make the grid non-text-selectable: selecting
                        // rows is done via checkboxes, and otherwise clicking / shift-clicking
                        // a checkbox drags a text highlight across the intervening rows (the
                        // checkbox alone being user-select:none isn't enough — the name/role
                        // cells stay selectable). It also lets a shift-click activate the box
                        // instead of extending a text range. Non-selection tables keep text
                        // selectable so values can still be copied.
                        userSelect={enableRowSelection ? "none" : undefined}
                        aria-busy={showSkeleton}
                        aria-label={ariaLabel}
                        css={virtualized ? { tableLayout: "fixed", width: "100%" } : undefined}
                    >
                        <Table.Header fontSize="xs" textTransform="uppercase">
                            {table.getHeaderGroups().map((headerGroup) => (
                                <Table.Row key={headerGroup.id}>
                                    {headerGroup.headers.map((header) => (
                                        <SortableColumnHeader
                                            key={header.id}
                                            header={
                                                header as import("@tanstack/react-table").Header<
                                                    unknown,
                                                    unknown
                                                >
                                            }
                                            enableColumnResizing={enableColumnResizing}
                                        />
                                    ))}
                                </Table.Row>
                            ))}
                        </Table.Header>
                        <Table.Body>
                            {showSkeleton ? (
                                <SkeletonRows columnCount={visibleColumnCount} />
                            ) : rows.length === 0 ? (
                                isError ? (
                                    <ErrorRow columnCount={visibleColumnCount} onRetry={onRetry} />
                                ) : (
                                    <EmptyRow
                                        columnCount={visibleColumnCount}
                                        message={emptyMessage}
                                    />
                                )
                            ) : virtualized ? (
                                <>
                                    {virtualPadTop > 0 && (
                                        <Table.Row aria-hidden="true">
                                            <Table.Cell
                                                colSpan={visibleColumnCount}
                                                css={{
                                                    height: `${virtualPadTop}px`,
                                                    padding: 0,
                                                    borderBottom: "none",
                                                }}
                                            />
                                        </Table.Row>
                                    )}
                                    {virtualItems.map((virtualRow) => {
                                        const row = rows[virtualRow.index];
                                        return (
                                            <Table.Row
                                                key={row.id}
                                                data-index={virtualRow.index}
                                                data-testid="resource-table-row"
                                                bg={
                                                    row.getIsSelected()
                                                        ? "table.rowHover"
                                                        : "transparent"
                                                }
                                                _hover={{ bg: "table.rowHover" }}
                                                cursor={onRowClick ? "pointer" : "default"}
                                                onClick={() => onRowClick?.(row.original)}
                                            >
                                                {row.getVisibleCells().map((cell, j) => (
                                                    <Table.Cell
                                                        key={cell.id}
                                                        color={
                                                            (enableRowSelection ? j === 1 : j === 0)
                                                                ? "slate.text.primary"
                                                                : "slate.text.muted"
                                                        }
                                                        fontSize="xs"
                                                        py={0}
                                                        px={2}
                                                        borderBottomColor="#384662"
                                                        w={`${cell.column.getSize()}px`}
                                                        css={{
                                                            height: `${VIRTUAL_ROW_HEIGHT}px`,
                                                            whiteSpace: "nowrap",
                                                            overflow: "hidden",
                                                            textOverflow: "ellipsis",
                                                        }}
                                                    >
                                                        {flexRender(
                                                            cell.column.columnDef.cell,
                                                            cell.getContext(),
                                                        )}
                                                    </Table.Cell>
                                                ))}
                                            </Table.Row>
                                        );
                                    })}
                                    {virtualPadBottom > 0 && (
                                        <Table.Row aria-hidden="true">
                                            <Table.Cell
                                                colSpan={visibleColumnCount}
                                                css={{
                                                    height: `${virtualPadBottom}px`,
                                                    padding: 0,
                                                    borderBottom: "none",
                                                }}
                                            />
                                        </Table.Row>
                                    )}
                                </>
                            ) : (
                                rows.map((row) => (
                                    <Table.Row
                                        key={row.id}
                                        bg={row.getIsSelected() ? "table.rowHover" : "transparent"}
                                        _hover={{ bg: "table.rowHover" }}
                                        cursor={onRowClick ? "pointer" : "default"}
                                        onClick={() => onRowClick?.(row.original)}
                                    >
                                        {row.getVisibleCells().map((cell, j) => (
                                            <Table.Cell
                                                key={cell.id}
                                                color={
                                                    (enableRowSelection ? j === 1 : j === 0)
                                                        ? "slate.text.primary"
                                                        : "slate.text.muted"
                                                }
                                                fontSize="xs"
                                                py={1.5}
                                                px={2}
                                                borderBottomColor="#384662"
                                                w={`${cell.column.getSize()}px`}
                                            >
                                                {flexRender(
                                                    cell.column.columnDef.cell,
                                                    cell.getContext(),
                                                )}
                                            </Table.Cell>
                                        ))}
                                    </Table.Row>
                                ))
                            )}
                        </Table.Body>
                    </Table.Root>
                    {isFetchingNextPage && (
                        <Flex justify="center" py={3}>
                            <Spinner size="sm" color="fg.muted" borderWidth="2px" />
                        </Flex>
                    )}
                    {isError && rows.length > 0 && !isFetchingNextPage && (
                        <Flex justify="center" align="center" gap={2} py={3}>
                            <Text fontSize="xs" color="fg.muted">
                                {t("resourceTable.loadMoreError")}
                            </Text>
                            {onRetry && (
                                <Button size="xs" variant="toolbar" onClick={onRetry}>
                                    {t("resourceTable.retry")}
                                </Button>
                            )}
                        </Flex>
                    )}
                </Box>
                {totalCount != null && (
                    <Flex
                        justify="flex-end"
                        px={2}
                        py={1}
                        borderTopWidth="1px"
                        borderColor="#384662"
                    >
                        <Text fontSize="xs" color="fg.muted">
                            {t("resourceTable.loadedCount", {
                                loaded: rows.length,
                                total: totalCount,
                            })}
                        </Text>
                    </Flex>
                )}
            </Flex>
            {detailDrawer}

            {pendingConfirmAction?.confirm && (
                <ConfirmDialog
                    open={true}
                    onClose={() => setPendingConfirmAction(null)}
                    onConfirm={handleConfirm}
                    title={pendingConfirmAction.confirm.title}
                    message={pendingConfirmAction.confirm.message}
                    confirmLabel={pendingConfirmAction.confirm.confirmLabel}
                    isDestructive={pendingConfirmAction.confirm.isDestructive}
                />
            )}
        </>
    );
}

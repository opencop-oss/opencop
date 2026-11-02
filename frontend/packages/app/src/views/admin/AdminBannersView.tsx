// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Box, Flex, Text } from "@chakra-ui/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
    ColumnFilterBuilder,
    ResourceTable,
    createColumnHelper,
    toFilterableColumns,
} from "@opencop/ui";
import type { ResourceTableToolbar } from "@opencop/ui";
import { useMemo, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { ViewDrawerProvider, useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { useBulkAction } from "~/features/admin/use-bulk-action.hook";
import { buildBulkDeleteAction } from "~/features/admin/bulk-delete.util";
import { useResourceTableState } from "~/features/admin/use-resource-table-state.hook";
import { useSelectedRows } from "~/features/admin/use-selected-rows.hook";
import { FaPlus } from "react-icons/fa";
import { DateTime } from "luxon";
import { BANNER_NO_EXPIRY_DATE } from "@opencop/api";
import type { Banner } from "@opencop/api";
import { bannerMutations, bannerQueries } from "~/features/banners/banners.queries";
import { BannerFormDialog } from "~/features/banners/dialogs/BannerFormDialog";
import { ConfirmDeleteDialog } from "~/features/membership-management/ConfirmDeleteDialog";
import { BannerDrawer } from "~/features/banners/BannerDrawer";
import type { BannerDrawerState, BannerModalState } from "~/features/banners/BannerDrawer";
import { formatInstant } from "~/features/banners/banner.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { useToast } from "~/features/notifications/use-toast.hook";

const columnHelper = createColumnHelper<Banner>();

/** Module-level so the table-state hook's selection callback keeps a stable identity. */
const getBannerId = (banner: Banner) => banner.id;

export function AdminBannersView() {
    return (
        <ViewDrawerProvider<BannerModalState> initialModal={{ type: "none" }}>
            <AdminBannersViewContent />
        </ViewDrawerProvider>
    );
}

function AdminBannersViewContent() {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const { selectedId, openDrawer, closeDrawer, modal, setModal } =
        useViewDrawerContext<BannerModalState>();

    const { data: banners = [], isLoading, refetch, isFetching } = useQuery(bannerQueries.list());
    const { mutateAsync: deleteBanner } = useMutation(bannerMutations.delete(queryClient));

    const tableState = useResourceTableState();
    const { selectedRows, onRowSelectionChange } = useSelectedRows(
        banners,
        getBannerId,
        tableState.rowSelection,
        tableState.onRowSelectionChange,
    );
    const selectedIds = useMemo(() => selectedRows.map(getBannerId), [selectedRows]);
    const { handleBulkAction } = useBulkAction(
        deleteBanner,
        selectedIds,
        tableState.resetSelection,
    );

    const handleBulkDelete = useCallback(async () => {
        const { failures } = await handleBulkAction();
        if (failures > 0) {
            showToast({
                type: "error",
                title: t("adminBanners.bulkDeleteError", { count: failures }),
            });
        }
    }, [handleBulkAction, showToast, t]);

    const drawerState: BannerDrawerState = useMemo(() => {
        if (!selectedId) return { type: "closed" };
        const banner = banners.find((b) => b.id === selectedId);
        return banner ? { type: "banner", entity: banner } : { type: "closed" };
    }, [selectedId, banners]);
    const closeModal = () => setModal({ type: "none" });

    const handleDeleteBanner = async (banner: Banner) => {
        try {
            await deleteBanner(banner.id);
            closeModal();
            closeDrawer();
        } catch {
            showToast({ type: "error", title: t("layers.deleteError" as never) });
        }
    };

    const timezone = useUserTimezone();

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("adminBanners.name"),
                cell: (info) => info.getValue(),
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("adminBanners.name"),
                        type: "string" as const,
                    },
                },
                filterFn: "ocStringFilter",
            }),
            columnHelper.accessor("kind", {
                header: () => t("adminBanners.kind"),
                cell: (info) => t(`adminBanners.kinds.${info.getValue().toLowerCase()}` as never),
                enableSorting: true,
                meta: {
                    filterDef: {
                        label: t("adminBanners.kind"),
                        type: "enum" as const,
                        options: [
                            { label: t("adminBanners.kinds.header"), value: "HEADER" },
                            { label: t("adminBanners.kinds.footer"), value: "FOOTER" },
                            { label: t("adminBanners.kinds.modal"), value: "MODAL" },
                            { label: t("adminBanners.kinds.system"), value: "SYSTEM" },
                        ],
                    },
                },
                filterFn: "ocEnumFilter",
            }),
            columnHelper.accessor("enabled", {
                header: () => t("adminBanners.enabled"),
                cell: (info) => {
                    const banner = info.row.original;
                    const enabled = info.getValue();
                    if (!enabled) {
                        return (
                            <Badge colorPalette="gray" variant="subtle">
                                {t("adminBanners.disabled")}
                            </Badge>
                        );
                    }
                    if (
                        banner.endsAt !== BANNER_NO_EXPIRY_DATE &&
                        DateTime.fromISO(banner.endsAt) < DateTime.now()
                    ) {
                        return <Badge colorPalette="red">{t("adminBanners.expired")}</Badge>;
                    }
                    return <Badge colorPalette="green">{t("adminBanners.enabled")}</Badge>;
                },
                enableSorting: false,
            }),
            columnHelper.accessor("priority", {
                header: () => t("adminBanners.priority"),
                cell: (info) => <Text color="fg.muted">{String(info.getValue())}</Text>,
                enableSorting: true,
            }),
            columnHelper.accessor("color", {
                header: () => t("adminBanners.color"),
                cell: (info) => {
                    const color = info.getValue();
                    return (
                        <Flex align="center" gap={2}>
                            <Box
                                w={4}
                                h={4}
                                borderRadius="sm"
                                bg={color}
                                border="1px solid"
                                borderColor="border.muted"
                                flexShrink={0}
                            />
                            <Text color="fg.muted">{color}</Text>
                        </Flex>
                    );
                },
                enableSorting: false,
            }),
            columnHelper.accessor((banner) => banner.textColor ?? "#ffffff", {
                id: "textColor",
                header: () => t("adminBanners.textColor"),
                cell: (info) => {
                    const color = info.getValue();
                    return (
                        <Flex align="center" gap={2}>
                            <Box
                                w={4}
                                h={4}
                                borderRadius="sm"
                                bg={color}
                                border="1px solid"
                                borderColor="border.muted"
                                flexShrink={0}
                            />
                            <Text color="fg.muted">{color}</Text>
                        </Flex>
                    );
                },
                enableSorting: false,
            }),
            columnHelper.accessor("startsAt", {
                header: () => t("adminBanners.startsAt"),
                cell: (info) => (
                    <Text color="fg.muted">{formatInstant(info.getValue(), t, timezone)}</Text>
                ),
                enableSorting: true,
            }),
            columnHelper.accessor("endsAt", {
                header: () => t("adminBanners.endsAt"),
                cell: (info) => (
                    <Text color="fg.muted">{formatInstant(info.getValue(), t, timezone)}</Text>
                ),
                enableSorting: true,
            }),
        ],
        [t, timezone],
    );

    const filterableColumns = useMemo(() => toFilterableColumns(columns), [columns]);

    const toolbar: ResourceTableToolbar = {
        onRefresh: refetch,
        isRefreshing: isFetching,
        actions: [
            {
                label: t("common.create"),
                icon: <FaPlus />,
                showLabel: true,
                onClick: () => setModal({ type: "addBanner" }),
            },
        ],
        selectionActions: buildBulkDeleteAction(selectedRows.length, handleBulkDelete, t),
    };

    return (
        <Flex height="100%" width="100%" direction="column">
            <ResourceTable
                aria-label={t("adminBanners.title")}
                data={banners}
                columns={columns}
                isLoading={isLoading}
                isFetching={isFetching}
                getRowId={getBannerId}
                onRowClick={(row) => openDrawer(row.id)}
                globalFilter={tableState.globalFilter}
                onGlobalFilterChange={tableState.onGlobalFilterChange}
                columnFilters={tableState.columnFilters}
                onColumnFiltersChange={tableState.onColumnFiltersChange}
                enableRowSelection
                rowSelection={tableState.rowSelection}
                onRowSelectionChange={onRowSelectionChange}
                selectedCount={selectedRows.length}
                toolbar={toolbar}
                filterBar={
                    <ColumnFilterBuilder
                        filterableColumns={filterableColumns}
                        value={tableState.columnFilters}
                        onChange={tableState.onColumnFiltersChange}
                        searchValue={tableState.globalFilter}
                        onSearchChange={tableState.onGlobalFilterChange}
                        searchPlaceholder={t("resourceTable.filterForAnyField")}
                    />
                }
                detailDrawer={<BannerDrawer drawerState={drawerState} />}
            />

            {modal.type === "addBanner" && <BannerFormDialog onClose={closeModal} />}

            {modal.type === "deleteBanner" && (
                <ConfirmDeleteDialog
                    isOpen
                    onClose={closeModal}
                    userName={modal.banner.name}
                    onDelete={() => handleDeleteBanner(modal.banner)}
                />
            )}
        </Flex>
    );
}

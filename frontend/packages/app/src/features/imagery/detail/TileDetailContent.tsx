// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Flex, Stack, Text } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { FaPencilAlt, FaStar, FaTrash } from "react-icons/fa";
import { TbPhoto, TbUserFilled, TbUsers } from "react-icons/tb";
import {
    ConfirmDialog,
    DetailTable,
    DrawerSideNavLayout,
    OcSwitch,
    OcTag,
    Tooltip,
} from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { useDrawerContext } from "~/features/drawers/DrawerContext";
import { useViewMode } from "~/features/drawers/use-view-mode.hook";
import { tileQueries, tileMutations } from "~/features/imagery/imagery.queries";
import { useToast } from "~/features/notifications/use-toast.hook";
import { ZOOM_MAX, ZOOM_MIN } from "~/features/map/core/slices/view/view-url.util";
import { TileDrawerContent } from "./TileDrawerContent";
import type { FormDeltas, FormTouched } from "./TileDrawerContent";
import { canBePrimary } from "~/features/imagery/imagery.constants";
import {
    getTileConfig,
    getTileKind,
    getWeatherFeatures,
} from "~/features/imagery/tile-config.util";
import { isWeatherTile } from "~/features/weather/weather.util";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { TileMembersSection } from "./TileMembersSection";
import { TileUserGroupsSection } from "./TileUserGroupsSection";

interface TileDetailContentProps {
    tileId: string;
}

export function TileDetailContent({ tileId }: TileDetailContentProps) {
    const { onClose } = useDrawerContext();
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { showToast } = useToast();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";
    const { data: tile } = useQuery(tileQueries.detail(tileId));
    const makePrimaryMutation = useMutation(tileMutations.makePrimary(queryClient));
    const deleteMutation = useMutation(tileMutations.delete(queryClient));
    const updateMutation = useMutation(tileMutations.update(queryClient));

    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [editDeltas, setEditDeltas] = useState<FormDeltas>({});
    const [editTouched, setEditTouched] = useState<FormTouched>({});

    if (!tile) return null;

    const tileConfig = getTileConfig(tile);
    const tileKind = getTileKind(tile);
    const displayKind =
        tileKind === "TERRAIN_MESH"
            ? (tileConfig as { kind: string; encoding: string }).encoding
            : tileKind;
    const kindLabel = t(`adminImagery.kinds.${displayKind.toLowerCase()}` as never);
    // Header subtitle leads with the product-level category; the precise kind stays in the detail row below.
    const categoryKey = isWeatherTile(tile)
        ? "adminImagery.types.weather"
        : tileKind === "VECTOR"
          ? "adminImagery.kinds.vector"
          : "adminImagery.types.basemap";
    const categoryLabel = t(categoryKey);
    const tilePrimaryEligible = canBePrimary(tile);

    const hasZoomAndSize =
        "tileSize" in tileConfig && ("minZoom" in tileConfig || "maxZoom" in tileConfig);

    const configWithZoom = hasZoomAndSize
        ? (tileConfig as { tileSize: number; minZoom: number | null; maxZoom: number | null })
        : null;
    const weatherFeatures = getWeatherFeatures(tile.config);

    const detailRows = [
        { key: "name", label: t("adminImagery.name"), value: tile.name },
        { key: "kind", label: t("adminImagery.kind"), value: kindLabel },
        {
            key: "description",
            label: t("adminImagery.description"),
            value: tile.description || "-",
        },
        {
            key: "attribution",
            label: t("adminImagery.attribution"),
            value: tile.attribution || "-",
        },
        { key: "url", label: t("adminImagery.url"), value: tile.url },
        {
            key: "primary",
            label: t("adminImagery.primary"),
            value: tile.primary ? (
                <OcTag size="sm" colorPalette="cyan" startElement={<FaStar />}>
                    {t("adminImagery.primary")}
                </OcTag>
            ) : (
                "-"
            ),
        },
        {
            key: "enabled",
            // "Enabled", not "Status": the value here is a bare toggle with no text, so the label
            // has to say what the ON position means. It also matches the switch's own aria-label.
            label: t("adminImagery.enabled"),
            value: (
                <Tooltip
                    content={t("adminImagery.cannotDisablePrimary")}
                    disabled={!tile.primary}
                    uppercase={false}
                >
                    {/* Ark disabled controls emit no pointer events, so the tooltip needs a wrapper. */}
                    <span tabIndex={tile.primary ? 0 : -1}>
                        <OcSwitch
                            fullWidth={false}
                            srOnlyLabel
                            label={t("adminImagery.enabled")}
                            checked={tile.enabled}
                            disabled={tile.primary || updateMutation.isPending}
                            // Handled per-call rather than on the shared mutation: the edit
                            // form surfaces the same failure inline as submitError, and
                            // would otherwise report it twice.
                            onCheckedChange={(enabled) =>
                                updateMutation.mutate(
                                    { id: tileId, data: { enabled } },
                                    {
                                        onError: () =>
                                            showToast({
                                                type: "error",
                                                title: t("adminImagery.failedToUpdate"),
                                            }),
                                    },
                                )
                            }
                        />
                    </span>
                </Tooltip>
            ),
        },
        {
            key: "accessScope",
            label: t("adminImagery.accessScope"),
            value: <ScopeIndicator scope={tile.accessScope} />,
        },
        {
            key: "tileType",
            label: t("adminImagery.tileType"),
            value: (
                <Badge colorPalette={isWeatherTile(tile) ? "blue" : "gray"} variant="subtle">
                    {isWeatherTile(tile) ? "WEATHER" : "BASEMAP"}
                </Badge>
            ),
        },
        ...(configWithZoom
            ? [
                  {
                      key: "tileSize",
                      label: t("adminImagery.config.tileSize"),
                      value: String(configWithZoom.tileSize),
                  },
                  {
                      key: "zoomRange",
                      label: t("adminImagery.config.zoomRange"),
                      value: `${configWithZoom.minZoom ?? ZOOM_MIN} — ${configWithZoom.maxZoom ?? ZOOM_MAX}`,
                  },
              ]
            : []),
        ...(weatherFeatures.length
            ? [
                  {
                      key: "weatherFeatures",
                      label: t("adminImagery.weatherFeatures"),
                      value: (
                          <Stack gap={1} w="full">
                              {weatherFeatures.map((entry) => (
                                  <Flex key={entry.feature} align="center" gap={2} minW={0}>
                                      <Badge
                                          size="sm"
                                          colorPalette="cyan"
                                          variant="subtle"
                                          flexShrink={0}
                                      >
                                          {entry.feature}
                                      </Badge>
                                      <Text fontSize="xs" color="fg.muted" truncate>
                                          {entry.url}
                                      </Text>
                                  </Flex>
                              ))}
                          </Stack>
                      ),
                  },
              ]
            : []),
    ];

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(
            t,
            <DetailTable rows={detailRows} compact />,
            t("drawerSections.entity.imageryTile"),
        ),
        {
            key: "members",
            label: t("adminActions.users"),
            icon: <TbUserFilled />,
            content: <TileMembersSection tileId={tileId} />,
        },
        {
            key: "userGroups",
            label: t("adminLayerGroups.userGroups"),
            icon: <TbUsers />,
            content: <TileUserGroupsSection tileId={tileId} />,
        },
    ];

    return (
        <>
            <DrawerSideNavLayout
                name={tile.name}
                badgeLabel={categoryLabel}
                navLabel={t("adminNav.imagery")}
                icon={<TbPhoto />}
                sections={sections}
                actions={
                    editing ? undefined : (
                        <>
                            {tilePrimaryEligible && !tile.primary && (
                                <DrawerToolbarButton
                                    label={t("adminImagery.makePrimary")}
                                    icon={<FaStar />}
                                    loading={makePrimaryMutation.isPending}
                                    onClick={() => makePrimaryMutation.mutate(tileId)}
                                />
                            )}
                            <DrawerToolbarButton
                                label={t("common.edit")}
                                icon={<FaPencilAlt />}
                                onClick={toEdit}
                            />
                            <DrawerToolbarButton
                                label={t("common.delete")}
                                icon={<FaTrash />}
                                destructive
                                loading={deleteMutation.isPending}
                                disabled={tile.primary}
                                disabledReason={
                                    tile.primary ? t("adminImagery.cannotDeletePrimary") : undefined
                                }
                                onClick={() => setShowDeleteConfirm(true)}
                            />
                        </>
                    )
                }
                editContent={
                    editing ? (
                        <TileDrawerContent
                            tileId={tileId}
                            deltas={editDeltas}
                            touched={editTouched}
                            onDeltasChange={setEditDeltas}
                            onTouchedChange={setEditTouched}
                            onDone={toView}
                        />
                    ) : undefined
                }
            />
            <ConfirmDialog
                open={showDeleteConfirm}
                onClose={() => setShowDeleteConfirm(false)}
                onConfirm={async () => {
                    await deleteMutation.mutateAsync(tileId);
                    onClose();
                }}
                title={t("adminImagery.confirmDeleteTitle")}
                message={t("adminImagery.confirmDeleteMessage")}
                confirmLabel={t("common.delete")}
            />
        </>
    );
}

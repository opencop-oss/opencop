// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Text, Link, Stack } from "@chakra-ui/react";
import { type Ref, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Link as RouterLink } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { OcSelect } from "@opencop/ui";
import { TileKind, type UserPreferences } from "@opencop/api";
import { PreferenceRow } from "./PreferenceRow";
import { PreferenceSelect } from "./PreferenceSelect";
import { ZOOM_BEHAVIORS } from "./preferences.constants";
import { preferencesMutations } from "./preferences.queries";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { getTileKind } from "~/features/imagery/tile-config.util";
import { isWeatherTile } from "~/features/weather/weather.util";
import { mapCaptureQueries } from "~/features/mapcapture/mapcapture.queries";
import { clearPersistedViewport } from "~/features/map/use-persisted-viewport.hook";

interface MapOptionsSectionProps {
    preferences: UserPreferences | undefined;
    ref?: Ref<HTMLDivElement>;
}

export function MapOptionsSection({ preferences, ref }: MapOptionsSectionProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { mutate: updatePreferencesMutate } = useMutation(
        preferencesMutations.update(queryClient),
    );

    const { data: allTiles = [] } = useQuery(tileQueries.list());
    const userSelectableTiles = allTiles.filter(
        (tile) =>
            (getTileKind(tile) === TileKind.VECTOR || getTileKind(tile) === TileKind.RASTER) &&
            !isWeatherTile(tile),
    );

    const { data: captures = [] } = useQuery(mapCaptureQueries.list());

    const captureItems = useMemo(() => {
        return [
            {
                label: t("userPreferences.notConfigured", { defaultValue: "Not configured" }),
                value: "",
            },
            ...captures.map((capture) => ({
                label: capture.name,
                value: capture.id,
            })),
        ];
    }, [captures, t]);
    const zoomBehaviorItems = useMemo(
        () =>
            ZOOM_BEHAVIORS.map((value) => ({
                value,
                label:
                    value === "CENTER"
                        ? t("userPreferences.zoomToCenter", { defaultValue: "Zoom to Center" })
                        : t("userPreferences.zoomToMouse", { defaultValue: "Zoom to Mouse" }),
            })),
        [t],
    );

    return (
        <Stack gap={4} ref={ref} id="map-options">
            <Text fontSize="lg" fontWeight="semibold" color="fg">
                {t("userPreferences.mapOptionsSection", { defaultValue: "Map Options" })}
            </Text>
            <Box display="grid" gridTemplateColumns="1fr auto auto" gap={4} alignItems="center">
                <Text fontWeight="medium" color="fg.muted">
                    {t("userPreferences.initialView", { defaultValue: "Initial View" })}
                </Text>
                <Link
                    asChild
                    color="active"
                    fontSize="sm"
                    textDecoration="none"
                    _hover={{ textDecoration: "underline", opacity: 0.8 }}
                    whiteSpace="nowrap"
                >
                    <RouterLink to="/map?openCapture=true">
                        {t("userPreferences.goToMapCaptures", {
                            defaultValue: "Go to Map Captures",
                        })}
                    </RouterLink>
                </Link>
                <Box width="180px">
                    {captures.length === 0 ? (
                        <Text fontSize="sm" color="fg.muted">
                            {t("userPreferences.noCapturesAvailable", {
                                defaultValue: "No captures available",
                            })}
                        </Text>
                    ) : (
                        <OcSelect
                            items={captureItems}
                            value={
                                preferences?.initialView === null
                                    ? ""
                                    : (preferences?.initialView?.id ?? "")
                            }
                            onValueChange={(value) => {
                                clearPersistedViewport();
                                updatePreferencesMutate({
                                    initialView: value === "" ? null : value,
                                });
                            }}
                            size="sm"
                            variant="outline"
                        />
                    )}
                </Box>
            </Box>

            <PreferenceRow
                label={t("userPreferences.defaultTile", { defaultValue: "Default Tile" })}
                description={t("userPreferences.defaultTileNote", {
                    defaultValue: "Override app default basemap",
                })}
                alignItems="start"
            >
                {userSelectableTiles.length === 0 ? (
                    <Text fontSize="sm" color="fg.muted">
                        {t("userPreferences.noTilesAvailable", {
                            defaultValue: "No tiles available",
                        })}
                    </Text>
                ) : (
                    <OcSelect
                        items={[
                            {
                                label: t("userPreferences.appDefault", {
                                    defaultValue: "App Default",
                                }),
                                value: "",
                            },
                            ...userSelectableTiles.map((tile) => ({
                                label: tile.name,
                                value: tile.id,
                            })),
                        ]}
                        value={preferences?.defaultTile?.id ?? ""}
                        onValueChange={(value) => {
                            updatePreferencesMutate({
                                defaultTileId: value === "" ? null : value,
                            });
                        }}
                        size="sm"
                        variant="outline"
                    />
                )}
            </PreferenceRow>

            <PreferenceSelect
                label={t("userPreferences.zoomBehavior", { defaultValue: "Zoom Behavior" })}
                items={zoomBehaviorItems}
                value={preferences?.zoomBehavior ?? "CENTER"}
                onValueChange={(value) => {
                    updatePreferencesMutate({
                        zoomBehavior: value as "CENTER" | "MOUSE",
                    });
                }}
            />
        </Stack>
    );
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ParseKeys } from "i18next";
import type { ComponentType, ReactElement } from "react";
import { AiFillNotification } from "react-icons/ai";
import { NotificationBadge } from "../notifications/DropNotification";
import { FaFilter, FaImages, FaLayerGroup } from "react-icons/fa6";
import { IoIosSettings } from "react-icons/io";
import { IoCameraSharp, IoListSharp } from "react-icons/io5";
import { LuCloudRainWind } from "react-icons/lu";
import { MdTerrain } from "react-icons/md";
import { Filters } from "../filters/Filters";
import { Imagery } from "../imagery/Imagery";
import { MapCapture } from "../mapcapture/MapCapture";
import { NotificationList } from "../notifications/NotificationList";
import { MapSettings } from "../settings/MapSettings";
import { Terrain } from "../terrain/Terrain";
import { Weather } from "../weather/Weather";
import { EventsPanel } from "./EventsPanel";
import { EVENTS_PANEL_ID } from "../events/events-panel.const";
import { useSelectedTerrainStore } from "../terrain/selected-terrain.store";
import { WatchboxPanel } from "../watchbox/WatchboxPanel";
import { WATCHBOX_PANEL_ID } from "../watchbox/watchbox-panel.policy";
import { TbRadar } from "react-icons/tb";
import { MapFilterPanel } from "../map-filter/MapFilterPanel";
import { MAP_FILTERS_PANEL_ID } from "../map-filter/map-filter.store";

export interface PanelDefinition {
    id: string;
    /** i18n key for the panel's label, resolved via t() at render time. */
    translationKey: ParseKeys;
    icon: ComponentType;
    content: ComponentType;
    isActive?: () => boolean;
    badge?: ReactElement;
    /** When true, the panel is registered and renderable but has no toolbar toggle button. */
    hidden?: boolean;
}

export const panels: PanelDefinition[] = [
    {
        id: "imagery",
        translationKey: "mapbar.imagery",
        icon: FaImages,
        content: Imagery,
    },
    {
        id: "terrain",
        translationKey: "mapbar.terrain",
        icon: MdTerrain,
        content: Terrain,
        isActive: () => !!useSelectedTerrainStore.getState().terrainId,
    },
    {
        id: "weather",
        translationKey: "mapbar.weather",
        icon: LuCloudRainWind,
        content: Weather,
    },
    {
        id: "captures",
        translationKey: "mapbar.captures",
        icon: IoCameraSharp,
        content: MapCapture,
    },
    {
        id: EVENTS_PANEL_ID,
        translationKey: "mapbar.events",
        icon: IoListSharp,
        content: EventsPanel,
    },
    {
        id: MAP_FILTERS_PANEL_ID,
        translationKey: "mapbar.mapFilter",
        icon: FaFilter,
        content: MapFilterPanel,
    },
    {
        // Layer/data-source visibility, not predicate filtering — that lives in
        // the "map-filters" panel above, which keeps the funnel icon.
        id: "filters",
        translationKey: "mapbar.layersAndSources",
        icon: FaLayerGroup,
        content: Filters,
    },
    {
        id: "notifications",
        translationKey: "mapbar.notifications",
        icon: AiFillNotification,
        content: NotificationList,
        badge: <NotificationBadge />,
    },
    {
        id: WATCHBOX_PANEL_ID,
        translationKey: "mapbar.watchbox",
        icon: TbRadar,
        content: WatchboxPanel,
    },
    {
        id: "settings",
        translationKey: "mapbar.settings",
        icon: IoIosSettings,
        content: MapSettings,
    },
];

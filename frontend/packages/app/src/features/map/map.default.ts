// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { StyleSpecification } from "maplibre-gl";

export const INITIAL_VIEW_STATE = {
    longitude: 0,
    latitude: 0,
    zoom: 1,
    pitch: 0,
    bearing: 0,
};

export const defaultStyle: StyleSpecification = {
    version: 8,
    sources: {},
    layers: [
        {
            id: "opencop-background",
            type: "background",
            paint: { "background-color": "#1f2530" },
        },
    ],
};

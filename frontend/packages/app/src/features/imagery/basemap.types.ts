// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapTile } from "@opencop/api";

export interface BasemapRow {
    id: string;
    name: string;
    kindLabel: string;
    description: string | null;
    tile: MapTile;
}

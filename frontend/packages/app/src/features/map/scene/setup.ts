// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    AngleBatch,
    IconBatch,
    MarchingAntsBatch,
    PathBatch,
    PointBatch,
    PolygonBatch,
    TextBatch,
} from "@opencop/scene";
import type { Device } from "@luma.gl/core";

export type SetupSceneDeps = {
    device: Device;
};

export function setup({ device }: SetupSceneDeps) {
    const props = { device };

    return [
        new PolygonBatch(props),
        new PathBatch(props),
        new PointBatch(props),
        new IconBatch(props),
        new TextBatch(props),
        new MarchingAntsBatch(),
        new AngleBatch(props),
    ];
}

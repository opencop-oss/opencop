// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { StyleSpecification } from "maplibre-gl";
import { CustomMapSource } from "./custom-map-source";
import { generateMapStyle } from "./map-style.util";

export function mobac(): StyleSpecification | undefined {
    const custom: CustomMapSource | undefined = CustomMapSource.fromXML("");

    if (custom) {
        return generateMapStyle(custom);
    }

    return custom;
}

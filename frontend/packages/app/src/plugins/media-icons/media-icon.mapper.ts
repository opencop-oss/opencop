// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MediaIcon, type MediaIconElement } from "@opencop/elements";
import type { ElementMapper, MapperSelector, MapperState } from "@opencop/contributions";
import { icon, label, type Primitive } from "@opencop/scene";
import { resolveMediaIcon } from "./media-icon";

const sizeSelector: MapperSelector<number> = {
    select: (state: MapperState) => state.icons.size,
};

/**
 * How a media icon is drawn: its image at the operator's icon size, its name
 * below it, and a course arrow orbiting it when it is moving. The image is
 * resolved from the element's `href`; one that cannot be fetched draws as a
 * "broken image" mark, so the placemark stays visible.
 */
export const mediaIconMapper: ElementMapper<MediaIconElement, number> = {
    type: MediaIcon.type,
    selector: sizeSelector,
    map(element, size) {
        const source = resolveMediaIcon(MediaIcon.getHref(element));
        const [lng, lat] = element.geometry.getPosition() as [number, number];
        const positions: [number, number, number] = [lng, lat, 0];
        const primitives: Primitive[] = [
            icon({
                id: "icon",
                geometry: { positions },
                style: {
                    id: source.id,
                    source: source.url,
                    width: source.width,
                    height: source.height,
                    size,
                    billboard: true,
                },
            }),
        ];
        const name = element.getDisplayName();
        if (name)
            primitives.push(
                label({
                    id: "label",
                    geometry: { positions },
                    style: { text: name, offset: [0, size * 0.75] },
                }),
            );
        return primitives;
    },
};

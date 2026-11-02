// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Point as PointGeometry } from "../geometry/Point";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position2D } from "../geometry/Geometry";
import { MediaIconStyle } from "../style/MediaIconStyle";

/**
 * Construction properties for {@link MediaIcon.create}. Unlike the other types, there is
 * no `style` override: the image location is the style's `href`, so a style passed beside
 * `href` could name a different image.
 */
export interface MediaIconProps extends Omit<TypeDefProps<MediaIconStyle>, "style"> {
    /** The icon position. */
    position: Position2D;
    /** The image's location (see {@link MediaIconStyle}). */
    href: string;
    /** Optional display size override in pixels. */
    size?: number;
}

export type MediaIconElement = Element<PointGeometry, MediaIconStyle>;

/**
 * Type definition for media icons: points drawn as an imported image rather
 * than a MIL-STD-2525 symbol.
 *
 * The image's location is the style's `href` (see {@link MediaIconStyle}). A
 * media icon has no `code`: the code slot holds a symbol code, and an image
 * location is not one.
 */
export const MediaIcon = {
    type: "media-icon",

    /** Type guard narrowing to a media icon element. */
    is(element: Element): element is MediaIconElement {
        return element.type === this.type;
    },

    /**
     * The image's location.
     * @param element The media icon.
     */
    getHref(element: MediaIconElement): string {
        return element.getStyle().getHref();
    },

    /**
     * Create a media icon from an image location and position.
     * @param props The media icon properties.
     */
    create({ position, href, size, meta }: MediaIconProps): MediaIconElement {
        return new Element({
            ...meta,
            type: this.type,
            geometry: new PointGeometry(position),
            style: new MediaIconStyle({ href, size }),
        });
    },
} as const satisfies TypeDef<PointGeometry, MediaIconStyle>;

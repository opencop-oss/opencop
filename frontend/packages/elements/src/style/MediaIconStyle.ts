// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconStyle, type IconStyleProps } from "./IconStyle";
import type { Style } from "./Style";

/** Construction properties for a {@link MediaIconStyle}. */
export interface MediaIconStyleProps extends IconStyleProps {
    /** The image's location, as the server sent it. */
    href: string;
}

/**
 * Style of a media icon: an {@link IconStyle} plus the location of the image
 * it draws.
 *
 * The image belongs to the style the way KML's `<IconStyle><Icon><href>` does,
 * which also means it travels with every copy of the style — a pasted or moved
 * media icon keeps its image with no copy step of its own. Like the rest of this
 * package, the style only stores the `href`: an `opencop:url:` reference with a
 * `#WxH` size fragment, an `http(s)://` or `data:` URL, or a relative path that
 * matched no imported image. Whoever renders the element resolves it.
 */
export class MediaIconStyle extends IconStyle {
    private readonly _href: string;

    /**
     * @param props The media icon style properties.
     */
    constructor({ href, ...icon }: MediaIconStyleProps) {
        super(icon);
        this._href = href;
    }

    /** The image's location. */
    getHref(): string {
        return this._href;
    }

    /** Clone the media icon style. */
    clone(): Style {
        return new MediaIconStyle({
            href: this._href,
            size: this.getSize(),
            angle: this.getAngle(),
        });
    }

    /** Serialize to a plain object for the wire format. */
    toProperties(): Record<string, unknown> {
        return { ...super.toProperties(), href: this._href };
    }

    /** Inverse of {@link toProperties}. Reconstructs a {@link MediaIconStyle} from a serialized property bag. */
    static fromProperties(props: Record<string, unknown>): MediaIconStyle {
        const icon = IconStyle.fromProperties(props);
        return new MediaIconStyle({
            href: typeof props["href"] === "string" ? props["href"] : "",
            size: icon.getSize(),
            angle: icon.getAngle(),
        });
    }
}

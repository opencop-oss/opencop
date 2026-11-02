// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { cachedImage } from "@opencop/ui";
import { MediaIcon } from "@opencop/elements";
import type { ContributionContext } from "@opencop/contributions";
import { resolveMediaIcon } from "./media-icon";
import { mediaIconMapper } from "./media-icon.mapper";

/**
 * The media-icon subject: points drawn as an imported image. It contributes only
 * a mapper for the `media-icon` element type — there is no draw tool (you
 * don't draw a media icon), so its elements get the host's generic edit. Same
 * one-context registration shape as every other subject.
 */
export function installMediaIcons(ctx: ContributionContext): void {
    ctx.registerElementMapper({
        ...mediaIconMapper,
        // The thumbnail is the image itself, loaded once per URL.
        preview: (element) =>
            MediaIcon.is(element)
                ? cachedImage(resolveMediaIcon(MediaIcon.getHref(element)).url)
                : null,
        textSetting: { labelKey: "mapSettings.textLabels.mediaIcon", order: 75 },
    });
}

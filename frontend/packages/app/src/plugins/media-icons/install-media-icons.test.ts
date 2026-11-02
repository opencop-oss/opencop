// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { MediaIcon } from "@opencop/elements";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { installNativeSubjects } from "~/features/contributions/native-install";
import {
    getTextSettings,
    mapperFor,
    resolveElementPreview,
} from "~/features/contributions/element-mapper-registry";
import { installMediaIcons } from "./install-media-icons";

/**
 * Imported artwork is an OSS capability: media icons draw and preview on their
 * own, with no other subject installed — symbology in particular. Only the
 * media-icons subject is installed here, against the real registries.
 */

const MEDIA_HREF = "opencop:url:/v1/layers/abc/media/def#32x32";
const MEDIA_URL = "http://localhost/api/v1/layers/abc/media/def";

let dispose: () => void = () => undefined;
beforeEach(() => {
    dispose = installNativeSubjects(
        [{ id: "opencop.media-icons", install: installMediaIcons }],
        contributionContextFor,
    );
});
afterEach(() => dispose());

describe("media icons without symbology", () => {
    it("are drawn by the media-icons mapper, as their own image", () => {
        const mapper = mapperFor("media-icon")!;
        const element = MediaIcon.create({ position: [0, 0], href: MEDIA_HREF });

        const [icon] = mapper.map(element, 40);

        expect(icon.style).toMatchObject({ type: "icon", source: MEDIA_URL, size: 40 });
        expect(mapperFor("symbol")).toBeUndefined();
    });

    it("preview as their own image", () => {
        const preview = resolveElementPreview(
            MediaIcon.create({ position: [0, 0], href: MEDIA_HREF }),
        );

        expect(preview).toBeInstanceOf(HTMLImageElement);
        expect((preview as HTMLImageElement).src).toBe(MEDIA_URL);
    });

    it("have their own text switch, apart from symbol text", () => {
        expect(getTextSettings()).toEqual([
            { type: "media-icon", labelKey: "mapSettings.textLabels.mediaIcon", order: 75 },
        ]);
    });
});

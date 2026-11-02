// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** How many decoded images {@link cachedImage} keeps; the least recently used goes first. */
export const CACHED_IMAGE_LIMIT = 256;

const images = new Map<string, HTMLImageElement>();

/**
 * One shared `HTMLImageElement` per `url`, so a thumbnail shown in many places
 * loads and decodes once. The image may still be loading when it is returned;
 * listen for its `load` and `error` events, or read `complete`.
 */
export function cachedImage(url: string): HTMLImageElement {
    const hit = images.get(url);
    if (hit) {
        images.delete(url);
        images.set(url, hit);
        return hit;
    }
    const image = new Image();
    image.decoding = "async";
    image.src = url;
    images.set(url, image);
    if (images.size > CACHED_IMAGE_LIMIT) images.delete(images.keys().next().value as string);
    return image;
}

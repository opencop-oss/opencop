// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export const THUMBNAIL_MAX_DIMENSION = 320;
export const THUMBNAIL_FORMAT = "image/webp";
export const THUMBNAIL_QUALITY = 0.85;

/**
 * Generates a thumbnail File from an image File.
 * Scales the image so its longest side is at most 320px,
 * preserving the original aspect ratio. Outputs as WebP.
 * Returns null on failure (canvas unavailable, or image load error);
 * callers should omit the thumbnail rather than substituting the original.
 */
export async function createThumbnail(
    file: File,
    dimension: number = THUMBNAIL_MAX_DIMENSION,
): Promise<File | null> {
    if (file.type === "image/svg+xml") return null;
    if (file.type.startsWith("video/")) return createVideoThumbnail(file, dimension);

    return new Promise((resolve) => {
        const img = new Image();
        const objectUrl = URL.createObjectURL(file);

        img.onload = () => {
            URL.revokeObjectURL(objectUrl);

            const { naturalWidth: w, naturalHeight: h } = img;
            const scale = Math.min(1, dimension / Math.max(w, h));
            const thumbW = Math.round(w * scale);
            const thumbH = Math.round(h * scale);

            const canvas = document.createElement("canvas");
            canvas.width = thumbW;
            canvas.height = thumbH;

            const ctx = canvas.getContext("2d");
            if (!ctx) {
                resolve(null);
                return;
            }

            ctx.drawImage(img, 0, 0, thumbW, thumbH);

            canvas.toBlob(
                (blob) =>
                    resolve(
                        blob
                            ? new File([blob], `thumb_${file.name.replace(/\.[^.]+$/, "")}.webp`, {
                                  type: THUMBNAIL_FORMAT,
                              })
                            : null,
                    ),
                THUMBNAIL_FORMAT,
                THUMBNAIL_QUALITY,
            );
        };

        img.onerror = () => {
            URL.revokeObjectURL(objectUrl);
            resolve(null);
        };

        img.src = objectUrl;
    });
}

// This function gets a snapshot from the midpoint, because the first frame is frequently a black screen
async function createVideoThumbnail(
    file: File,
    dimension: number = THUMBNAIL_MAX_DIMENSION,
): Promise<File | null> {
    return new Promise((resolve) => {
        const video = document.createElement("video");
        const objectUrl = URL.createObjectURL(file);
        let done = false;
        const cleanup = (result: File | null) => {
            if (done) return;
            done = true;
            URL.revokeObjectURL(objectUrl);
            resolve(result);
        };
        const timeout = setTimeout(() => cleanup(null), 10_000);

        video.addEventListener("loadedmetadata", () => {
            if (!Number.isFinite(video.duration) || video.duration <= 0) {
                clearTimeout(timeout);
                cleanup(null);
                return;
            }
            video.currentTime = video.duration * 0.5;
        });
        video.addEventListener("seeked", () => {
            URL.revokeObjectURL(objectUrl);

            const { videoWidth: w, videoHeight: h } = video;
            const scale = Math.min(1, dimension / Math.max(w, h));
            const thumbW = Math.round(w * scale);
            const thumbH = Math.round(h * scale);

            const canvas = document.createElement("canvas");
            canvas.width = thumbW;
            canvas.height = thumbH;

            const ctx = canvas.getContext("2d");
            if (!ctx) {
                resolve(null);
                return;
            }

            ctx.drawImage(video, 0, 0, thumbW, thumbH);

            canvas.toBlob(
                (blob) =>
                    resolve(
                        blob
                            ? new File([blob], `thumb_${file.name.replace(/\.[^.]+$/, "")}.webp`, {
                                  type: THUMBNAIL_FORMAT,
                              })
                            : null,
                    ),
                THUMBNAIL_FORMAT,
                THUMBNAIL_QUALITY,
            );
        });
        video.addEventListener("error", () => {
            clearTimeout(timeout);
            cleanup(null);
        });

        video.preload = "metadata";
        video.src = objectUrl;
    });
}

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Hls from "hls.js";
import Logger from "js-logger";
import { useEffect } from "react";
import { VisuallyHidden } from "@chakra-ui/react";
import { pipRefs, usePipStore } from "./pip.store";

const logger = Logger.get("pip");

/**
 * Keeps a hidden video element alive at the app root so a PiP stream can survive
 * the event panel unmounting. When activePipUrl is set (user entered PiP), this
 * component pre-warms an HLS instance on the backup video. VideoPlayer's cleanup
 * then calls requestPictureInPicture() on the backup before the original DOM node
 * is removed, transferring PiP seamlessly.
 */
export function StreamPipManager() {
    const activePipUrl = usePipStore((s) => s.activePipUrl);

    // Pre-warm an HLS instance on the backup video as soon as PiP starts.
    // This runs a second HLS session on the same URL for the full PiP lifetime (~2x
    // bandwidth/decode), but that cost is required: the backup must reach readyState
    // HAVE_CURRENT_DATA before panel-close so requestPictureInPicture() succeeds.
    // Starting just-in-time at close intent leaves readyState: 0 and causes a frozen frame.
    useEffect(() => {
        if (!activePipUrl) return;
        const video = pipRefs.backupVideoRef.current;
        if (!video) {
            logger.warn("backup video ref null when activePipUrl set; PiP pre-warm skipped");
            return;
        }
        if (!Hls.isSupported()) return;

        const hls = new Hls({ liveSyncDurationCount: 2 });
        hls.loadSource(activePipUrl);
        hls.attachMedia(video);
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
            video.play().catch((err) => {
                logger.warn("backup autoplay blocked:", err);
            });
        });
        pipRefs.backupHls = hls;

        return () => {
            // Only destroy if leavepictureinpicture hasn't already torn this instance down.
            if (pipRefs.backupHls === hls) {
                pipRefs.backupHls = null;
                hls.destroy();
            }
        };
    }, [activePipUrl]);

    // When the user exits PiP from the backup video, tear down.
    useEffect(() => {
        const video = pipRefs.backupVideoRef.current;
        if (!video) {
            logger.warn(
                "backup video ref null on mount; leavepictureinpicture teardown will not work",
            );
            return;
        }
        const onLeavePip = () => {
            pipRefs.backupHls?.destroy();
            pipRefs.backupHls = null;
            usePipStore.getState().setActivePipUrl(null);
        };
        video.addEventListener("leavepictureinpicture", onLeavePip);
        return () => video.removeEventListener("leavepictureinpicture", onLeavePip);
    }, []);

    return (
        <VisuallyHidden>
            <video
                ref={(el) => {
                    pipRefs.backupVideoRef.current = el;
                }}
                muted
                playsInline
            />
        </VisuallyHidden>
    );
}

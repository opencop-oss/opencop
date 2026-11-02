// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Hls from "hls.js";
import { useEffect, useRef, useState } from "react";
import type { StreamKind } from "@opencop/api";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useSpinDelay } from "spin-delay";
import { Center, Spinner, Text } from "@chakra-ui/react";
import { streamQueries } from "../events.queries";
import { pipRefs, usePipStore } from "../pip.store";
import { isBlockedMixedContent } from "./attachment.util";
import { StreamVideoShell } from "./StreamVideoShell";
import {
    CONVERTED_LOAD_TIMEOUT_MS,
    DIRECT_LOAD_TIMEOUT_MS,
    useStreamLoadTimeout,
    type StreamState,
} from "./use-stream-load-timeout.hook";

interface HlsPlayerProps {
    url: string;
    /** Only the video-playable kinds reach this player; camera feeds use CameraImageFeed. */
    streamKind: Extract<StreamKind, "RTSP" | "SRT" | "HLS">;
    sourceId: string;
    objectId: string;
    attachmentId: string;
    style?: React.CSSProperties;
    /** Fill the parent box, letterboxed on black, instead of taking the stream's own height. */
    fill?: boolean;
}

function VideoPlayer({
    hlsUrl,
    style,
    fill,
    converted,
}: {
    hlsUrl: string;
    style?: React.CSSProperties;
    fill?: boolean;
    /** Pulled through MediaMTX, whose cold start can take far longer than a direct manifest. */
    converted: boolean;
}) {
    const videoRef = useRef<HTMLVideoElement>(null);
    const [streamState, setStreamState] = useState<StreamState>("loading");

    // Waits as long as the lane can legitimately take, so a slow cold start isn't reported as
    // offline, but still gives up if the stream never produces a manifest — MediaMTX holds the path
    // open for its full runOnDemandStartTimeout while hls.js retries against 500 responses.
    const { slow } = useStreamLoadTimeout(
        hlsUrl,
        streamState,
        setStreamState,
        converted ? CONVERTED_LOAD_TIMEOUT_MS : DIRECT_LOAD_TIMEOUT_MS,
    );

    useEffect(() => {
        const video = videoRef.current;
        if (!video) return;

        if (Hls.isSupported()) {
            const hls = new Hls(
                converted
                    ? {
                          // hls.js's default manifest policy goes fatal at about 60 s (20 s per
                          // attempt, two timeout retries), before MediaMTX's 90 s cold start can
                          // finish. Let the converted-lane timeout decide instead.
                          manifestLoadPolicy: {
                              default: {
                                  maxTimeToFirstByteMs: Infinity,
                                  maxLoadTimeMs: CONVERTED_LOAD_TIMEOUT_MS,
                                  timeoutRetry: null,
                                  errorRetry: {
                                      maxNumRetry: 1,
                                      retryDelayMs: 1000,
                                      maxRetryDelayMs: 8000,
                                  },
                              },
                          },
                      }
                    : undefined,
            );
            hls.loadSource(hlsUrl);
            hls.attachMedia(video);
            hls.on(Hls.Events.MANIFEST_PARSED, () => {
                setStreamState("playing");
                video.play().catch(() => setStreamState("offline"));
            });
            hls.on(Hls.Events.ERROR, (_event, data) => {
                if (data.fatal) setStreamState("offline");
            });
            return () => {
                // Cleanup runs synchronously before React removes DOM nodes, so
                // document.pictureInPictureElement is still set if we were in PiP.
                // Transfer PiP to the backup video (pre-warmed by StreamPipManager)
                // so the stream survives the event panel closing.
                const backupVideo = pipRefs.backupVideoRef.current;
                if (video === document.pictureInPictureElement && backupVideo) {
                    if (backupVideo.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
                        backupVideo.requestPictureInPicture().catch(() => {
                            usePipStore.getState().setActivePipUrl(null);
                            document.exitPictureInPicture().catch(() => {});
                        });
                    } else {
                        // Backup not ready yet — close PiP cleanly rather than leaving a frozen frame.
                        usePipStore.getState().setActivePipUrl(null);
                        document.exitPictureInPicture().catch(() => {});
                    }
                }
                hls.destroy();
            };
        } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
            video.src = hlsUrl;
            const onCanPlay = () => {
                setStreamState("playing");
                video.play().catch(() => setStreamState("offline"));
            };
            const onError = () => setStreamState("offline");
            video.addEventListener("canplay", onCanPlay);
            video.addEventListener("error", onError);
            return () => {
                video.removeEventListener("canplay", onCanPlay);
                video.removeEventListener("error", onError);
                video.pause();
                video.src = "";
                video.load();
            };
        } else {
            let active = true;
            queueMicrotask(() => {
                if (active) setStreamState("offline");
            });
            return () => {
                active = false;
            };
        }
    }, [hlsUrl, converted]);

    useEffect(() => {
        const video = videoRef.current;
        if (!video) return;
        const onEnterPip = () => {
            // Verify this video is the actual PiP element before taking ownership —
            // guards against clobbering an active session on a concurrently-mounted VideoPlayer.
            if (document.pictureInPictureElement === video) {
                usePipStore.getState().setActivePipUrl(hlsUrl);
            }
        };
        // Only clear state if PiP is truly exiting (not transferring to the backup video).
        // During transfer, this listener is removed synchronously before the async
        // leavepictureinpicture event fires, so it won't run.
        // Don't clear if PiP transferred to another element (backup video).
        const onLeavePip = () => {
            if (document.pictureInPictureElement !== null) return;
            usePipStore.getState().setActivePipUrl(null);
        };
        video.addEventListener("enterpictureinpicture", onEnterPip);
        video.addEventListener("leavepictureinpicture", onLeavePip);
        return () => {
            video.removeEventListener("enterpictureinpicture", onEnterPip);
            video.removeEventListener("leavepictureinpicture", onLeavePip);
        };
    }, [hlsUrl]);

    return (
        <StreamVideoShell
            streamState={streamState}
            videoRef={videoRef}
            style={style}
            fill={fill}
            slow={slow}
        />
    );
}

export function HlsPlayer({
    url,
    streamKind,
    sourceId,
    objectId,
    attachmentId,
    style,
    fill,
}: HlsPlayerProps) {
    const { t } = useTranslation();
    const needsConversion = streamKind === "RTSP" || streamKind === "SRT";

    const { data, isLoading, isError } = useQuery({
        ...streamQueries.prepareStream(sourceId, objectId, attachmentId),
        enabled: needsConversion,
    });

    const showSpinner = useSpinDelay(isLoading, { delay: 500, minDuration: 200, ssr: false });
    const mutedColor = fill ? "whiteAlpha.800" : "fg.muted";
    const centered = fill ? { h: "full" } : {};

    if (!needsConversion) {
        // An http:// manifest on an https:// page is blocked with no actionable error.
        if (isBlockedMixedContent(url)) {
            return (
                <Center py={4} {...centered}>
                    <Text fontSize="sm" color={mutedColor} textAlign="center">
                        {t("events.cameraMixedContent")}
                    </Text>
                </Center>
            );
        }
        return <VideoPlayer hlsUrl={url} style={style} fill={fill} converted={false} />;
    }

    if (showSpinner) {
        return (
            <Center py={4} flexDir="column" gap={2} {...centered}>
                <Spinner size="sm" color={mutedColor} />
                <Text fontSize="xs" color={mutedColor}>
                    {t("events.fetchingStream")}
                </Text>
            </Center>
        );
    }

    if (isError || !data?.hlsUrl) {
        return (
            <Center py={fill ? 0 : undefined} {...centered}>
                <Text fontSize="xs" color={mutedColor}>
                    {t("events.streamPrepareFailed")}
                </Text>
            </Center>
        );
    }

    return <VideoPlayer hlsUrl={data.hlsUrl} style={style} fill={fill} converted />;
}

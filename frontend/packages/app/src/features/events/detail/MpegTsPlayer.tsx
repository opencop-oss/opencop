// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import mpegts from "mpegts.js";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Center, Text } from "@chakra-ui/react";
import { isBlockedMixedContent } from "./attachment.util";
import { StreamVideoShell } from "./StreamVideoShell";
import {
    DIRECT_LOAD_TIMEOUT_MS,
    useStreamLoadTimeout,
    type StreamState,
} from "./use-stream-load-timeout.hook";

// mpegts.js logs every demux/MSE step by default; keep only warnings and errors.
// applyConfig overwrites every field (unset ones become undefined), so merge onto the current config.
mpegts.LoggingControl.applyConfig({
    ...mpegts.LoggingControl.getConfig(),
    enableVerbose: false,
    enableDebug: false,
    enableInfo: false,
});

interface MpegTsPlayerProps {
    url: string;
    style?: React.CSSProperties;
    /** Fill the parent box, letterboxed on black, instead of taking the stream's own height. */
    fill?: boolean;
}

/**
 * Plays a live MPEG transport stream over HTTP in a `<video>` via mpegts.js (MSE). The browser's
 * native Picture-in-Picture control works, but there is no persistent handoff (that is built around
 * hls.js instances): the PiP window closes with the player.
 *
 * Mount it with `key={url}`: state is not reset when the URL changes. Any player error is
 * terminal by design, as for the other lanes; there is no reconnect.
 */
export function MpegTsPlayer({ url, style, fill }: MpegTsPlayerProps) {
    const { t } = useTranslation();
    const videoRef = useRef<HTMLVideoElement>(null);
    const [streamState, setStreamState] = useState<StreamState>("loading");
    const blockedByMixedContent = isBlockedMixedContent(url);
    const supported = mpegts.isSupported();
    const playable = supported && !blockedByMixedContent;

    const { slow } = useStreamLoadTimeout(url, streamState, setStreamState, DIRECT_LOAD_TIMEOUT_MS);

    useEffect(() => {
        const video = videoRef.current;
        if (!video || !playable) return;

        let current = true;
        const player = mpegts.createPlayer(
            { type: "mpegts", isLive: true, url },
            // A drawer can stay open for hours: drop played data and stay near the live edge
            // instead of letting the MSE buffer grow without bound.
            { autoCleanupSourceBuffer: true, liveBufferLatencyChasing: true },
        );
        player.attachMediaElement(video);
        player.on(mpegts.Events.ERROR, () => {
            if (current) setStreamState("offline");
        });
        const onPlaying = () => setStreamState("playing");
        video.addEventListener("playing", onPlaying);
        player.load();
        Promise.resolve(player.play()).catch((error: unknown) => {
            // Tearing the player down mid-start rejects play() with an AbortError.
            const aborted = error instanceof DOMException && error.name === "AbortError";
            if (current && !aborted) setStreamState("offline");
        });

        return () => {
            current = false;
            video.removeEventListener("playing", onPlaying);
            // Leave no frozen floating window behind once the player is gone.
            if (document.pictureInPictureElement === video) {
                document.exitPictureInPicture().catch(() => {});
            }
            player.pause();
            player.unload();
            player.detachMediaElement();
            player.destroy();
        };
    }, [url, playable]);

    if (blockedByMixedContent) {
        return (
            <Center py={4} h={fill ? "full" : undefined}>
                <Text fontSize="sm" color={fill ? "whiteAlpha.800" : "fg.muted"} textAlign="center">
                    {t("events.cameraMixedContent")}
                </Text>
            </Center>
        );
    }

    return (
        <StreamVideoShell
            streamState={supported ? streamState : "offline"}
            videoRef={videoRef}
            style={style}
            fill={fill}
            slow={slow}
        />
    );
}

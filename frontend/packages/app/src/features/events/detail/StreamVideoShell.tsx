// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useTranslation } from "react-i18next";
import { Box, Center, Spinner, Text, VisuallyHidden } from "@chakra-ui/react";
import type { StreamState } from "./use-stream-load-timeout.hook";

interface StreamVideoShellProps {
    streamState: StreamState;
    videoRef: React.Ref<HTMLVideoElement>;
    style?: React.CSSProperties;
    /** Fill the parent box (letterboxed on black) instead of taking the stream's own height. */
    fill?: boolean;
    /** Loading has run past the usual time; say it is still trying rather than looking stuck. */
    slow?: boolean;
}

/**
 * The `<video>` and its loading/offline chrome shared by the stream players. The video stays in
 * the DOM so the player engine can attach to it, but is hidden until the stream is playing.
 */
export function StreamVideoShell({
    streamState,
    videoRef,
    style,
    fill,
    slow,
}: StreamVideoShellProps) {
    const { t } = useTranslation();

    const mutedColor = fill ? "whiteAlpha.800" : "fg.muted";
    const videoStyle: React.CSSProperties = fill
        ? {
              position: "absolute",
              inset: 0,
              width: "100%",
              height: "100%",
              objectFit: "contain",
              visibility: streamState === "playing" ? "visible" : "hidden",
          }
        : {
              display: "block",
              maxWidth: "100%",
              width: "100%",
              height: "auto",
              visibility: streamState === "playing" ? "visible" : "hidden",
              position: streamState === "playing" ? "static" : "absolute",
          };
    const overlay = fill ? { position: "absolute" as const, inset: 0 } : {};

    return (
        <Box
            position="relative"
            w={fill ? "full" : undefined}
            h={fill ? "full" : undefined}
            style={style}
        >
            <VisuallyHidden aria-live="polite">
                {streamState === "offline"
                    ? t("events.streamCheckUrl")
                    : slow && streamState === "loading"
                      ? t("events.streamStillConnecting")
                      : ""}
            </VisuallyHidden>
            <video
                ref={videoRef}
                aria-label={t("events.liveStream")}
                controls
                autoPlay
                muted
                playsInline
                style={videoStyle}
            />

            {streamState === "loading" && (
                <Center py={fill ? 0 : 4} flexDir="column" gap={2} {...overlay}>
                    <Spinner size="sm" color={mutedColor} />
                    <Text fontSize="xs" color={mutedColor}>
                        {slow ? t("events.streamStillConnecting") : t("events.fetchingStream")}
                    </Text>
                </Center>
            )}

            {streamState === "offline" && (
                <Center py={fill ? 0 : 4} {...overlay}>
                    <Text fontSize="sm" color={mutedColor} textAlign="center">
                        {t("events.streamCheckUrl")}
                    </Text>
                </Center>
            )}
        </Box>
    );
}

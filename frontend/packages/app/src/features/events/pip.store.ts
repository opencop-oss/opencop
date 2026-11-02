// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * PiP handoff protocol — coordinates VideoPlayer (HlsPlayer.tsx) and StreamPipManager.tsx.
 *
 * States:
 *   idle            activePipUrl = null, backupHls = null
 *   pre-warm        user entered PiP; activePipUrl = URL; StreamPipManager loads a backup HLS
 *   transfer        VideoPlayer unmounts while in PiP; cleanup calls backupVideo.requestPictureInPicture()
 *   backup-active   PiP lives on the backup video; backupHls keeps the stream running
 *   backup-leaves   user closes PiP from the OS chrome; leavepictureinpicture on backup → idle
 *
 * Guards and the races they defend:
 *   HlsPlayer onEnterPip  — checks document.pictureInPictureElement === video so a concurrently-mounted
 *                           VideoPlayer can't clobber an already-active session.
 *   HlsPlayer onLeavePip  — checks document.pictureInPictureElement !== null so the leave event that
 *                           fires during a transfer (before the backup's enter event) doesn't clear state.
 *   HlsPlayer cleanup     — checks backupVideo.readyState >= HAVE_CURRENT_DATA before transferring;
 *                           falls back to exitPictureInPicture() on failure or unready backup.
 *   StreamPipManager cleanup — checks pipRefs.backupHls === hls before destroying to prevent a
 *                           double-destroy when leavepictureinpicture already ran teardown first.
 *
 * Effect-declaration order in VideoPlayer is load-bearing: the PiP event-listener effect (enter/leave)
 * must be declared AFTER the HLS effect so React runs its cleanup first on unmount — removing the
 * leavepictureinpicture listener before the async browser event fires during the PiP transfer.
 */

import { create } from "zustand";
import type Hls from "hls.js";
import type { RefObject } from "react";

interface PipStore {
    activePipUrl: string | null;
    setActivePipUrl: (url: string | null) => void;
}

export const usePipStore = create<PipStore>()((set) => ({
    activePipUrl: null,
    setActivePipUrl: (activePipUrl) => set({ activePipUrl }),
}));

// Module-level mutable — the legitimate escape hatch here: VideoPlayer needs to read
// these handles synchronously during its own useEffect cleanup (before DOM removal),
// which neither Zustand state nor a same-component useRef can satisfy across components.
export const pipRefs: {
    backupVideoRef: RefObject<HTMLVideoElement | null>;
    backupHls: Hls | null;
} = {
    backupVideoRef: { current: null },
    backupHls: null,
};

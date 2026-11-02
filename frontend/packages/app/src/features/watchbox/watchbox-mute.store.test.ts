// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { WATCHBOX_MUTE_STORAGE_KEY, useWatchboxMuteStore } from "./watchbox-mute.store";

function resetStore() {
    useWatchboxMuteStore.setState({ mutedIdsByUser: {} });
    localStorage.removeItem(WATCHBOX_MUTE_STORAGE_KEY);
}

beforeEach(resetStore);
afterEach(resetStore);

const USER_A = "user-a";
const USER_B = "user-b";

describe("watchbox mute store", () => {
    it("mutes a watchbox for one user without affecting others", () => {
        const { setMuted, isMuted } = useWatchboxMuteStore.getState().actions;
        setMuted(USER_A, "wb-1", true);

        expect(isMuted(USER_A, "wb-1")).toBe(true);
        expect(isMuted(USER_B, "wb-1")).toBe(false);
    });

    it("unmutes a watchbox while leaving the user's other mutes intact", () => {
        const { setMuted, isMuted } = useWatchboxMuteStore.getState().actions;
        setMuted(USER_A, "wb-1", true);
        setMuted(USER_A, "wb-2", true);

        setMuted(USER_A, "wb-1", false);

        expect(isMuted(USER_A, "wb-1")).toBe(false);
        expect(isMuted(USER_A, "wb-2")).toBe(true);
    });

    it("leaves other users' mutes untouched when one user's set changes", () => {
        const { setMuted, isMuted } = useWatchboxMuteStore.getState().actions;
        setMuted(USER_A, "wb-1", true);
        setMuted(USER_B, "wb-1", true);

        setMuted(USER_A, "wb-1", false);

        expect(isMuted(USER_A, "wb-1")).toBe(false);
        expect(isMuted(USER_B, "wb-1")).toBe(true);
    });

    it("persists mutes under the namespaced storage key", () => {
        useWatchboxMuteStore.getState().actions.setMuted(USER_A, "wb-1", true);

        const persisted = JSON.parse(localStorage.getItem(WATCHBOX_MUTE_STORAGE_KEY)!);
        expect(persisted.state.mutedIdsByUser).toEqual({ [USER_A]: ["wb-1"] });
    });

    it("rehydrates a persisted mute set for its user", async () => {
        localStorage.setItem(
            WATCHBOX_MUTE_STORAGE_KEY,
            JSON.stringify({ state: { mutedIdsByUser: { [USER_A]: ["wb-1"] } }, version: 0 }),
        );

        await useWatchboxMuteStore.persist.rehydrate();

        expect(useWatchboxMuteStore.getState().actions.isMuted(USER_A, "wb-1")).toBe(true);
    });

    it("drops non-array entries, non-string ids, and duplicates from a malformed persisted blob", async () => {
        localStorage.setItem(
            WATCHBOX_MUTE_STORAGE_KEY,
            JSON.stringify({
                state: {
                    mutedIdsByUser: {
                        [USER_A]: ["wb-1", "wb-1", 42, null, "wb-2"],
                        [USER_B]: "not-an-array",
                    },
                },
                version: 0,
            }),
        );

        await useWatchboxMuteStore.persist.rehydrate();

        expect(useWatchboxMuteStore.getState().mutedIdsByUser).toEqual({
            [USER_A]: ["wb-1", "wb-2"],
        });
    });

    it("falls back to an empty map for a completely malformed persisted value", async () => {
        localStorage.setItem(
            WATCHBOX_MUTE_STORAGE_KEY,
            JSON.stringify({ state: { mutedIdsByUser: "garbage" }, version: 0 }),
        );

        await useWatchboxMuteStore.persist.rehydrate();

        expect(useWatchboxMuteStore.getState().mutedIdsByUser).toEqual({});
    });
});

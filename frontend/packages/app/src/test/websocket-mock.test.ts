// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { OCMPClient, SESSION_ENDED, websocketMock } from "./websocket-mock";

describe("websocketMock", () => {
    it("drives the active client's close callback", () => {
        const onClose = vi.fn();
        new OCMPClient("ws://test.invalid", { onClose });

        const event = websocketMock.close(SESSION_ENDED, "Session ended");

        expect(onClose).toHaveBeenCalledOnce();
        expect(onClose).toHaveBeenCalledWith(event);
    });

    it("drops the active callback when reset", () => {
        const onClose = vi.fn();
        new OCMPClient("ws://test.invalid", { onClose });

        websocketMock.reset();
        websocketMock.close(SESSION_ENDED, "Session ended");

        expect(onClose).not.toHaveBeenCalled();
    });
});

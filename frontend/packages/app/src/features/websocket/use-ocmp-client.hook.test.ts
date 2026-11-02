// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
// This test exercises the websocket singleton hook in isolation and self-mocks
// @opencop/websocket. The ~/test harness mounts a WebSocketProvider that uses this
// very hook, which would fight the ref-count assertions here, so it deliberately
// renders raw (not through the harness).
// eslint-disable-next-line no-restricted-syntax
import { renderHook, act, waitFor } from "@testing-library/react";
import { useOCMPClient, clearClient } from "./use-ocmp-client.hook";
import { SESSION_ENDED, type OCMPClientOptions } from "@opencop/websocket";

// Hoisted mocks - available when vi.mock factory runs
const { mockClient, getCapturedOptions, setCapturedOptions, isTerminalCloseMock } = vi.hoisted(
    () => {
        const mockClient = {
            disconnect: vi.fn(),
            subscribe: vi.fn(),
            unsubscribe: vi.fn(),
            send: vi.fn(),
            isOpen: vi.fn(() => true),
            isClosed: vi.fn(() => false),
            isConnecting: vi.fn(() => false),
        };
        let capturedOptions: OCMPClientOptions | undefined;
        return {
            mockClient,
            getCapturedOptions: () => capturedOptions,
            setCapturedOptions: (opts: OCMPClientOptions | undefined) => {
                capturedOptions = opts;
            },
            isTerminalCloseMock: vi.fn((code: number) => code === 1000 || code === 4401),
        };
    },
);

vi.mock("@opencop/websocket", () => ({
    OCMPClient: vi.fn(function (_url: string, options?: OCMPClientOptions) {
        setCapturedOptions(options);
        setTimeout(() => options?.onOpen?.(), 0);
        return mockClient;
    }),
    MAX_RECONNECT_ATTEMPTS: 10,
    SESSION_ENDED: 4401,
    isTerminalClose: isTerminalCloseMock,
}));

describe("useOCMPClient", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        setCapturedOptions(undefined);
    });

    afterEach(() => {
        clearClient();
    });

    describe("singleton behavior", () => {
        it("should create only one client for multiple consumers", async () => {
            const { OCMPClient } = await import("@opencop/websocket");

            const { result: result1 } = renderHook(() => useOCMPClient());
            const { result: result2 } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(result1.current[0]).toBe(result2.current[0]);
            });

            expect(OCMPClient).toHaveBeenCalledTimes(1);
        });
    });

    describe("connection state", () => {
        it("should start with connecting status", () => {
            const { result } = renderHook(() => useOCMPClient());
            expect(result.current[1].status).toBe("connecting");
        });

        it("should update to connected when onOpen is called", async () => {
            const { result } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(result.current[1].status).toBe("connected");
            });
        });

        it.each([1006, 1011, 4403])(
            "should update to reconnecting when onClose is called with code %i",
            async (code) => {
                const { result } = renderHook(() => useOCMPClient());

                await waitFor(() => {
                    expect(result.current[1].status).toBe("connected");
                });

                act(() => {
                    getCapturedOptions()?.onClose?.({ code } as CloseEvent);
                });

                expect(result.current[1].status).toBe("reconnecting");
            },
        );

        it("should treat SESSION_ENDED as disconnected and forward the original event", async () => {
            const onClose = vi.fn();
            const { result } = renderHook(() => useOCMPClient({ onClose }));

            await waitFor(() => {
                expect(result.current[1].status).toBe("connected");
            });

            const event = { code: SESSION_ENDED, reason: "Session ended" } as CloseEvent;
            act(() => {
                getCapturedOptions()?.onClose?.(event);
            });

            expect(result.current[1]).toMatchObject({
                status: "disconnected",
                reconnectAttempt: 0,
            });
            expect(onClose).toHaveBeenCalledOnce();
            expect(onClose).toHaveBeenCalledWith(event);
        });

        it("should update to disconnected when onClose is called with code 1000", async () => {
            const { result } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(result.current[1].status).toBe("connected");
            });

            act(() => {
                getCapturedOptions()?.onClose?.({ code: 1000 } as CloseEvent);
            });

            expect(result.current[1].status).toBe("disconnected");
        });

        it("should delegate terminal close classification to the WebSocket package", async () => {
            const { result } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(result.current[1].status).toBe("connected");
            });

            isTerminalCloseMock.mockReturnValueOnce(true);
            act(() => {
                getCapturedOptions()?.onClose?.({ code: 4499 } as CloseEvent);
            });

            expect(isTerminalCloseMock).toHaveBeenCalledWith(4499);
            expect(result.current[1].status).toBe("disconnected");
        });

        it("should track reconnect attempts", async () => {
            const { result } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(result.current[1].status).toBe("connected");
            });

            act(() => {
                getCapturedOptions()?.onRetry?.(1);
            });

            expect(result.current[1].status).toBe("reconnecting");
            expect(result.current[1].reconnectAttempt).toBe(1);

            act(() => {
                getCapturedOptions()?.onRetry?.(2);
            });

            expect(result.current[1].reconnectAttempt).toBe(2);
        });

        it("should update to failed when max retries reached", async () => {
            const { result } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(result.current[1].status).toBe("connected");
            });

            act(() => {
                getCapturedOptions()?.onMaxRetries?.();
            });

            expect(result.current[1].status).toBe("failed");
            expect(result.current[1].error).toBeInstanceOf(Error);
            expect(result.current[1].error?.message).toBe("Max reconnection attempts reached");
        });

        it("should reset state on successful reconnect", async () => {
            const { result } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(result.current[1].status).toBe("connected");
            });

            act(() => {
                getCapturedOptions()?.onClose?.({ code: 1006 } as CloseEvent);
                getCapturedOptions()?.onRetry?.(3);
            });

            expect(result.current[1].reconnectAttempt).toBe(3);

            act(() => {
                getCapturedOptions()?.onReconnect?.();
            });

            expect(result.current[1].status).toBe("connected");
            expect(result.current[1].reconnectAttempt).toBe(0);
            expect(result.current[1].error).toBeNull();
        });
    });

    describe("reference counting", () => {
        it("should not disconnect when one consumer unmounts but another exists", async () => {
            const { unmount: unmount1 } = renderHook(() => useOCMPClient());
            renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(mockClient.disconnect).not.toHaveBeenCalled();
            });

            unmount1();
            expect(mockClient.disconnect).not.toHaveBeenCalled();
        });

        it("should disconnect when all consumers unmount", async () => {
            const { unmount: unmount1 } = renderHook(() => useOCMPClient());
            const { unmount: unmount2 } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(mockClient.disconnect).not.toHaveBeenCalled();
            });

            unmount1();
            expect(mockClient.disconnect).not.toHaveBeenCalled();

            unmount2();
            expect(mockClient.disconnect).toHaveBeenCalledTimes(1);
        });
    });

    describe("shared state across consumers", () => {
        it("should share connection state between consumers", async () => {
            const { result: result1 } = renderHook(() => useOCMPClient());
            const { result: result2 } = renderHook(() => useOCMPClient());

            await waitFor(() => {
                expect(result1.current[1].status).toBe("connected");
                expect(result2.current[1].status).toBe("connected");
            });

            act(() => {
                getCapturedOptions()?.onRetry?.(5);
            });

            expect(result1.current[1].reconnectAttempt).toBe(5);
            expect(result2.current[1].reconnectAttempt).toBe(5);
        });
    });
});

// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { i18n } from "~/i18n/config";
import { API_BASE, HttpResponse, act, http, renderHook, server, waitFor } from "~/test";
import { useConnectionIntegration } from "./use-connection-integration.hook";

const NATIVE_ID = "5b96caf4-20dd-4a8a-8f1b-09fb594265be";

describe("useConnectionIntegration", () => {
    afterEach(async () => {
        await act(() => i18n.changeLanguage("en"));
    });

    it("names the integration in the user's language", async () => {
        server.use(
            http.get(`${API_BASE}/integrations`, () =>
                HttpResponse.json([
                    {
                        integrationId: NATIVE_ID,
                        schemaVersion: 1,
                        name: "Native",
                        nameKey: `integrations.${NATIVE_ID}.name`,
                        description: "",
                        available: true,
                    },
                ]),
            ),
        );
        const { result } = renderHook(() =>
            useConnectionIntegration({ integrationId: NATIVE_ID, schemaVersion: 1 }, null),
        );
        await waitFor(() => expect(result.current?.name).toBe("Native"));

        await act(() => i18n.changeLanguage("fr"));

        expect(result.current?.name).toBe("Natif");
    });
});

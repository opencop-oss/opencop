// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { AssignedDataSource } from "@opencop/api";
import { API_BASE, HttpResponse, http, render, screen, server, waitFor, within } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { UserDataSourcesSection } from "./UserDataSourcesSection";

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

const USER_ID = "user-1";
const ASSIGNED_SOURCES = [
    {
        serverId: "tcp-source",
        serverName: "TCP Write",
        serverState: "CONNECTED",
        role: "OWNER",
        directRole: "OWNER",
        access: "W",
        transportType: "TCP",
    },
    {
        serverId: "udp-source",
        serverName: "UDP Write",
        serverState: "CONNECTED",
        role: "OWNER",
        directRole: null,
        access: "W",
        transportType: "UDP",
    },
    {
        serverId: "kafka-source",
        serverName: "Kafka Read & Write",
        serverState: "CONNECTED",
        role: "VIEWER",
        directRole: "VIEWER",
        access: "RW",
        transportType: "KAFKA",
    },
    {
        serverId: "rtsp-source",
        serverName: "RTSP Read",
        serverState: "CONNECTED",
        role: "VIEWER",
        directRole: null,
        access: "R",
        transportType: "RTSP",
    },
] satisfies AssignedDataSource[];

describe("UserDataSourcesSection", () => {
    it("renders exact summary values and transport-only warnings without detail fan-out", async () => {
        let assignedListRequests = 0;
        let poolListRequests = 0;
        let detailRequests = 0;
        server.use(
            http.get(`${API_BASE}/users/${USER_ID}/sources`, () => {
                assignedListRequests += 1;
                return HttpResponse.json(ASSIGNED_SOURCES);
            }),
            http.get(`${API_BASE}/admin/connections`, () => {
                poolListRequests += 1;
                return HttpResponse.json([]);
            }),
            http.get(`${API_BASE}/connections/:sourceId`, () => {
                detailRequests += 1;
                return HttpResponse.json({});
            }),
            http.get(`${API_BASE}/admin/profile/mapping/:mapperId`, () => {
                detailRequests += 1;
                return HttpResponse.json({});
            }),
        );

        render(<UserDataSourcesSection userId={USER_ID} />);

        expect(await screen.findByRole("columnheader", { name: "Protocol" })).toBeVisible();
        expect(screen.getByRole("columnheader", { name: "Access Mode" })).toBeVisible();

        const kafkaRow = (await screen.findByText("Kafka Read & Write")).closest("tr");
        expect(kafkaRow).not.toBeNull();
        expect(within(kafkaRow!).getByText("KAFKA")).toBeVisible();
        expect(within(kafkaRow!).getByText("Read & Write")).toBeVisible();
        expect(
            within(kafkaRow!).getByRole("img", {
                name: "Read & Write is incompatible with Kafka.",
            }),
        ).toBeVisible();

        const tcpRow = screen.getByText("TCP Write").closest("tr");
        expect(tcpRow).not.toBeNull();
        expect(within(tcpRow!).getByText("TCP")).toBeVisible();
        expect(within(tcpRow!).getByText("Write")).toBeVisible();
        expect(within(tcpRow!).queryByRole("img")).toBeNull();

        await waitFor(() => {
            expect(assignedListRequests).toBe(1);
            expect(poolListRequests).toBe(1);
        });
        expect(detailRequests).toBe(0);
    });
});

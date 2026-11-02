// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { IntegrationConnectionSetup } from "@opencop/api";
import { i18n } from "~/i18n/config";
import {
    API_BASE,
    HttpResponse,
    act,
    http,
    render,
    screen,
    server,
    userEvent,
    within,
    waitFor,
} from "~/test";
import { NATIVE_INTEGRATION_ID } from "../connection-setup/tcp-setup.model";
import { AddDataSourceDialog } from "./AddDataSourceDialog";

type AppMetadataResponse = {
    features?: Array<{ name: string; enabled: boolean }>;
} | null;

let mockAppMetadata: AppMetadataResponse = {
    features: [{ name: "rtsp", enabled: true }],
};

const V2_CONNECTIONS = `${API_BASE.replace(/\/v1$/, "/v2")}/connections`;
const RTSP_EXTRACTOR_ID = "9a4c2e71-5b3d-4f86-a1c0-7e2d9b5f3a48";
const KLV_MAPPER_ID = "d8ba1624-ccd2-4bca-8d4b-241b38f246c4";

const NATIVE_CATALOG = [
    {
        integrationId: NATIVE_INTEGRATION_ID,
        schemaVersion: 1,
        name: "Native",
        description: "",
        available: true,
    },
];

/** Native's RTSP setup as Core serves it: one stream extractor, decoded only by the KLV mapper. */
const NATIVE_RTSP_SETUP: IntegrationConnectionSetup = {
    integrationId: NATIVE_INTEGRATION_ID,
    schemaVersion: 1,
    protocols: [
        {
            kind: "RTSP",
            accessModes: ["R"],
            extractors: [
                {
                    id: RTSP_EXTRACTOR_ID,
                    label: "RTSP stream",
                    fields: [
                        { name: "url", label: "Stream URL", required: true, kind: "TEXT" },
                        {
                            name: "latitude",
                            label: "Camera latitude",
                            required: false,
                            kind: "TEXT",
                        },
                        {
                            name: "longitude",
                            label: "Camera longitude",
                            required: false,
                            kind: "TEXT",
                        },
                    ],
                },
            ],
            ingress: [{ type: "MAPPER", mapperId: KLV_MAPPER_ID }],
        },
    ],
};

const MAPPERS = [
    { id: "mapper-json", name: "JSON mapper", description: "", dynamicMapper: {} },
    { id: KLV_MAPPER_ID, name: "KLV Metadata", description: "", dynamicMapper: {} },
];

const CREATED_RTSP = {
    kind: "RTSP",
    id: "source-1",
    name: "Camera One",
    state: "CONNECTING",
    access: "R",
    transportType: "RTSP",
    defaultVisible: true,
};

type User = ReturnType<typeof userEvent.setup>;

async function renderDialog(onClose = vi.fn()) {
    const rendered = render(<AddDataSourceDialog open={true} onClose={onClose} />);
    await waitFor(() =>
        expect(rendered.queryClient.getQueryState(["app-metadata"])?.status).toBe("success"),
    );
    return rendered;
}

async function waitForStep(name: string) {
    await waitFor(() =>
        expect(screen.getByRole("tab", { name })).toHaveAttribute("aria-selected", "true"),
    );
}

/** From Data Flow to RTSP's Connection step, through Native. */
async function toRtspConnection(user: User, name = "Camera One") {
    await user.click(screen.getByRole("radio", { name: "RTSP" }));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(await screen.findByRole("combobox", { name: "Integration" }));
    await user.click(await screen.findByRole("option", { name: "Native" }));
    await user.type(await screen.findByLabelText(/^Server Name/), name);
    await waitFor(() => expect(screen.getByRole("radio", { name: "Read" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitForStep("Connection");
}

beforeEach(() => {
    mockAppMetadata = { features: [{ name: "rtsp", enabled: true }] };
    server.use(
        http.get(`${API_BASE}/app-metadata`, () => HttpResponse.json(mockAppMetadata)),
        http.get(`${API_BASE}/integrations`, () => HttpResponse.json(NATIVE_CATALOG)),
        http.get(
            `${API_BASE}/integrations/${NATIVE_INTEGRATION_ID}/versions/1/connection/setup`,
            () => HttpResponse.json(NATIVE_RTSP_SETUP),
        ),
        http.get(`${API_BASE}/profile/mapping`, () => HttpResponse.json(MAPPERS)),
    );
});

describe("AddDataSourceDialog — Data Flow", () => {
    it("asks TCP for no access on Data Flow and continues into the integration steps", async () => {
        await renderDialog();

        expect(screen.getByRole("radiogroup", { name: "Transport type" })).toBeInTheDocument();
        expect(screen.getByRole("radio", { name: "TCP" })).toBeChecked();
        expect(screen.queryByRole("radiogroup", { name: "Access Mode" })).toBeNull();
        expect(screen.getByRole("tab", { name: "Integration" })).toBeInTheDocument();
    });
});

describe("AddDataSourceDialog — RTSP through Native", () => {
    afterEach(async () => {
        await act(() => i18n.changeLanguage("en"));
    });

    it("creates an RTSP source from Native's stream fields and the KLV mapper", async () => {
        const bodies: unknown[] = [];
        server.use(
            http.post(V2_CONNECTIONS, async ({ request }) => {
                bodies.push(await request.json());
                return HttpResponse.json(CREATED_RTSP, { status: 201 });
            }),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await toRtspConnection(user);
        await user.type(screen.getByLabelText(/^Stream URL/), "rtsp://camera.example/stream");
        await user.type(screen.getByLabelText(/^Camera latitude/), "48.85");
        await user.type(screen.getByLabelText(/^Camera longitude/), "2.35");
        await user.click(screen.getByRole("button", { name: "Next" }));
        // The one mapper RTSP permits is chosen without asking, named once the mapper list loads.
        await waitFor(() =>
            expect(screen.getByRole("combobox", { name: "Mapper" })).toHaveTextContent(
                "KLV Metadata",
            ),
        );
        await user.click(screen.getByRole("button", { name: "Next" }));
        await waitForStep("Review");
        const dataFlowReview = screen.getByRole("group", { name: "Data Flow" });
        expect(within(dataFlowReview).getByText("RTSP")).toBeVisible();
        expect(within(dataFlowReview).getByText("Read")).toBeVisible();
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        // One creation, on v2. The harness fails any request without a handler, so a profile read
        // or a v1 creation would fail this test.
        expect(bodies).toEqual([
            {
                kind: "RTSP",
                name: "Camera One",
                access: "R",
                integration: { id: NATIVE_INTEGRATION_ID, schemaVersion: 1 },
                extractorId: RTSP_EXTRACTOR_ID,
                values: {
                    url: "rtsp://camera.example/stream",
                    latitude: "48.85",
                    longitude: "2.35",
                },
                mapperId: KLV_MAPPER_ID,
            },
        ]);
    });

    it.each([
        {
            detailCode: "UNSUPPORTED_RTSP_SCHEME",
            message: "RTSP URL must use rtsp:// or rtsps:// scheme",
            url: "https://camera.example/stream",
            language: "en",
            expectedMessage: "RTSP URL must use rtsp:// or rtsps:// scheme",
        },
        {
            detailCode: "RTSP_URL_QUERY",
            message: "RTSP URL must not contain query parameters",
            url: "rtsp://camera.example/s?token=1",
            language: "tw",
            expectedMessage: "RTSP URL 不得包含查詢參數",
        },
        {
            detailCode: "RTSP_URL_CREDENTIALS",
            message: "RTSP URL must not contain credentials — use Vault instead",
            url: "rtsp://user:secret@camera.example/stream",
            language: "fr",
            expectedMessage:
                "L'URL RTSP ne doit pas contenir d'identifiants — utilisez Vault à la place",
        },
        {
            detailCode: "FUTURE_RTSP_RULE",
            message: "This stream is not supported by this extractor",
            url: "rtsp://camera.example/stream",
            language: "fr",
            expectedMessage: "This stream is not supported by this extractor",
        },
    ])(
        "puts Core's $detailCode rejection back on the Stream URL field in $language",
        async ({ detailCode, message, url, language, expectedMessage }) => {
            server.use(
                http.post(V2_CONNECTIONS, () =>
                    HttpResponse.json(
                        { code: "CONNECTION_INPUT_INVALID", message, detailCode, field: "url" },
                        { status: 400 },
                    ),
                ),
            );
            const onClose = vi.fn();
            const user = userEvent.setup();
            await renderDialog(onClose);

            await toRtspConnection(user);
            await user.type(screen.getByLabelText(/^Stream URL/), url);
            await user.click(screen.getByRole("button", { name: "Next" }));
            await screen.findByRole("combobox", { name: "Mapper" });
            await user.click(screen.getByRole("button", { name: "Next" }));
            await waitForStep("Review");
            await act(() => i18n.changeLanguage(language));
            await user.click(screen.getByRole("button", { name: i18n.t("common.confirm") }));

            expect(await screen.findByText(expectedMessage)).toBeVisible();
            expect(
                screen.getByRole("tab", {
                    name: `${i18n.t("admin.connection")} ${i18n.t("connectionSetup.stepFailed")}`,
                }),
            ).toHaveAttribute("aria-selected", "true");
            await waitFor(() => expect(screen.getByLabelText(/^Stream URL/)).toHaveFocus());
            expect(onClose).not.toHaveBeenCalled();
        },
    );
});

describe("AddDataSourceDialog — RTSP feature flag", () => {
    it("marks the RTSP option as disabled when the flag is off", async () => {
        mockAppMetadata = { features: [{ name: "rtsp", enabled: false }] };
        await renderDialog();
        const rtspRadio = screen.getByRole("radio", { hidden: true, name: /rtsp/i });
        expect(rtspRadio).toBeDisabled();
    });

    it("marks the RTSP option as disabled when the feature list is missing", async () => {
        mockAppMetadata = {};
        await renderDialog();
        const rtspRadio = screen.getByRole("radio", { hidden: true, name: /rtsp/i });
        expect(rtspRadio).toBeDisabled();
    });

    it("enables RTSP by name while ignoring unknown entries", async () => {
        mockAppMetadata = {
            features: [
                { name: "future-feature", enabled: true },
                { name: "rtsp", enabled: true },
            ],
        };
        await renderDialog();
        const rtspRadio = screen.getByRole("radio", { hidden: true, name: /rtsp/i });
        expect(rtspRadio).toBeEnabled();
    });

    it("always permits Kafka regardless of runtime metadata", async () => {
        mockAppMetadata = null;
        await renderDialog();
        const kafkaRadio = screen.getByRole("radio", { hidden: true, name: /kafka/i });
        expect(kafkaRadio).toBeEnabled();
    });
});

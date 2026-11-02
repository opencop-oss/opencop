// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { IntegrationConnectionSetup } from "@opencop/api";
import { i18n } from "~/i18n/config";
import {
    API_BASE,
    HttpResponse,
    act,
    fireEvent,
    http,
    render,
    screen,
    server,
    userEvent,
    waitFor,
    within,
} from "~/test";
import { AddDataSourceDialog } from "../add-data-source/AddDataSourceDialog";
import { NATIVE_INTEGRATION_ID } from "./tcp-setup.model";

const V2_CONNECTIONS = `${API_BASE.replace(/\/v1$/, "/v2")}/connections`;
const NATIVE_ID = NATIVE_INTEGRATION_ID;
const TAK_ID = "tak-integration";
const WRITER_ID = "writer-integration";
const JSON_MAPPER_ID = "mapper-json";
const COT_MAPPER_ID = "mapper-cot";
const COT_EGRESS_ID = "egress-cot";
const JSON_EGRESS_ID = "egress-json";
const COT_FRAMING_ID = "framing-cot";

const CATALOG = [
    {
        integrationId: NATIVE_ID,
        schemaVersion: 1,
        name: "Native",
        description: "",
        available: true,
    },
    { integrationId: TAK_ID, schemaVersion: 1, name: "TAK", description: "", available: true },
    {
        integrationId: WRITER_ID,
        schemaVersion: 1,
        name: "Writer",
        description: "",
        available: true,
    },
    {
        integrationId: "retired-integration",
        schemaVersion: 3,
        name: "Retired plugin",
        description: "",
        available: false,
    },
];

const HOST_PORT = [
    { name: "host", label: "host", required: true, kind: "TEXT", standardKey: "HOST" },
    { name: "port", label: "port", required: true, kind: "TEXT", standardKey: "PORT" },
] as const;

const NATIVE_SETUP: IntegrationConnectionSetup = {
    integrationId: NATIVE_ID,
    schemaVersion: 1,
    protocols: [
        {
            kind: "TCP",
            extractors: [
                { id: "extractor-plain", label: "Plain TCP", fields: [...HOST_PORT] },
                {
                    id: "extractor-mtls",
                    label: "TCP with mutual TLS",
                    fields: [
                        {
                            name: "host",
                            label: "host",
                            required: true,
                            kind: "TEXT",
                            standardKey: "HOST",
                        },
                    ],
                },
            ],
            accessModes: ["R", "W", "RW"],
            ingress: [{ type: "ANY_MAPPER_CUSTOM_FRAMING" }],
            egress: { anyMapper: true, allowedMappers: [] },
            framings: [],
        },
        {
            kind: "UDP",
            extractors: [
                {
                    id: "extractor-udp",
                    label: "UDP listener",
                    fields: [
                        { ...HOST_PORT[0] },
                        { name: "listenPort", label: "Listen port", required: true, kind: "TEXT" },
                    ],
                },
            ],
            accessModes: ["R"],
            ingress: [{ type: "ANY_MAPPER" }],
        },
        {
            kind: "KAFKA",
            extractors: [
                {
                    id: "extractor-kafka",
                    label: "Kafka producer",
                    fields: [
                        {
                            name: "producerByteRate",
                            label: "Producer byte rate (bytes/s)",
                            required: false,
                            kind: "TEXT",
                        },
                    ],
                },
            ],
            accessModes: ["R"],
            ingress: [{ type: "ANY_MAPPER" }],
        },
    ],
};

const TAK_SETUP: IntegrationConnectionSetup = {
    integrationId: TAK_ID,
    schemaVersion: 1,
    protocols: [
        {
            kind: "TCP",
            extractors: [
                {
                    id: "extractor-zip",
                    label: "TAK credentials package",
                    fields: [
                        {
                            name: "credentialsZip",
                            label: "Credentials package",
                            required: true,
                            kind: "FILE",
                            extensions: [".zip"],
                            mediaTypes: ["application/zip"],
                        },
                    ],
                },
            ],
            accessModes: ["R", "W", "RW"],
            ingress: [{ type: "EXACT_PAIR", mapperId: COT_MAPPER_ID, framingId: COT_FRAMING_ID }],
            egress: { anyMapper: false, allowedMappers: [COT_EGRESS_ID] },
            framings: [{ id: COT_FRAMING_ID, mode: "delimiterInclude" }],
        },
    ],
};

/**
 * Reports Write only. Today's contract cannot carry this for a creatable connection — every TCP
 * request needs a framing the ingress rules permit — so it only exercises the access offer.
 */
const WRITER_SETUP: IntegrationConnectionSetup = {
    integrationId: WRITER_ID,
    schemaVersion: 1,
    protocols: [
        {
            kind: "TCP",
            extractors: [
                { id: "extractor-writer", label: "Writer endpoint", fields: [...HOST_PORT] },
            ],
            accessModes: ["W"],
            ingress: [],
            egress: { anyMapper: false, allowedMappers: [COT_EGRESS_ID] },
            framings: [],
        },
    ],
};

function mapper(id: string, name: string) {
    return { id, name, description: "", dynamicMapper: {} };
}

const INGRESS_MAPPERS = [
    mapper(JSON_MAPPER_ID, "JSON mapper"),
    mapper(COT_MAPPER_ID, "CoT mapper"),
];
const EGRESS_MAPPERS = [mapper(JSON_EGRESS_ID, "JSON egress"), mapper(COT_EGRESS_ID, "CoT egress")];

const CREATED = {
    kind: "TCP",
    id: "source-1",
    name: "TCP source",
    state: "CONNECTED",
    access: "R",
    transportType: "TCP",
    defaultVisible: true,
};

beforeEach(() => {
    server.use(
        http.get(`${API_BASE}/app-metadata`, () => HttpResponse.json({ features: [] })),
        http.get(`${API_BASE}/integrations`, () => HttpResponse.json(CATALOG)),
        http.get(`${API_BASE}/integrations/${NATIVE_ID}/versions/1/connection/setup`, () =>
            HttpResponse.json(NATIVE_SETUP),
        ),
        http.get(`${API_BASE}/integrations/${TAK_ID}/versions/1/connection/setup`, () =>
            HttpResponse.json(TAK_SETUP),
        ),
        http.get(`${API_BASE}/integrations/${WRITER_ID}/versions/1/connection/setup`, () =>
            HttpResponse.json(WRITER_SETUP),
        ),
        http.get(`${API_BASE}/profile/mapping`, ({ request }) =>
            HttpResponse.json(
                new URL(request.url).searchParams.get("qualifier") === "EGRESS"
                    ? EGRESS_MAPPERS
                    : INGRESS_MAPPERS,
            ),
        ),
    );
});

type User = ReturnType<typeof userEvent.setup>;

async function renderDialog(onClose = vi.fn()) {
    const rendered = render(<AddDataSourceDialog open onClose={onClose} />);
    await waitFor(() =>
        expect(rendered.queryClient.getQueryState(["app-metadata"])?.status).toBe("success"),
    );
    return rendered;
}

/** From Data Flow (TCP is the default transport) to the Connection step. */
async function chooseIntegration(user: User, name: string) {
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(await screen.findByRole("combobox", { name: "Integration" }));
    await user.click(await screen.findByRole("option", { name }));
    await screen.findByRole("radiogroup", { name: "Access Mode" });
}

/** Names the source on the Integration step, then moves on to Connection. */
async function toConnection(user: User, name = "TCP source") {
    await user.type(screen.getByPlaceholderText("Enter server name"), name);
    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() =>
        expect(screen.getByRole("tab", { name: "Connection" })).toHaveAttribute(
            "aria-selected",
            "true",
        ),
    );
}

async function fillNativePlain(user: User) {
    await user.click(screen.getByRole("radio", { name: "Plain TCP" }));
    await user.type(await screen.findByLabelText(/^Host/), "tcp.example.test");
    await user.type(screen.getByLabelText(/^Port/), "8089");
    await user.click(screen.getByRole("button", { name: "Next" }));
}

async function finishCustomFraming(user: User, mapperLabel?: string) {
    await user.type(await screen.findByLabelText(/^Delimiter/), "|");
    if (mapperLabel) {
        await user.click(screen.getByRole("combobox", { name: "Mapper" }));
        await user.click(await screen.findByRole("option", { name: mapperLabel }));
    }
}

async function toReview(user: User) {
    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() =>
        expect(screen.getByRole("tab", { name: "Review" })).toHaveAttribute(
            "aria-selected",
            "true",
        ),
    );
}

function captureJson() {
    const captured: { body?: unknown; contentType?: string | null } = {};
    server.use(
        http.post(V2_CONNECTIONS, async ({ request }) => {
            captured.contentType = request.headers.get("Content-Type");
            captured.body = await request.json();
            return HttpResponse.json(CREATED, { status: 201 });
        }),
    );
    return captured;
}

describe("New Data Source — TCP through integrations (flag on)", () => {
    it("asks for access after the integration instead of on Data Flow", async () => {
        await renderDialog();

        expect(screen.getByRole("radio", { name: "TCP" })).toBeChecked();
        expect(screen.queryByRole("radiogroup", { name: "Access Mode" })).toBeNull();
    });

    it("offers only integrations available in this runtime", async () => {
        const user = userEvent.setup();
        await renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));
        await user.click(await screen.findByRole("combobox", { name: "Integration" }));

        expect(await screen.findByRole("option", { name: "Native" })).toBeVisible();
        expect(screen.getByRole("option", { name: "TAK" })).toBeVisible();
        expect(screen.queryByRole("option", { name: "Retired plugin" })).toBeNull();
    });

    it("lists every access mode and mutes the ones the integration does not support", async () => {
        const user = userEvent.setup();
        await renderDialog();
        const accessRadios = () =>
            within(screen.getByRole("radiogroup", { name: "Access Mode" })).getAllByRole("radio");
        const enabledModes = () =>
            accessRadios()
                .filter((radio) => !(radio as HTMLInputElement).disabled)
                .map((radio) => radio.getAttribute("value"));

        await chooseIntegration(user, "Native");
        expect(accessRadios().map((radio) => radio.getAttribute("value"))).toEqual([
            "R",
            "W",
            "RW",
        ]);
        expect(enabledModes()).toEqual(["R"]);
        expect(screen.getByRole("radio", { name: "Read" })).toBeChecked();
        expect(screen.getByRole("radio", { name: "Write" })).toHaveAccessibleDescription(
            expect.stringContaining("Native is Read only"),
        );

        await user.click(screen.getByRole("combobox", { name: "Integration" }));
        await user.click(await screen.findByRole("option", { name: "TAK" }));
        await waitFor(() => expect(enabledModes()).toEqual(["R", "W", "RW"]));
        expect(screen.getByRole("radio", { name: "Read" })).toBeChecked();

        await user.click(screen.getByRole("combobox", { name: "Integration" }));
        await user.click(await screen.findByRole("option", { name: "Writer" }));
        await waitFor(() => expect(enabledModes()).toEqual(["W"]));
        expect(screen.getByRole("radio", { name: "Write" })).toBeChecked();
        expect(screen.getByRole("radio", { name: "Read" })).toHaveAccessibleDescription(
            expect.stringContaining("Writer is Write only"),
        );
    });

    it("submits the exact v2 JSON body for a Native plain TCP Read connection", async () => {
        const captured = captureJson();
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await fillNativePlain(user);
        await finishCustomFraming(user, "JSON mapper");
        await toReview(user);
        expect(screen.getByText("tcp.example.test")).toBeVisible();
        expect(screen.getByText("8089")).toBeVisible();
        expect(
            within(screen.getByRole("group", { name: "Mapping" })).getByText("Delimiter Split (|)"),
        ).toBeVisible();
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(captured.contentType).toBe("application/json");
        expect(captured.body).toEqual({
            kind: "TCP",
            name: "TCP source",
            access: "R",
            integration: { id: NATIVE_ID, schemaVersion: 1 },
            extractorId: "extractor-plain",
            values: { host: "tcp.example.test", port: "8089" },
            mapperId: JSON_MAPPER_ID,
            framing: { type: "CUSTOM", mode: "delimiterSplit", delimiter: "|" },
        });
    });

    it("sends the egress mapper and no ingress mapper for Write", async () => {
        let submitted: unknown;
        server.use(
            http.post(V2_CONNECTIONS, async ({ request }) => {
                const body = (await request.formData()).get("body");
                if (!(body instanceof File)) throw new Error("Expected a JSON body part");
                submitted = JSON.parse(await body.text());
                return HttpResponse.json(CREATED, { status: 201 });
            }),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await chooseIntegration(user, "TAK");
        await user.click(screen.getByText("Write"));
        await toConnection(user, "TAK writer");
        await user.upload(
            await screen.findByLabelText("Credentials package", { selector: "input" }),
            new File(["package"], "creds.zip", { type: "application/zip" }),
        );
        await user.click(screen.getByRole("button", { name: "Next" }));
        expect(await screen.findByRole("combobox", { name: "Egress Mapper" })).toHaveTextContent(
            "CoT egress",
        );
        expect(screen.queryByRole("combobox", { name: "Mapper" })).toBeNull();
        await toReview(user);
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(submitted).toMatchObject({
            access: "W",
            egressMapperId: COT_EGRESS_ID,
            framing: { type: "INTEGRATION", framingId: COT_FRAMING_ID },
        });
        expect(submitted).not.toHaveProperty("mapperId");
    });

    it("shows the setup error when connection setup fails to load", async () => {
        server.use(
            http.get(`${API_BASE}/integrations/${NATIVE_ID}/versions/1/connection/setup`, () =>
                HttpResponse.json({ code: "NOT_FOUND", message: "x" }, { status: 404 }),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));
        await user.click(await screen.findByRole("combobox", { name: "Integration" }));
        await user.click(await screen.findByRole("option", { name: "Native" }));

        expect(await screen.findByText(/Couldn't load this integration's setup/)).toBeVisible();
        expect(screen.queryByRole("radiogroup", { name: "Access Mode" })).toBeNull();
    });

    it("shows the setup error when the integration catalog fails to load", async () => {
        server.use(
            http.get(`${API_BASE}/integrations`, () =>
                HttpResponse.json({ code: "INTERNAL_ERROR", message: "x" }, { status: 500 }),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));

        expect(await screen.findByText(/Couldn't load this integration's setup/)).toBeVisible();
    });

    it("says so when the integration offers no TCP setup", async () => {
        server.use(
            http.get(`${API_BASE}/integrations/${NATIVE_ID}/versions/1/connection/setup`, () =>
                HttpResponse.json({ ...NATIVE_SETUP, protocols: [] }),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));
        await user.click(await screen.findByRole("combobox", { name: "Integration" }));
        await user.click(await screen.findByRole("option", { name: "Native" }));

        expect(
            await screen.findByText(
                "This integration cannot set up TCP connections. Choose another.",
            ),
        ).toBeVisible();
    });

    it("asks for the server name on the Integration step before moving on", async () => {
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await user.click(screen.getByRole("button", { name: "Next" }));

        const name = screen.getByPlaceholderText("Enter server name");
        expect(await screen.findByText("Server Name is required")).toBeVisible();
        expect(name).toHaveFocus();
        expect(screen.getByRole("tab", { name: "Integration" })).toHaveAttribute(
            "aria-selected",
            "true",
        );
        await user.type(name, "T");
        expect(screen.queryByText("Server Name is required")).toBeNull();
    });

    it("accepts a mapper as soon as it is picked", async () => {
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await fillNativePlain(user);
        await user.click(screen.getByRole("button", { name: "Next" }));
        expect(await screen.findAllByText("Choose an option to continue")).not.toHaveLength(0);

        await user.type(screen.getByLabelText(/^Delimiter/), "|");
        await user.click(screen.getByRole("combobox", { name: "Mapper" }));
        await user.click(await screen.findByRole("option", { name: "JSON mapper" }));

        expect(screen.queryByText("Choose an option to continue")).toBeNull();
        await toReview(user);
    });

    it("clears typed connection values when the integration changes", async () => {
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await user.click(screen.getByRole("radio", { name: "Plain TCP" }));
        await user.type(await screen.findByLabelText(/^Host/), "native-host.test");
        await user.click(screen.getByRole("tab", { name: "Integration" }));
        await user.click(screen.getByRole("combobox", { name: "Integration" }));
        await user.click(await screen.findByRole("option", { name: "Writer" }));
        await user.click(screen.getByRole("button", { name: "Next" }));

        expect(await screen.findByLabelText(/^Host/)).toHaveValue("");
    });

    it("drops a typed delimiter when the integration changes, keeping the name", async () => {
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user, "Kept name");
        await fillNativePlain(user);
        await user.type(await screen.findByLabelText(/^Delimiter/), "|");

        await user.click(screen.getByRole("tab", { name: "Integration" }));
        await user.click(screen.getByRole("combobox", { name: "Integration" }));
        await user.click(await screen.findByRole("option", { name: "TAK" }));
        await user.click(screen.getByRole("combobox", { name: "Integration" }));
        await user.click(await screen.findByRole("option", { name: "Native" }));

        expect(await screen.findByPlaceholderText("Enter server name")).toHaveValue("Kept name");
        await user.click(screen.getByRole("button", { name: "Next" }));
        await user.click(await screen.findByRole("radio", { name: "Plain TCP" }));
        await user.type(await screen.findByLabelText(/^Host/), "tcp.example.test");
        await user.type(screen.getByLabelText(/^Port/), "8089");
        await user.click(screen.getByRole("button", { name: "Next" }));
        expect(await screen.findByLabelText(/^Delimiter/)).toHaveValue("");
    });

    it("focuses the first invalid field on a blocked Next and clears its error on the next keystroke", async () => {
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await user.click(screen.getByRole("radio", { name: "Plain TCP" }));
        await user.type(await screen.findByLabelText(/^Host/), "tcp.example.test");
        await user.click(screen.getByRole("button", { name: "Next" }));

        expect(await screen.findByText("Port is required")).toBeVisible();
        expect(screen.getByLabelText(/^Port/)).toHaveFocus();
        await user.type(screen.getByLabelText(/^Port/), "8");
        expect(screen.queryByText("Port is required")).toBeNull();
    });

    it("stays on Connection and names the missing field when a required value is empty", async () => {
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await user.click(screen.getByRole("radio", { name: "Plain TCP" }));
        await user.type(await screen.findByLabelText(/^Host/), "tcp.example.test");
        await user.click(screen.getByRole("button", { name: "Next" }));

        expect(await screen.findByText("Port is required")).toBeVisible();
        expect(screen.getByRole("tab", { name: "Connection" })).toHaveAttribute(
            "aria-selected",
            "true",
        );
    });

    it("stays on Connection and sends nothing when the port is not a number", async () => {
        const captured = captureJson();
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await user.click(screen.getByRole("radio", { name: "Plain TCP" }));
        await user.type(await screen.findByLabelText(/^Host/), "tcp.example.test");
        await user.type(screen.getByLabelText(/^Port/), "80a");
        await user.click(screen.getByRole("button", { name: "Next" }));

        expect(
            await screen.findByText("Port must be a whole number from 1 to 65535"),
        ).toBeVisible();
        expect(screen.getByRole("tab", { name: "Connection" })).toHaveAttribute(
            "aria-selected",
            "true",
        );
        expect(captured.body).toBeUndefined();
    });

    it("puts a server rejection of an extractor field back on that field and focuses it", async () => {
        server.use(
            http.post(V2_CONNECTIONS, () =>
                HttpResponse.json(
                    {
                        code: "CONNECTION_INPUT_INVALID",
                        message: "Port must be a whole number between 1 and 65535",
                        detailCode: "INVALID_TCP_PORT",
                        field: "port",
                    },
                    { status: 400 },
                ),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user, "X");
        await fillNativePlain(user);
        await finishCustomFraming(user, "JSON mapper");
        await toReview(user);
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(
            await screen.findByText("Port must be a whole number between 1 and 65535"),
        ).toBeVisible();
        // The stepper marks the step Core rejected until its field is edited.
        const failedTab = screen.getByRole("tab", {
            name: "Connection (has a problem to fix)",
        });
        expect(failedTab).toHaveAttribute("aria-selected", "true");
        await waitFor(() => expect(screen.getByLabelText(/^Port/)).toHaveFocus());

        await user.type(screen.getByLabelText(/^Port/), "0");
        expect(screen.getByRole("tab", { name: "Connection" })).toBeInTheDocument();
    });

    it("focuses the drop zone, not the hidden input, when Core rejects a file", async () => {
        server.use(
            http.post(V2_CONNECTIONS, () =>
                HttpResponse.json(
                    {
                        code: "CONNECTION_INPUT_INVALID",
                        message: "The credentials package could not be read",
                        field: "credentialsZip",
                    },
                    { status: 400 },
                ),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "TAK");
        await toConnection(user, "TAK source");
        await user.upload(
            await screen.findByLabelText("Credentials package", { selector: "input" }),
            new File(["package"], "creds.zip", { type: "application/zip" }),
        );
        await user.click(screen.getByRole("button", { name: "Next" }));
        await toReview(user);
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByText("The credentials package could not be read")).toBeVisible();
        // The hidden file input is aria-hidden; focus must land where assistive tech can follow.
        await waitFor(() =>
            expect(screen.getByRole("button", { name: "Credentials package" })).toHaveFocus(),
        );
    });

    it("shows an access rejection on Access Mode and focuses a supported option", async () => {
        server.use(
            http.post(V2_CONNECTIONS, () =>
                HttpResponse.json(
                    {
                        code: "CONNECTION_INPUT_INVALID",
                        message: "This integration cannot write over TCP",
                        field: "access",
                    },
                    { status: 400 },
                ),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await fillNativePlain(user);
        await finishCustomFraming(user, "JSON mapper");
        await toReview(user);
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByText("This integration cannot write over TCP")).toBeVisible();
        expect(
            screen.getByRole("tab", { name: "Integration (has a problem to fix)" }),
        ).toHaveAttribute("aria-selected", "true");
        await waitFor(() => expect(screen.getByRole("radio", { name: "Read" })).toHaveFocus());
    });

    it("puts a custom framing rejection on the Delimiter field", async () => {
        server.use(
            http.post(V2_CONNECTIONS, () =>
                HttpResponse.json(
                    {
                        code: "INVALID_CUSTOM_FRAMING",
                        message: "A delimiter is at most 16 bytes",
                        field: "framing.delimiter",
                    },
                    { status: 400 },
                ),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await fillNativePlain(user);
        await finishCustomFraming(user, "JSON mapper");
        await toReview(user);
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByText("A delimiter is at most 16 bytes")).toBeVisible();
        expect(screen.getByRole("tab", { name: "Mapping (has a problem to fix)" })).toHaveAttribute(
            "aria-selected",
            "true",
        );
    });

    it("shows Core's message on Review for a rejection that names no field", async () => {
        server.use(
            http.post(V2_CONNECTIONS, () =>
                HttpResponse.json(
                    {
                        code: "CORE-CONNECTION-1003",
                        message: "A data source named 'TCP source' already exists",
                    },
                    { status: 409 },
                ),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await fillNativePlain(user);
        await finishCustomFraming(user, "JSON mapper");
        await toReview(user);
        // The live region is there before it has anything to say, so the error is announced.
        expect(screen.getByRole("alert")).toBeEmptyDOMElement();
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "A data source named 'TCP source' already exists",
        );
    });

    it("neither requires nor sends fields declared only by the previous connection method", async () => {
        const captured = captureJson();
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await chooseIntegration(user, "Native");
        await toConnection(user);
        await user.click(screen.getByRole("radio", { name: "Plain TCP" }));
        await user.type(await screen.findByLabelText(/^Port/), "8089");
        await user.click(screen.getByRole("radio", { name: "TCP with mutual TLS" }));
        await user.type(await screen.findByLabelText(/^Host/), "tcp.example.test");
        await user.click(screen.getByRole("button", { name: "Next" }));
        await finishCustomFraming(user, "JSON mapper");
        await toReview(user);
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(captured.body).toMatchObject({ extractorId: "extractor-mtls" });
        expect(captured.body).toHaveProperty("values", { host: "tcp.example.test" });
    });

    it("submits TAK multipart with the package under its declared field and the only allowed pair", async () => {
        let submitted: unknown;
        let packageNames: string[] = [];
        server.use(
            http.post(V2_CONNECTIONS, async ({ request }) => {
                const formData = await request.formData();
                const body = formData.get("body");
                if (!(body instanceof File)) throw new Error("Expected a JSON body part");
                submitted = JSON.parse(await body.text());
                packageNames = formData
                    .getAll("credentialsZip")
                    .map((entry) => (entry instanceof File ? entry.name : String(entry)));
                return HttpResponse.json(CREATED, { status: 201 });
            }),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await chooseIntegration(user, "TAK");
        await toConnection(user, "TAK source");
        await user.upload(
            await screen.findByLabelText("Credentials package", { selector: "input" }),
            new File(["package"], "creds.zip", { type: "application/zip" }),
        );
        await user.click(screen.getByRole("button", { name: "Next" }));
        expect(await screen.findByRole("combobox", { name: "Mapper" })).toHaveTextContent(
            "CoT mapper",
        );
        // The wizard words an integration framing as the data-source drawer does.
        expect(screen.getByRole("combobox", { name: "Framing" })).toHaveTextContent(
            "Provided by TAK (Delimiter Include)",
        );
        await toReview(user);
        expect(
            within(screen.getByRole("group", { name: "Connection" })).getByText("creds.zip"),
        ).toBeVisible();
        expect(
            within(screen.getByRole("group", { name: "Mapping" })).getByText(
                "Provided by TAK (Delimiter Include)",
            ),
        ).toBeVisible();
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(packageNames).toEqual(["creds.zip"]);
        expect(submitted).toEqual({
            kind: "TCP",
            name: "TAK source",
            access: "R",
            integration: { id: TAK_ID, schemaVersion: 1 },
            extractorId: "extractor-zip",
            values: {},
            mapperId: COT_MAPPER_ID,
            framing: { type: "INTEGRATION", framingId: COT_FRAMING_ID },
        });
    });
});

const KAFKA_CREDENTIALS = {
    topicName: "kafka-topic",
    username: "kafka-user",
    password: "kafka-password",
    bootstrapServer: "kafka.example:9092",
};

describe("New Data Source — integration-supplied text", () => {
    const NATIVE_KEY = `integrations.${NATIVE_ID}`;
    // The real Native ids, so the keys match the fr locale entries.
    const PLAIN_TCP_KEY = `${NATIVE_KEY}.extractors.5e8b2c47-1f3a-4d69-8b0e-6a4c9d2f7e15.label`;
    const UDP_LISTENER_KEY = `${NATIVE_KEY}.extractors.3f1d7a52-8c4e-4b0a-9e6f-2d5b7c8a1e93.label`;

    /** Serves Native with keys: Plain TCP and the UDP listener have translations, mutual TLS doesn't. */
    function serveKeyedNative() {
        const [tcp, udp, ...rest] = NATIVE_SETUP.protocols;
        const [plain, mtls] = tcp.extractors;
        server.use(
            http.get(`${API_BASE}/integrations`, () =>
                HttpResponse.json(
                    CATALOG.map((entry) =>
                        entry.integrationId === NATIVE_ID
                            ? { ...entry, nameKey: `${NATIVE_KEY}.name` }
                            : entry,
                    ),
                ),
            ),
            http.get(`${API_BASE}/integrations/${NATIVE_ID}/versions/1/connection/setup`, () =>
                HttpResponse.json({
                    ...NATIVE_SETUP,
                    protocols: [
                        {
                            ...tcp,
                            extractors: [
                                { ...plain, labelKey: PLAIN_TCP_KEY },
                                { ...mtls, labelKey: `${NATIVE_KEY}.extractors.unknown.label` },
                            ],
                        },
                        {
                            ...udp,
                            extractors: udp.extractors.map((extractor) => ({
                                ...extractor,
                                labelKey: UDP_LISTENER_KEY,
                            })),
                        },
                        ...rest,
                    ],
                }),
            ),
        );
    }

    afterEach(async () => {
        await act(() => i18n.changeLanguage("en"));
    });

    it("lists the integration in the user's language", async () => {
        serveKeyedNative();
        const user = userEvent.setup();
        await renderDialog();
        await user.click(screen.getByRole("button", { name: "Next" }));

        await act(() => i18n.changeLanguage("fr"));
        await user.click(await screen.findByRole("combobox", { name: "Intégration" }));

        expect(await screen.findByRole("option", { name: "Natif" })).toBeInTheDocument();
    });

    it("captions a single connection method in the user's language", async () => {
        serveKeyedNative();
        const user = userEvent.setup();
        await renderDialog();
        await user.click(screen.getByRole("radio", { name: "UDP" }));
        await chooseIntegration(user, "Native");
        await toConnection(user);

        await act(() => i18n.changeLanguage("fr"));

        // Review stays mounted and names the method too, so leave its row out.
        const reviewRow =
            screen.getByText("Méthode de connexion :").parentElement?.nextElementSibling;
        const caption = screen
            .getAllByText("Écouteur UDP")
            .filter((element) => !reviewRow?.contains(element));
        expect(caption).toHaveLength(1);
    });

    it("translates a connection method that has a translation", async () => {
        serveKeyedNative();
        const user = userEvent.setup();
        await renderDialog();
        await chooseIntegration(user, "Native");
        await toConnection(user);

        await act(() => i18n.changeLanguage("fr"));

        expect(screen.getByRole("radio", { name: "TCP simple" })).toBeInTheDocument();
    });

    it("keeps Core's label for a connection method with no translation", async () => {
        serveKeyedNative();
        const user = userEvent.setup();
        await renderDialog();
        await chooseIntegration(user, "Native");
        await toConnection(user);

        await act(() => i18n.changeLanguage("fr"));

        expect(screen.getByRole("radio", { name: "TCP with mutual TLS" })).toBeInTheDocument();
    });

    it("shows the translated integration and connection method on Review", async () => {
        serveKeyedNative();
        const user = userEvent.setup();
        await renderDialog();
        await chooseIntegration(user, "Native");
        await toConnection(user);
        await fillNativePlain(user);
        await finishCustomFraming(user, "JSON mapper");
        await toReview(user);

        await act(() => i18n.changeLanguage("fr"));

        const reviewed = (label: string) =>
            screen.getByText(label).parentElement?.nextElementSibling?.textContent;
        expect(reviewed("Intégration :")).toBe("Natif");
        expect(reviewed("Méthode de connexion :")).toBe("TCP simple");
    });
});

/** The transport Review names, read from the field beside its "Transport type:" label. */
function reviewedTransport(): string | null | undefined {
    return screen.getByText("Transport type:").parentElement?.nextElementSibling?.textContent;
}

/** From Data Flow to Review for a Native source of a mapper-only transport. */
async function toMapperOnlyReview(
    user: User,
    transport: "UDP" | "Kafka",
    name: string,
    fillConnection: () => Promise<void> = async () => {},
) {
    await user.click(screen.getByRole("radio", { name: transport }));
    await chooseIntegration(user, "Native");
    await toConnection(user, name);
    await fillConnection();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByRole("combobox", { name: "Mapper" })).toBeVisible();
    expect(screen.queryByRole("combobox", { name: "Framing" })).toBeNull();
    await user.click(screen.getByRole("combobox", { name: "Mapper" }));
    await user.click(await screen.findByRole("option", { name: "JSON mapper" }));
    await toReview(user);
}

describe("New Data Source — UDP and Kafka through integrations", () => {
    it("creates a UDP source from its listener fields and a mapper, with no profile", async () => {
        const captured = captureJson();
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await toMapperOnlyReview(user, "UDP", "UDP source", async () => {
            await user.type(await screen.findByLabelText(/^Host/), "udp.example.test");
            await user.type(screen.getByLabelText(/^Listen port/), "5000");
        });
        expect(screen.queryByText("Framing")).toBeNull();
        expect(reviewedTransport()).toBe("UDP");
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(captured.body).toEqual({
            kind: "UDP",
            name: "UDP source",
            access: "R",
            integration: { id: NATIVE_ID, schemaVersion: 1 },
            extractorId: "extractor-udp",
            values: { host: "udp.example.test", listenPort: "5000" },
            mapperId: JSON_MAPPER_ID,
        });
    });

    it("creates a Kafka source with a mapper and shows its one-time credentials", async () => {
        let body: unknown;
        server.use(
            http.post(V2_CONNECTIONS, async ({ request }) => {
                body = await request.json();
                return HttpResponse.json(
                    {
                        ...CREATED,
                        kind: "KAFKA",
                        transportType: "KAFKA",
                        kafkaCredentials: KAFKA_CREDENTIALS,
                    },
                    { status: 201 },
                );
            }),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await toMapperOnlyReview(user, "Kafka", "Kafka source", async () => {
            expect(screen.getByText(/Kafka sources are provisioned by OpenCOP/)).toBeVisible();
        });
        // Review names the transport as Data Flow does, and warns the credentials come once.
        expect(reviewedTransport()).toBe("Kafka");
        expect(
            screen.getByText("Kafka credentials are returned only once after creation."),
        ).toBeVisible();
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByText("Kafka Credentials")).toBeVisible();
        expect(screen.getByDisplayValue("kafka-topic")).toBeVisible();
        expect(onClose).not.toHaveBeenCalled();
        // The swap unmounted the focused Confirm button; focus moves to the credentials dialog,
        // and keyboard users stay inside it rather than falling to the page.
        const credentialsDialog = screen.getByRole("dialog", { name: /Kafka Credentials/ });
        await waitFor(() => expect(credentialsDialog).toHaveFocus());
        await user.tab();
        expect(credentialsDialog).toContainElement(document.activeElement as HTMLElement);
        // The optional byte rate left empty is omitted rather than sent blank.
        expect(body).toEqual({
            kind: "KAFKA",
            name: "Kafka source",
            access: "R",
            integration: { id: NATIVE_ID, schemaVersion: 1 },
            extractorId: "extractor-kafka",
            values: {},
            mapperId: JSON_MAPPER_ID,
        });
    });

    it("clears a failed credential copy's message when the next copy succeeds", async () => {
        server.use(
            http.post(V2_CONNECTIONS, () =>
                HttpResponse.json(
                    {
                        ...CREATED,
                        kind: "KAFKA",
                        transportType: "KAFKA",
                        kafkaCredentials: KAFKA_CREDENTIALS,
                    },
                    { status: 201 },
                ),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();
        await toMapperOnlyReview(user, "Kafka", "Kafka source");
        await user.click(screen.getByRole("button", { name: "Confirm" }));
        await screen.findByText("Kafka Credentials");
        const writeText = vi
            .spyOn(navigator.clipboard, "writeText")
            .mockRejectedValueOnce(new Error("denied"));

        const [copy] = screen.getAllByRole("button", { name: "Copy" });
        await user.click(copy);
        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Failed to copy value to clipboard",
        );

        await user.click(copy);
        await waitFor(() => expect(screen.getByRole("alert")).toBeEmptyDOMElement());
        expect(writeText).toHaveBeenCalledTimes(2);
    });

    it("says the source exists and blocks a resubmit when a Kafka creation returns no credentials", async () => {
        server.use(
            http.post(V2_CONNECTIONS, () =>
                HttpResponse.json(
                    { ...CREATED, kind: "KAFKA", transportType: "KAFKA" },
                    { status: 201 },
                ),
            ),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await toMapperOnlyReview(user, "Kafka", "Kafka source");
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(
            await screen.findByText(
                "The source was created, but no Kafka credentials came back. Delete it and create it again.",
            ),
        ).toBeVisible();
        expect(screen.queryByText("Kafka Credentials")).not.toBeInTheDocument();
        expect(onClose).not.toHaveBeenCalled();
        // The source exists, so Confirm must not create it a second time; Cancel still closes.
        expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Cancel" })).toBeEnabled();
    });

    describe("after a Kafka creation returns no credentials", () => {
        const MISSING =
            "The source was created, but no Kafka credentials came back. Delete it and create it again.";

        async function createKafkaWithoutCredentials(user: User) {
            server.use(
                http.post(V2_CONNECTIONS, () =>
                    HttpResponse.json(
                        { ...CREATED, kind: "KAFKA", transportType: "KAFKA" },
                        { status: 201 },
                    ),
                ),
            );
            await renderDialog();
            await toMapperOnlyReview(user, "Kafka", "Kafka source");
            await user.click(screen.getByRole("button", { name: "Confirm" }));
            expect(await screen.findByText(MISSING)).toBeVisible();
        }

        it("keeps the reason beside the held Confirm when the user steps back and returns", async () => {
            const user = userEvent.setup();
            await createKafkaWithoutCredentials(user);

            await user.click(screen.getByRole("tab", { name: "Mapping" }));
            await user.click(screen.getByRole("tab", { name: "Review" }));

            expect(await screen.findByText(MISSING)).toBeVisible();
            expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
        });

        it("lets a new source be created once the transport changes", async () => {
            const user = userEvent.setup();
            await createKafkaWithoutCredentials(user);
            const captured = captureJson();

            await user.click(screen.getByRole("tab", { name: "Data Flow" }));
            await toMapperOnlyReview(user, "UDP", "UDP source", async () => {
                await user.type(await screen.findByLabelText(/^Host/), "udp.example.test");
                await user.type(screen.getByLabelText(/^Listen port/), "5000");
            });

            expect(screen.queryByText(MISSING)).toBeNull();
            const confirm = screen.getByRole("button", { name: "Confirm" });
            expect(confirm).toBeEnabled();
            await user.click(confirm);
            await waitFor(() =>
                expect(captured.body).toMatchObject({ kind: "UDP", name: "UDP source" }),
            );
        });
    });

    it("says the mappers failed to load, rather than that none fit, and retries them", async () => {
        let mapperReads = 0;
        server.use(
            http.get(`${API_BASE}/profile/mapping`, () => {
                mapperReads += 1;
                return mapperReads === 1
                    ? HttpResponse.json({ message: "Temporary failure" }, { status: 503 })
                    : HttpResponse.json(INGRESS_MAPPERS);
            }),
        );
        const user = userEvent.setup();
        await renderDialog();

        await user.click(screen.getByRole("radio", { name: "UDP" }));
        await chooseIntegration(user, "Native");
        await toConnection(user, "UDP source");
        await user.type(await screen.findByLabelText(/^Host/), "udp.example.test");
        await user.type(screen.getByLabelText(/^Listen port/), "5000");
        await user.click(screen.getByRole("button", { name: "Next" }));

        // Shown beside the picker, and as the picker's own empty message.
        expect(await screen.findAllByText("Couldn't load the mappers.")).toHaveLength(2);
        await user.click(screen.getByRole("combobox", { name: "Mapper" }));
        expect(screen.queryByText("No mapper profiles fit this connection.")).toBeNull();

        await user.click(screen.getByRole("button", { name: "Retry" }));
        await user.click(screen.getByRole("combobox", { name: "Mapper" }));
        expect(await screen.findByRole("option", { name: "JSON mapper" })).toBeVisible();
        expect(screen.queryAllByText("Couldn't load the mappers.")).toHaveLength(0);
    });

    it("names the chosen transport when no integration is available for it", async () => {
        server.use(http.get(`${API_BASE}/integrations`, () => HttpResponse.json([])));
        const user = userEvent.setup();
        await renderDialog();

        await user.click(screen.getByRole("radio", { name: "Kafka" }));
        await user.click(screen.getByRole("button", { name: "Next" }));
        await user.click(await screen.findByRole("combobox", { name: "Integration" }));

        expect(await screen.findByText("No integrations are available for Kafka.")).toBeVisible();
    });

    it("offers UDP only Read, even when the integration also reports Write", async () => {
        server.use(
            http.get(`${API_BASE}/integrations/${NATIVE_ID}/versions/1/connection/setup`, () =>
                HttpResponse.json({
                    ...NATIVE_SETUP,
                    protocols: NATIVE_SETUP.protocols.map((protocol) =>
                        protocol.kind === "UDP"
                            ? { ...protocol, accessModes: ["R", "W", "RW"] }
                            : protocol,
                    ),
                }),
            ),
        );
        const user = userEvent.setup();
        await renderDialog();

        await user.click(screen.getByRole("radio", { name: "UDP" }));
        await chooseIntegration(user, "Native");

        // The v2 UDP request carries no egress mapper, so a Write choice could not be sent.
        const accessGroup = screen.getByRole("radiogroup", { name: "Access Mode" });
        await waitFor(() =>
            expect(
                within(accessGroup)
                    .getAllByRole("radio")
                    .filter((radio) => !(radio as HTMLInputElement).disabled)
                    .map((radio) => radio.getAttribute("value")),
            ).toEqual(["R"]),
        );
        expect(screen.getByRole("radio", { name: "Read" })).toBeChecked();
    });

    it("picks the one mapper a UDP integration permits without asking", async () => {
        server.use(
            http.get(`${API_BASE}/integrations/${NATIVE_ID}/versions/1/connection/setup`, () =>
                HttpResponse.json({
                    ...NATIVE_SETUP,
                    protocols: NATIVE_SETUP.protocols.map((protocol) =>
                        protocol.kind === "UDP"
                            ? {
                                  ...protocol,
                                  ingress: [{ type: "MAPPER", mapperId: COT_MAPPER_ID }],
                              }
                            : protocol,
                    ),
                }),
            ),
        );
        const captured = captureJson();
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await user.click(screen.getByRole("radio", { name: "UDP" }));
        await chooseIntegration(user, "Native");
        await toConnection(user, "UDP source");
        await user.type(await screen.findByLabelText(/^Host/), "udp.example.test");
        await user.type(screen.getByLabelText(/^Listen port/), "5000");
        await user.click(screen.getByRole("button", { name: "Next" }));
        expect(await screen.findByRole("combobox", { name: "Mapper" })).toBeVisible();
        await toReview(user);
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        await waitFor(() => expect(onClose).toHaveBeenCalled());
        expect(captured.body).toMatchObject({ kind: "UDP", mapperId: COT_MAPPER_ID });
    });

    it("keeps the result owner mounted while a Kafka creation is pending", async () => {
        let releaseResponse!: () => void;
        const responseGate = new Promise<void>((resolve) => {
            releaseResponse = resolve;
        });
        let createRequests = 0;
        server.use(
            http.post(V2_CONNECTIONS, async () => {
                createRequests += 1;
                await responseGate;
                return HttpResponse.json(
                    {
                        ...CREATED,
                        kind: "KAFKA",
                        transportType: "KAFKA",
                        kafkaCredentials: KAFKA_CREDENTIALS,
                    },
                    { status: 201 },
                );
            }),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await toMapperOnlyReview(user, "Kafka", "Kafka Pending");
        await user.click(screen.getByRole("button", { name: "Confirm" }));
        await waitFor(() => expect(createRequests).toBe(1));

        const cancel = screen.getByRole("button", { name: "Cancel" });
        const previous = screen.getByRole("button", { name: "Previous" });
        expect(cancel).toBeDisabled();
        expect(previous).toBeDisabled();
        await user.click(cancel);
        await user.click(previous);
        await user.keyboard("{Escape}");
        const backdrop = document.querySelector<HTMLElement>(
            '[data-scope="dialog"][data-part="backdrop"]',
        );
        if (!backdrop) throw new Error("Expected dialog backdrop");
        fireEvent.pointerDown(backdrop);
        fireEvent.click(backdrop);

        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByRole("tab", { name: "Review" })).toHaveAttribute(
            "aria-selected",
            "true",
        );

        releaseResponse();

        expect(await screen.findByText("Kafka Credentials")).toBeVisible();
        expect(screen.getByDisplayValue("kafka.example:9092")).toBeVisible();
        expect(createRequests).toBe(1);
        expect(onClose).not.toHaveBeenCalled();
    });

    it("holds a pending Kafka creation on Review so its failure stays visible", async () => {
        let releaseResponse!: () => void;
        const responseGate = new Promise<void>((resolve) => {
            releaseResponse = resolve;
        });
        let createRequests = 0;
        server.use(
            http.post(V2_CONNECTIONS, async () => {
                createRequests += 1;
                await responseGate;
                return HttpResponse.json({ message: "protected backend detail" }, { status: 500 });
            }),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();
        await renderDialog(onClose);

        await toMapperOnlyReview(user, "Kafka", "Kafka Failing");
        await user.click(screen.getByRole("button", { name: "Confirm" }));
        await waitFor(() => expect(createRequests).toBe(1));

        const dataFlowStep = screen.getByRole("tab", { name: "Data Flow" });
        expect(dataFlowStep).toBeDisabled();
        fireEvent.click(dataFlowStep);
        expect(screen.getByRole("tab", { name: "Review" })).toHaveAttribute(
            "aria-selected",
            "true",
        );

        releaseResponse();

        expect(await screen.findByText("Failed to create server")).toBeVisible();
        expect(screen.queryByText("protected backend detail")).not.toBeInTheDocument();
        expect(createRequests).toBe(1);
        expect(onClose).not.toHaveBeenCalled();
    });

    it("starts the integration steps over when the transport changes", async () => {
        const user = userEvent.setup();
        await renderDialog();

        await chooseIntegration(user, "Native");
        await user.type(screen.getByPlaceholderText("Enter server name"), "Was TCP");
        await user.click(screen.getByRole("tab", { name: "Data Flow" }));
        await user.click(screen.getByRole("radio", { name: "UDP" }));
        await user.click(screen.getByRole("button", { name: "Next" }));

        expect(await screen.findByRole("combobox", { name: "Integration" })).not.toHaveTextContent(
            "Native",
        );
        await user.click(screen.getByRole("combobox", { name: "Integration" }));
        await user.click(await screen.findByRole("option", { name: "Native" }));
        expect(await screen.findByPlaceholderText("Enter server name")).toHaveValue("");
    });
});

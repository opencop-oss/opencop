// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    ApiError,
    type AccessMode,
    type ConnectionCreatedV2Response,
    type TransportType,
} from "@opencop/api";
import { assertNever } from "assert-never";
import { pick } from "lodash";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useForm, useFormState, useWatch, type FieldErrors, type FieldPath } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { profileQueries } from "~/features/data-sources/data-source-profiles.queries";
import { dataSourceMutations } from "~/features/data-sources/data-sources.queries";
import {
    getConnectionInputErrorMessage,
    getCreateDataSourceErrorMessage,
} from "~/features/data-sources/data-source-errors";
import { integrationQueries } from "~/features/data-sources/integrations.queries";
import {
    buildTcpRequest,
    egressMapperIds,
    framingChoices,
    mapperChoice,
    supportedTcpAccessModes,
    type CustomFramingMode,
} from "./tcp-setup.model";
import {
    accessReads,
    accessWrites,
    buildMapperOnlyRequest,
    findProtocolSetup,
    ingressMapperChoice,
    isMapperOnly,
    type MapperChoice,
    supportedMapperOnlyAccessModes,
} from "./protocol-setup.model";
import { integrationDisplayName } from "~/features/data-sources/integration-text.util";

/** Steps of the integration-driven flow, after the shared Data Flow step. */
export type SetupStepId = "integration" | "connection" | "mapping" | "review";

/** The step that holds each top-level form field. */
const FIELD_STEPS: Record<keyof ConnectionSetupFormValues, SetupStepId> = {
    integrationKey: "integration",
    name: "integration",
    access: "integration",
    extractorId: "connection",
    values: "connection",
    files: "connection",
    framing: "mapping",
    customMode: "mapping",
    customDelimiter: "mapping",
    mapperId: "mapping",
    egressMapperId: "mapping",
};

/** Request fields Core names exactly as the form does. */
const SAME_NAMED_FIELDS = [
    "name",
    "extractorId",
    "access",
    "mapperId",
    "egressMapperId",
] as const satisfies readonly (keyof ConnectionSetupFormValues)[];

/** True when an error node, or any nested under it, came from a server rejection. */
function hasServerError(node: unknown): boolean {
    if (!node || typeof node !== "object") return false;
    if ((node as { type?: unknown }).type === "server") return true;
    // `ref` is the input element; never walk into the DOM.
    return Object.entries(node).some(([key, child]) => key !== "ref" && hasServerError(child));
}

/** Steps holding a field Core rejected, in step order, until the user edits that field. */
export function serverErrorSteps(errors: FieldErrors<ConnectionSetupFormValues>): SetupStepId[] {
    const failed = new Set(
        Object.entries(errors).flatMap(([field, error]) =>
            field in FIELD_STEPS && hasServerError(error)
                ? [FIELD_STEPS[field as keyof ConnectionSetupFormValues]]
                : [],
        ),
    );
    return (["integration", "connection", "mapping", "review"] as const).filter((step) =>
        failed.has(step),
    );
}

/**
 * Form state of the integration-driven flow of any protocol; `framing`, `customMode`,
 * `customDelimiter` and `egressMapperId` are used by TCP only. `values` and `files` are keyed by extractor field
 * names: they carry over when the user switches between one integration's extractors and are
 * cleared when the integration changes. The request is built from the selected extractor's
 * declared fields only.
 */
export interface ConnectionSetupFormValues {
    /** `<integrationId>:<schemaVersion>` of the chosen catalog entry, or empty before a choice. */
    integrationKey: string;
    /** Chosen access; the flow uses it only while the integration's setup still offers it. */
    access: AccessMode;
    name: string;
    extractorId: string;
    values: Record<string, string>;
    files: Record<string, File[]>;
    /** An integration framing id, or `CUSTOM_FRAMING`. */
    framing: string;
    /** Ingress mapper, used when the connection reads. */
    mapperId: string;
    /** Egress mapper, used when the connection writes. */
    egressMapperId: string;
    customMode: CustomFramingMode;
    customDelimiter: string;
}

const DEFAULT_VALUES: ConnectionSetupFormValues = {
    integrationKey: "",
    access: "R",
    name: "",
    extractorId: "",
    values: {},
    files: {},
    framing: "",
    mapperId: "",
    egressMapperId: "",
    customMode: "delimiterSplit",
    customDelimiter: "",
};

/** Form values that belong to no integration and so survive switching integrations. */
const PRESERVED_ON_INTEGRATION_CHANGE = [
    "name",
] as const satisfies readonly (keyof ConnectionSetupFormValues)[];

function parseIntegrationKey(key: string): { id: string; schemaVersion: number } | undefined {
    const [id, version] = key.split(":");
    return id && version ? { id, schemaVersion: Number(version) } : undefined;
}

interface UseConnectionSetupOptions {
    protocol: TransportType;
    goToStep: (step: SetupStepId) => void;
    onCreated: (created: ConnectionCreatedV2Response) => void;
}

const NO_MAPPERS: MapperChoice = { kind: "ONLY", mapperIds: [] };

/**
 * Form state, backend lookups and submission for the integration-driven flow of one protocol.
 * Framing and the egress mapper exist only for TCP; a mapper-only protocol chooses just an ingress
 * mapper, so the returned `framings`, `framing`, `egressIds`, `egressMapperId` and
 * `egressProfiles` are empty for it.
 * Every choice the backend constrains is derived rather than synchronised: the value in effect is
 * the user's choice when the setup still permits it, else the only permitted option, else none.
 */
export function useConnectionSetup({ protocol, goToStep, onCreated }: UseConnectionSetupOptions) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [errorMessage, setErrorMessage] = useState("");
    // The source exists once Core answers, so a Kafka create without credentials must not be
    // submitted again: a retry would create a second source.
    const [createdWithoutCredentials, setCreatedWithoutCredentials] = useState(false);
    const [focusRequest, setFocusRequest] = useState<{
        path: FieldPath<ConnectionSetupFormValues>;
        seq: number;
    }>();
    const form = useForm<ConnectionSetupFormValues>({
        defaultValues: DEFAULT_VALUES,
        mode: "onBlur",
        reValidateMode: "onBlur",
    });
    const { control, handleSubmit, setError, setFocus, trigger } = form;
    const { errors } = useFormState({ control });
    const [
        integrationKey,
        chosenAccess,
        chosenExtractorId,
        chosenFraming,
        chosenMapperId,
        chosenEgressMapperId,
    ] = useWatch({
        control,
        name: ["integrationKey", "access", "extractorId", "framing", "mapperId", "egressMapperId"],
    });

    // A routed server error focuses its input once the step holding it is shown, so the message
    // under the input scrolls into view.
    useEffect(() => {
        if (focusRequest) setFocus(focusRequest.path);
    }, [focusRequest, setFocus]);

    const catalog = useQuery(integrationQueries.catalog(protocol));
    const integration = parseIntegrationKey(integrationKey);
    const setupQuery = useQuery({
        ...integrationQueries.connectionSetup(
            integration?.id ?? "",
            integration?.schemaVersion ?? 0,
        ),
        enabled: !!integration,
    });
    const protocolSetup = setupQuery.data
        ? findProtocolSetup(setupQuery.data.protocols, protocol)
        : undefined;
    const tcp = protocolSetup?.kind === "TCP" ? protocolSetup : undefined;

    const accessModes =
        protocolSetup && integration
            ? tcp
                ? supportedTcpAccessModes(tcp, integration.id)
                : supportedMapperOnlyAccessModes(protocolSetup.accessModes)
            : [];
    const access: AccessMode = accessModes.includes(chosenAccess)
        ? chosenAccess
        : (accessModes[0] ?? "R");
    const reads = accessReads(access);
    const writes = accessWrites(access);

    const {
        data: mapperProfiles = [],
        isLoadingError: mappersFailed,
        refetch: retryMappers,
    } = useQuery({
        ...profileQueries.mapperProfileList(),
        enabled: reads,
    });
    const {
        data: egressProfiles = [],
        isLoadingError: egressMappersFailed,
        refetch: retryEgressMappers,
    } = useQuery({
        ...profileQueries.egressMapperProfileList(),
        enabled: writes,
    });
    const createMutation = useMutation(dataSourceMutations.createV2(queryClient));

    const extractors = protocolSetup?.extractors ?? [];
    const extractor =
        extractors.find((candidate) => candidate.id === chosenExtractorId) ??
        (extractors.length === 1 ? extractors[0] : undefined);
    const framings = tcp ? framingChoices(tcp) : [];
    const framing =
        framings.find((choice) => choice.value === chosenFraming)?.value ??
        (framings.length === 1 ? framings[0].value : "");
    const mappers: MapperChoice = tcp
        ? framing
            ? mapperChoice(tcp, framing)
            : NO_MAPPERS
        : protocolSetup && protocolSetup.kind !== "TCP"
          ? ingressMapperChoice(protocolSetup.ingress)
          : NO_MAPPERS;
    const mapperAllowed = (id: string) =>
        mappers.kind === "ANY"
            ? mapperProfiles.some((mapper) => mapper.id === id)
            : mappers.mapperIds.includes(id);
    const mapperId =
        chosenMapperId && mapperAllowed(chosenMapperId)
            ? chosenMapperId
            : mappers.kind === "ONLY" && mappers.mapperIds.length === 1
              ? mappers.mapperIds[0]
              : "";
    const egressIds = egressMapperIds(tcp?.egress ?? null, egressProfiles);
    const egressMapperId = egressIds.includes(chosenEgressMapperId)
        ? chosenEgressMapperId
        : egressIds.length === 1
          ? egressIds[0]
          : "";
    const integrations = (catalog.data ?? []).filter((entry) => entry.available);
    const chosenIntegration = integrations.find(
        (entry) => `${entry.integrationId}:${entry.schemaVersion}` === integrationKey,
    );
    const integrationName = chosenIntegration && integrationDisplayName(chosenIntegration, t);

    /**
     * Starts the form over for a newly chosen integration, keeping only what no integration owns,
     * so nothing typed for the previous one (values, files, framing, delimiter) is sent with this one.
     */
    const chooseIntegration = (key: string) => {
        const preserved = pick(form.getValues(), PRESERVED_ON_INTEGRATION_CHANGE);
        form.reset({ ...DEFAULT_VALUES, ...preserved, integrationKey: key });
    };

    const fieldPath = (name: string) => {
        const declared = extractor?.fields.find((field) => field.name === name);
        if (!declared) return undefined;
        return declared.kind === "FILE" || declared.kind === "FILE_LIST"
            ? (`files.${name}` as const)
            : (`values.${name}` as const);
    };

    const validateStep = async (step: SetupStepId): Promise<boolean> => {
        switch (step) {
            case "integration":
                if (!(await trigger("integrationKey", { shouldFocus: true }))) return false;
                if (!protocolSetup || accessModes.length === 0) return false;
                return trigger("name", { shouldFocus: true });
            case "connection":
                return trigger(
                    [
                        "extractorId",
                        ...(extractor?.fields.flatMap((field) => fieldPath(field.name) ?? []) ??
                            []),
                    ],
                    { shouldFocus: true },
                );
            case "mapping":
                return trigger(
                    [
                        ...(tcp ? (["framing", "customDelimiter"] as const) : []),
                        ...(reads ? (["mapperId"] as const) : []),
                        ...(writes ? (["egressMapperId"] as const) : []),
                    ],
                    { shouldFocus: true },
                );
            case "review":
                return true;
            default:
                return assertNever(step);
        }
    };

    /** The form path a request field Core names lands on, or `undefined` for one the form lacks. */
    const serverFieldPath = (field: string): FieldPath<ConnectionSetupFormValues> | undefined => {
        const declared = fieldPath(field);
        if (declared) return declared;
        if (field === "integration" || field.startsWith("integration.")) return "integrationKey";
        if (field === "framing.delimiter") return "customDelimiter";
        if (field === "framing" || field.startsWith("framing.")) return "framing";
        return SAME_NAMED_FIELDS.find((name) => name === field);
    };

    /**
     * Where a rejected request field is shown: the form path, and the step that holds it, read from
     * {@link FIELD_STEPS} so routing and the failed-step marker can never name different steps.
     */
    const serverFieldTarget = (
        field: string,
    ): { path: FieldPath<ConnectionSetupFormValues>; step: SetupStepId } | undefined => {
        const path = serverFieldPath(field);
        if (!path) return undefined;
        const root = path.split(".")[0] as keyof ConnectionSetupFormValues;
        return { path, step: FIELD_STEPS[root] };
    };

    /**
     * Any v2 rejection naming a `field` lands on that input, whatever its code: Core reports
     * extractor input, custom framing, mapper and name problems under different codes. Focus
     * follows only for inputs that register a DOM ref (text and secret fields, name, delimiter,
     * the connection-method radios, file inputs); OcSelect-backed choices (integration, framing,
     * mappers) show the message without moving focus until OcSelect forwards a ref. Known input
     * rejections use localized messages; others keep Core's message, which its contract keeps safe
     * to display.
     */
    const showServerError = (error: unknown) => {
        if (!(error instanceof ApiError)) {
            setErrorMessage(getCreateDataSourceErrorMessage(error, t));
            return;
        }
        const message = getConnectionInputErrorMessage(error, t);
        const target = error.body.field ? serverFieldTarget(error.body.field) : undefined;
        if (target && message) {
            setError(target.path, { type: "server", message });
            goToStep(target.step);
            setFocusRequest((previous) => ({ path: target.path, seq: (previous?.seq ?? 0) + 1 }));
            return;
        }
        if (message && (error.status === 400 || error.status === 409)) {
            setErrorMessage(message);
            return;
        }
        setErrorMessage(getCreateDataSourceErrorMessage(error, t));
    };

    const submit = handleSubmit((values) => {
        setErrorMessage("");
        if (!integration || !extractor) return;
        const selection = {
            name: values.name,
            access,
            integration,
            extractor,
            values: values.values,
            files: values.files,
            mapperId,
        };
        const { request, files } = isMapperOnly(protocol)
            ? buildMapperOnlyRequest({ ...selection, kind: protocol })
            : buildTcpRequest({
                  ...selection,
                  egressMapperId,
                  framing,
                  customMode: values.customMode,
                  customDelimiter: values.customDelimiter,
              });
        // These are mutate-level callbacks, which TanStack skips if the dialog unmounts before the
        // request settles; the source is still created, but its one-time Kafka credentials are
        // never shown. Accepted: the dialog blocks its own close while creating.
        createMutation.mutate(
            { request, files },
            {
                onSuccess: (created) => {
                    // Kafka credentials come only in this response, so a Kafka create without
                    // them can't be used and must not close the flow as a success.
                    if (protocol === "KAFKA" && !created.kafkaCredentials) {
                        setCreatedWithoutCredentials(true);
                        setErrorMessage(t("admin.kafkaCredentialsMissing"));
                        return;
                    }
                    onCreated(created);
                },
                onError: showServerError,
            },
        );
    });

    return {
        protocol,
        form,
        integrations,
        integrationKey,
        integrationName,
        catalogFailed: catalog.isError,
        setupFailed: setupQuery.isError,
        noProtocolSetup: setupQuery.isSuccess && !protocolSetup,
        chooseIntegration,
        /**
         * Starts a new source: clears every choice, back to the initial form (`form.reset()` alone
         * would restore the last integration's), and lifts the hold a credential-less Kafka
         * creation put on Confirm, which covers only the source already created.
         */
        startOver: () => {
            setCreatedWithoutCredentials(false);
            setErrorMessage("");
            form.reset(DEFAULT_VALUES);
        },
        accessModes,
        access,
        reads,
        writes,
        extractors,
        extractor,
        framings,
        framing,
        mappers,
        mapperId,
        mapperProfiles,
        egressIds,
        egressMapperId,
        egressProfiles,
        /** The ingress mapper list never loaded, so an empty picker means nothing. */
        mappersFailed: reads && mappersFailed,
        /** The egress mapper list never loaded, so an empty picker means nothing. */
        egressMappersFailed: writes && egressMappersFailed,
        /** Reloads whichever mapper list never loaded. */
        retryMappers: () => {
            if (mappersFailed) void retryMappers();
            if (egressMappersFailed) void retryEgressMappers();
        },
        errorMessage,
        clearErrorMessage: () => setErrorMessage(""),
        createdWithoutCredentials,
        validateStep,
        failedSteps: serverErrorSteps(errors),
        submit,
        isPending: createMutation.isPending,
    };
}

export type ConnectionSetupFlow = ReturnType<typeof useConnectionSetup>;

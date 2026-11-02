// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Dialog, Flex, Input, Spinner, VStack, useDisclosure } from "@chakra-ui/react";
import { ErrorMessage, Form, Formik } from "formik";
import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { useTranslation } from "react-i18next";
import { IoClose } from "react-icons/io5";
import { FaCamera, FaTrashCan } from "react-icons/fa6";
import * as Yup from "yup";
import {
    TacticalText,
    AccentHeading,
    DefaultCheckbox,
    ListPanelItem,
    scrollbarStyles,
    IconButton,
    ActionButton,
    BaseDialog,
    SortableLayerList,
} from "@opencop/ui";
import { arrayMove } from "@dnd-kit/sortable";
import { compact, differenceBy, keyBy } from "lodash";
import { useMapCaptureOrderStore, useMapCaptureOrderActions } from "./mapcapture-order.store";
import { SortableCaptureEntry } from "./SortableCaptureEntry";
import { useCoordFormat } from "~/features/coordinates/CoordFormatContext";
import { formatCoord } from "@opencop/utils";
import type { MapCaptureResponse } from "@opencop/api";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { mapCaptureMutations, mapCaptureQueries } from "./mapcapture.queries";
import { DateTime } from "luxon";
import { useToast } from "~/features/notifications/use-toast.hook";
import { ConfirmDeleteDialog } from "~/features/membership-management/ConfirmDeleteDialog";
import {
    preferencesQueries,
    preferencesMutations,
} from "~/features/preferences/preferences.queries";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { useMapStore } from "../map/core/map.store";
import { useViewActions } from "../map/core/slices/view/view.hook";
import { useMapManager } from "../map/MapManagerContext";

const COORD_TOLERANCE = 0.001; // ~111m at equator
const ZOOM_TOLERANCE = 0.1;
const ANGLE_TOLERANCE = 1; // degrees (bearing/pitch)

export function MapCapture() {
    const { t } = useTranslation();
    const { open: modalIsOpen, onOpen: modalOnOpen, onClose: modalOnClose } = useDisclosure();
    const mapManager = useMapManager();
    const { flyTo } = useViewActions();
    const timezone = useUserTimezone();
    const queryClient = useQueryClient();
    const { showToast } = useToast();
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);

    const captureOrder = useMapCaptureOrderStore((s) => s.order);
    const { reorderCaptures, reconcile } = useMapCaptureOrderActions();
    const { format: coordFormat } = useCoordFormat();

    const { data: preferences } = useQuery(preferencesQueries.current());
    const { data: captures = [], isLoading, isError } = useQuery(mapCaptureQueries.list());

    const serverCaptureIds = useMemo(() => captures.map((c) => c.id), [captures]);
    useEffect(() => {
        if (isLoading || isError) return;
        reconcile(serverCaptureIds);
    }, [serverCaptureIds, isLoading, isError, reconcile]);

    const createMutation = useMutation(mapCaptureMutations.create(queryClient));
    const deleteMutation = useMutation(mapCaptureMutations.delete(queryClient));
    const updatePreferences = useMutation(preferencesMutations.update(queryClient));

    // Backend returns initialView as full MapCaptureResponse object, extract the ID
    const defaultCaptureId = preferences?.initialView?.id ?? null;

    // Track which capture matches the current map camera via external store
    const [optimisticCaptureId, setOptimisticCaptureId] = useState<string | null>(null);

    const subscribeToMapMove = useCallback(
        (onStoreChange: () => void) => {
            const sub = mapManager.onMap("moveend", () => {
                setOptimisticCaptureId(null);
                onStoreChange();
            });
            return () => sub.unsub();
        },
        [mapManager],
    );

    const getMatchingCaptureId = useCallback((): string | null => {
        const { latitude, longitude, zoom, bearing, pitch } = useMapStore.getState();
        if (captures.length === 0) return null;
        return (
            captures.find(
                (c) =>
                    Math.abs(c.mapView.latitude - latitude) < COORD_TOLERANCE &&
                    Math.abs(c.mapView.longitude - longitude) < COORD_TOLERANCE &&
                    Math.abs(c.mapView.zoom - zoom) < ZOOM_TOLERANCE &&
                    Math.abs(c.mapView.bearing - (bearing ?? 0)) < ANGLE_TOLERANCE &&
                    Math.abs(c.mapView.pitch - (pitch ?? 0)) < ANGLE_TOLERANCE,
            )?.id ?? null
        );
    }, [captures]);

    const mapDerivedCaptureId = useSyncExternalStore(subscribeToMapMove, getMatchingCaptureId);
    const activeCaptureId = optimisticCaptureId ?? mapDerivedCaptureId;

    const pendingDeleteCapture = captures.find((c) => c.id === pendingDeleteId) ?? null;

    const orderedCaptures = useMemo(() => {
        if (captureOrder.length === 0) return captures;
        const byId = keyBy(captures, "id");
        const ordered = compact(captureOrder.map((id) => byId[id]));
        return [...ordered, ...differenceBy(captures, ordered, "id")];
    }, [captures, captureOrder]);

    const captureIds = useMemo(() => orderedCaptures.map((c) => c.id), [orderedCaptures]);

    const CreateCaptureSchema = Yup.object().shape({
        name: Yup.string()
            .min(3, t("mapCapture.nameMinLength"))
            .required(t("mapCapture.nameRequired"))
            .test("unique", t("mapCapture.nameExists"), (value) => {
                if (!value) return true;
                return !captures.some((c) => c.name === value);
            }),
    });

    function handleModalClose(): void {
        setSubmitError(null);
        modalOnClose();
    }

    function handleCaptureClick(capture: MapCaptureResponse): void {
        setOptimisticCaptureId(capture.id);
        flyTo({
            pitch: capture.mapView.pitch,
            bearing: capture.mapView.bearing,
            zoom: capture.mapView.zoom,
            center: [capture.mapView.longitude, capture.mapView.latitude],
            speed: 2.5,
        });
    }

    function handleConfirmDelete(): void {
        if (!pendingDeleteId) return;
        deleteMutation.mutate(pendingDeleteId, {
            onSuccess: (_, deletedId) => {
                if (defaultCaptureId === deletedId) {
                    updatePreferences.mutate({ initialView: null });
                }
                setPendingDeleteId(null);
            },
            onError: () => {
                showToast({
                    type: "error",
                    title: t("mapCapture.deleteError"),
                });
            },
        });
    }

    function handleDefaultViewChange(capture: MapCaptureResponse, checked: boolean): void {
        updatePreferences.mutate(
            {
                initialView: checked ? capture.id : null,
            },
            {
                onError: (error) => {
                    showToast({
                        type: "error",
                        title: t("mapCapture.updateError", { defaultValue: "Update failed" }),
                        description: error instanceof Error ? error.message : undefined,
                    });
                },
            },
        );
    }

    return (
        <Flex direction="column" height="100%" width="100%">
            <Formik
                key={
                    // modal never unmounts, just gets hidden and shown again -
                    // use a new component on open so stale form state is never shown
                    modalIsOpen ? "open" : "closed"
                }
                initialValues={{ name: "" }}
                validationSchema={CreateCaptureSchema}
                onSubmit={(values) => {
                    const { longitude, latitude, zoom, pitch, bearing } = useMapStore.getState();
                    setSubmitError(null);
                    createMutation.mutate(
                        {
                            name: values.name,
                            mapView: {
                                longitude,
                                latitude,
                                zoom: zoom ?? 1,
                                pitch: pitch ?? 0,
                                bearing: bearing ?? 0,
                            },
                        },
                        {
                            onSuccess: handleModalClose,
                            onError: (err) => {
                                setSubmitError(
                                    err instanceof Error
                                        ? err.message
                                        : t("mapCapture.createError"),
                                );
                            },
                        },
                    );
                }}
            >
                {({ values, handleChange }) => (
                    <BaseDialog open={modalIsOpen} onClose={handleModalClose} size="sm">
                        <Dialog.Header>
                            <Flex
                                direction="row"
                                justifyContent="space-between"
                                alignItems="center"
                                w="full"
                                flexWrap="nowrap"
                            >
                                <AccentHeading>{t("mapCapture.createTitle")}</AccentHeading>
                                <IconButton
                                    bg="type.error"
                                    color="bg.contrast"
                                    _hover={{
                                        opacity: 0.8,
                                    }}
                                    onClick={handleModalClose}
                                    aria-label={t("mapCapture.closeDialog")}
                                >
                                    <IoClose />
                                </IconButton>
                            </Flex>
                        </Dialog.Header>

                        <Dialog.Body>
                            <Form>
                                <Flex
                                    borderRadius="md"
                                    w="full"
                                    h="10"
                                    borderWidth="1px"
                                    borderColor="border"
                                    alignItems="center"
                                    position="relative"
                                    bg="transparent"
                                >
                                    <Input
                                        name="name"
                                        value={values.name}
                                        onChange={handleChange}
                                        border="none"
                                        fontFamily="mono"
                                        fontWeight="500"
                                        color="fg"
                                        _focus={{
                                            outline: "none",
                                            borderColor: "active",
                                        }}
                                        placeholder={t("mapCapture.namePlaceholder")}
                                        aria-label={t("mapCapture.captureName")}
                                        aria-describedby="capture-name-error"
                                        autoFocus
                                    />
                                    <Button
                                        type="submit"
                                        position="absolute"
                                        right="1"
                                        bg="type.success"
                                        color="bg.contrast"
                                        size="sm"
                                        borderRadius="md"
                                        fontFamily="mono"
                                        fontWeight="600"
                                        _hover={{
                                            opacity: 0.8,
                                        }}
                                        loading={createMutation.isPending}
                                        disabled={createMutation.isPending}
                                    >
                                        {t("common.create")}
                                    </Button>
                                </Flex>
                                <ErrorMessage name="name">
                                    {(msg) => (
                                        <TacticalText
                                            id="capture-name-error"
                                            color="type.error"
                                            mt="2"
                                            role="alert"
                                            aria-live="assertive"
                                        >
                                            {msg}
                                        </TacticalText>
                                    )}
                                </ErrorMessage>
                                {submitError && (
                                    <TacticalText
                                        color="type.error"
                                        mt="2"
                                        role="alert"
                                        aria-live="assertive"
                                    >
                                        {submitError}
                                    </TacticalText>
                                )}
                            </Form>
                            <Box mt="4">
                                <TacticalText fontSize="xs" color="fg.muted">
                                    {t("mapCapture.helpText")}
                                </TacticalText>
                            </Box>
                        </Dialog.Body>
                    </BaseDialog>
                )}
            </Formik>

            {pendingDeleteCapture && (
                <ConfirmDeleteDialog
                    isOpen
                    onClose={() => setPendingDeleteId(null)}
                    onDelete={handleConfirmDelete}
                    userName={pendingDeleteCapture.name}
                />
            )}

            <Flex
                direction="row"
                justifyContent="space-between"
                alignItems="center"
                mb="4"
                flexWrap="nowrap"
            >
                <AccentHeading>{t("mapCapture.listTitle")}</AccentHeading>
                <ActionButton
                    onClick={modalOnOpen}
                    bg="transparent"
                    color="active"
                    borderColor="active"
                    size="sm"
                    gap="2"
                    _hover={{
                        bg: "glass.blue",
                    }}
                >
                    <FaCamera size={14} />
                </ActionButton>
            </Flex>
            <VStack
                w="full"
                flex="1"
                overflowY="auto"
                borderRadius="sm"
                p="1"
                userSelect="none"
                css={scrollbarStyles}
            >
                {isLoading && (
                    <Flex align="center" justify="center" w="full" pt="8">
                        <Spinner size="lg" color="brand.500" />
                    </Flex>
                )}
                {isError && (
                    <TacticalText color="type.error" role="alert">
                        {t("mapCapture.loadError")}
                    </TacticalText>
                )}
                {!isLoading && !isError && (
                    <SortableLayerList ids={captureIds} onReorder={reorderCaptures}>
                        <VStack w="full" align="stretch" gap="2">
                            {orderedCaptures.map((capture, i) => {
                                const isDefaultCapture = defaultCaptureId === capture.id;
                                const isActiveCapture = activeCaptureId === capture.id;
                                const coords = formatCoord(
                                    capture.mapView.latitude,
                                    capture.mapView.longitude,
                                    coordFormat,
                                );
                                return (
                                    <SortableCaptureEntry
                                        key={capture.id}
                                        id={capture.id}
                                        captureName={capture.name}
                                        canMoveUp={i > 0}
                                        canMoveDown={i < orderedCaptures.length - 1}
                                        onMoveUp={() =>
                                            reorderCaptures(arrayMove(captureIds, i, i - 1))
                                        }
                                        onMoveDown={() =>
                                            reorderCaptures(arrayMove(captureIds, i, i + 1))
                                        }
                                    >
                                        <ListPanelItem
                                            compact
                                            index={i}
                                            isSelected={isActiveCapture}
                                            onClick={() => handleCaptureClick(capture)}
                                            ariaLabel={capture.name}
                                            title={capture.name}
                                            bottomSlot={
                                                <VStack
                                                    align="start"
                                                    gap="0"
                                                    w="full"
                                                    px="1"
                                                    pb="1"
                                                >
                                                    <TacticalText fontSize="xs" color="fg.muted">
                                                        {capture.createdAt &&
                                                        !isNaN(
                                                            new Date(capture.createdAt).getTime(),
                                                        )
                                                            ? DateTime.fromISO(capture.createdAt)
                                                                  .toUTC()
                                                                  .setZone(timezone)
                                                                  .toLocaleString(
                                                                      DateTime.DATETIME_SHORT,
                                                                  )
                                                            : "—"}
                                                    </TacticalText>
                                                    <TacticalText fontSize="xs" color="fg.subtle">
                                                        {coords}
                                                    </TacticalText>
                                                </VStack>
                                            }
                                            actions={
                                                <>
                                                    <DefaultCheckbox
                                                        checked={isDefaultCapture}
                                                        label={t("mapCapture.defaultView")}
                                                        onCheckedChange={(checked) =>
                                                            handleDefaultViewChange(
                                                                capture,
                                                                checked,
                                                            )
                                                        }
                                                    />
                                                    <IconButton
                                                        size="xs"
                                                        bg="transparent"
                                                        color="fg.muted"
                                                        _hover={{
                                                            color: "type.error",
                                                            bg: "transparent",
                                                        }}
                                                        disabled={deleteMutation.isPending}
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            setPendingDeleteId(capture.id);
                                                        }}
                                                        aria-label={t("mapCapture.deleteCapture")}
                                                    >
                                                        <FaTrashCan size={12} />
                                                    </IconButton>
                                                </>
                                            }
                                            actionsVisible={isDefaultCapture}
                                        />
                                    </SortableCaptureEntry>
                                );
                            })}
                        </VStack>
                    </SortableLayerList>
                )}
            </VStack>
        </Flex>
    );
}

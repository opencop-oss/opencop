// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Fragment, useMemo, type ReactNode } from "react";
import { Accordion, Flex, Text, VStack } from "@chakra-ui/react";
import { keyframes } from "@emotion/react";
import { startCase } from "lodash";
import { useTranslation } from "react-i18next";
import type { EventDetails, UrlAttachment } from "@opencop/api";
import type { Geometry } from "geojson";
import type { Element } from "@opencop/elements";
import {
    useDetailsInfoSections,
    useDetailsPosition,
} from "~/features/contributions/details-registry";
import { AccentHeading, DetailTable, type DetailTableRow, Section } from "@opencop/ui";
import { GroupDisplay } from "../detail/GroupDisplay";
import { Timestamps } from "../detail/Timestamps";
import { Identity } from "../detail/Identity";
import { CoordinateDisplay } from "../detail/CoordinateDisplay";
import { Motion } from "./Motion";
import { StreamViewer } from "../detail/StreamViewer";

const fadeInUp = keyframes`
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
`;

interface InformationSectionProps {
    details: EventDetails;
    geometry?: Geometry;
    element?: Element;
    urlAttachments?: UrlAttachment[];
}

export function InformationSection({
    details,
    geometry,
    element,
    urlAttachments,
}: InformationSectionProps) {
    const { t } = useTranslation();

    const attributeRows = useMemo<DetailTableRow[]>(() => {
        const attrs = details.attributes ?? {};
        return Object.entries(attrs).map(([key, value]) => ({
            key,
            label: startCase(key).toUpperCase(),
            value: (
                <Text color="bg.contrast" textStyle="xs">
                    {String(value ?? "")}
                </Text>
            ),
        }));
    }, [details.attributes]);

    // The subject that owns the element's kind may say what position to show
    // (an arrow's middle, a circle's centre); otherwise a point geometry shows its
    // position and anything else shows no row.
    const contributed = useDetailsPosition(element ?? undefined);
    const infoSections = useDetailsInfoSections(element ?? undefined);
    const coordinateDisplay =
        contributed !== undefined
            ? contributed && {
                  lng: contributed.position[0],
                  lat: contributed.position[1],
                  label: t(contributed.labelKey as never),
              }
            : geometry?.type === "Point"
              ? {
                    lat: geometry.coordinates[1],
                    lng: geometry.coordinates[0],
                    label: t("events.fieldPosition"),
                }
              : null;

    // The tab's own sections and the contributed ones, listed by order (the
    // orders are part of the information-section contract).
    const sections: Array<{ key: string; order: number; node: ReactNode }> = [
        {
            key: "identity",
            order: 10,
            node: (
                <Section heading={t("events.identity")}>
                    <Identity
                        identity={{
                            displayName: details.displayName,
                            id: details.id,
                            objectId: details.objectId,
                            source: details.source,
                            kind: details.kind ?? t("events.notApplicable"),
                            version: details.version ?? t("events.notApplicable"),
                        }}
                    />
                </Section>
            ),
        },
        {
            key: "coordinates",
            order: 20,
            node: coordinateDisplay && (
                <Section heading={t("events.coordinates")}>
                    <CoordinateDisplay
                        lat={coordinateDisplay.lat}
                        lng={coordinateDisplay.lng}
                        label={coordinateDisplay.label}
                    />
                </Section>
            ),
        },
        ...(element
            ? infoSections.map(({ id, order, Component }) => ({
                  key: `contributed:${id}`,
                  order,
                  node: <Component element={element} />,
              }))
            : []),
        {
            key: "timestamps",
            order: 40,
            node: (
                <Section heading={t("events.timestamps")}>
                    <Timestamps timestampz={details.timestampz} />
                </Section>
            ),
        },
        {
            key: "motion",
            order: 50,
            node: (
                <Section heading={t("events.motion")}>
                    <Motion
                        speed={details.element.speed}
                        course={details.element.course}
                        heading={details.element.heading}
                    />
                </Section>
            ),
        },
        {
            key: "contacts",
            order: 60,
            node:
                details.contacts && details.contacts.length > 0 ? (
                    <GroupDisplay
                        mt="2"
                        label={t("events.contacts")}
                        path={["contacts"]}
                        object={details.contacts ?? []}
                    />
                ) : null,
        },
        {
            key: "attributes",
            order: 70,
            node: attributeRows.length > 0 && (
                <Section heading={t("events.attributes")}>
                    <DetailTable rows={attributeRows} />
                </Section>
            ),
        },
    ];

    return (
        <Flex
            direction="column"
            w="full"
            h="full"
            animation={`${fadeInUp} 300ms ease-out forwards`}
        >
            {urlAttachments && urlAttachments.length > 0 && (
                <Accordion.Root
                    collapsible
                    unmountOnExit
                    defaultValue={["streams"]}
                    w="full"
                    mb={2}
                >
                    <Accordion.Item value="streams" border="none">
                        <Accordion.ItemTrigger px={0} py={2}>
                            <AccentHeading rootProps={{ flex: "1" }}>
                                {t("events.liveStream")}
                            </AccentHeading>
                            <Accordion.ItemIndicator />
                        </Accordion.ItemTrigger>
                        <Accordion.ItemContent px={0}>
                            <Accordion.ItemBody px={0} pb={2}>
                                <StreamViewer
                                    attachments={urlAttachments}
                                    sourceId={details.source}
                                    objectId={details.objectId}
                                />
                            </Accordion.ItemBody>
                        </Accordion.ItemContent>
                    </Accordion.Item>
                </Accordion.Root>
            )}
            <VStack gap="6" w="full" flex="1" overflowY="auto" px="1">
                {sections
                    .sort((x, y) => x.order - y.order)
                    .map(({ key, node }) => (
                        <Fragment key={key}>{node}</Fragment>
                    ))}
            </VStack>
        </Flex>
    );
}

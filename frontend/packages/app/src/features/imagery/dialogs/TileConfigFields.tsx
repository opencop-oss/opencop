// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Collapsible, Field, Flex, Input, SimpleGrid, Slider, Text } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { TbChevronDown, TbChevronRight } from "react-icons/tb";
import { OcSelect, OcSlider, OcTabSelect } from "@opencop/ui";
import { ZOOM_MAX, ZOOM_MIN } from "~/features/map/core/slices/view/view-url.util";
import type {
    RasterDemTileConfig,
    RasterTileConfig,
    TerrainMeshTileConfig,
    TileConfig,
    VectorTileConfig,
    WmsTileConfig,
    WmtsTileConfig,
} from "@opencop/api";

const TILE_SIZE_OPTIONS = [
    { label: "256", value: "256" },
    { label: "512", value: "512" },
];

interface TileConfigFieldsProps {
    config: TileConfig;
    onConfigChange: (config: TileConfig) => void;
}

function parseCsvList(value: string): string[] {
    return value
        .split(",")
        .map((entry) => entry.trim())
        .filter(Boolean);
}

export function TileConfigFields({ config, onConfigChange }: TileConfigFieldsProps) {
    switch (config.kind) {
        case "VECTOR":
        case "RASTER":
            return <BaseZoomFields config={config} onConfigChange={onConfigChange} />;
        case "RASTER_DEM":
            return <RasterDemFields config={config} onConfigChange={onConfigChange} />;
        case "TERRAIN_MESH":
            return <TerrainMeshFields config={config} onConfigChange={onConfigChange} />;
        case "WMS":
            return <WmsFields config={config} onConfigChange={onConfigChange} />;
        case "WMTS":
            return <WmtsFields config={config} onConfigChange={onConfigChange} />;
        default:
            return null;
    }
}

// ---------------------------------------------------------------------------
// WMS / WMTS fields
// ---------------------------------------------------------------------------

interface WmsFieldsProps {
    config: WmsTileConfig;
    onConfigChange: (config: TileConfig) => void;
}

function WmsFields({ config, onConfigChange }: WmsFieldsProps) {
    const { t } = useTranslation();

    return (
        <>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.version")}</Field.Label>
                <Input
                    value={config.version}
                    onChange={(e) => onConfigChange({ ...config, version: e.target.value })}
                    placeholder={t("adminImagery.config.wmsVersionPlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.layers")}</Field.Label>
                <Input
                    value={config.layers.join(", ")}
                    onChange={(e) =>
                        onConfigChange({ ...config, layers: parseCsvList(e.target.value) })
                    }
                    placeholder={t("adminImagery.config.wmsLayersPlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.styles")}</Field.Label>
                <Input
                    value={config.styles?.join(", ") ?? ""}
                    onChange={(e) =>
                        onConfigChange({ ...config, styles: parseCsvList(e.target.value) })
                    }
                    placeholder={t("adminImagery.config.wmsStylesPlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.format")}</Field.Label>
                <Input
                    value={config.format}
                    onChange={(e) => onConfigChange({ ...config, format: e.target.value })}
                    placeholder={t("adminImagery.config.wmsFormatPlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.crs")}</Field.Label>
                <Input
                    value={config.crs}
                    onChange={(e) => onConfigChange({ ...config, crs: e.target.value })}
                    placeholder={t("adminImagery.config.wmsCrsPlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.transparent")}</Field.Label>
                <OcTabSelect
                    defaultValue={config.transparent ? "true" : "false"}
                    value={config.transparent ? "true" : "false"}
                    options={[
                        { label: t("common.yes"), value: "true" },
                        { label: t("common.no"), value: "false" },
                    ]}
                    onChange={(value) =>
                        onConfigChange({ ...config, transparent: value === "true" })
                    }
                />
            </Field.Root>
        </>
    );
}

interface WmtsFieldsProps {
    config: WmtsTileConfig;
    onConfigChange: (config: TileConfig) => void;
}

function WmtsFields({ config, onConfigChange }: WmtsFieldsProps) {
    const { t } = useTranslation();

    return (
        <>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.version")}</Field.Label>
                <Input
                    value={config.version}
                    onChange={(e) => onConfigChange({ ...config, version: e.target.value })}
                    placeholder={t("adminImagery.config.wmtsVersionPlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.layer")}</Field.Label>
                <Input
                    value={config.layer}
                    onChange={(e) => onConfigChange({ ...config, layer: e.target.value })}
                    placeholder={t("adminImagery.config.wmtsLayerPlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.style")}</Field.Label>
                <Input
                    value={config.style}
                    onChange={(e) => onConfigChange({ ...config, style: e.target.value })}
                    placeholder={t("adminImagery.config.wmtsStylePlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.format")}</Field.Label>
                <Input
                    value={config.format}
                    onChange={(e) => onConfigChange({ ...config, format: e.target.value })}
                    placeholder={t("adminImagery.config.wmtsFormatPlaceholder")}
                />
            </Field.Root>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.tilematrixset")}</Field.Label>
                <Input
                    value={config.tileMatrixSet}
                    onChange={(e) => onConfigChange({ ...config, tileMatrixSet: e.target.value })}
                    placeholder={t("adminImagery.config.wmtsTilematrixsetPlaceholder")}
                />
            </Field.Root>
        </>
    );
}

// ---------------------------------------------------------------------------
// Zoom range slider — shared by VECTOR, RASTER, RASTER_DEM
// ---------------------------------------------------------------------------

interface ZoomRangeFieldProps {
    minZoom: number | null;
    maxZoom: number | null;
    onChange: (minZoom: number, maxZoom: number) => void;
}

function ZoomRangeField({ minZoom, maxZoom, onChange }: ZoomRangeFieldProps) {
    const { t } = useTranslation();
    const min = minZoom ?? ZOOM_MIN;
    const max = maxZoom ?? ZOOM_MAX;

    return (
        <Field.Root>
            <Flex justify="space-between" align="center" w="full">
                <Field.Label mb={0}>{t("adminImagery.config.zoomRange")}</Field.Label>
                <Text fontFamily="mono" textStyle="xs" color="fg.muted">
                    {min} — {max}
                </Text>
            </Flex>
            <Slider.Root
                w="full"
                min={ZOOM_MIN}
                max={ZOOM_MAX}
                step={1}
                value={[min, max]}
                onValueChange={(e) => onChange(e.value[0], e.value[1])}
            >
                <Slider.Control>
                    <Slider.Track bg="border.muted" borderRadius="0">
                        <Slider.Range />
                    </Slider.Track>
                    <Slider.Thumb
                        index={0}
                        borderRadius="0"
                        w="2"
                        borderWidth="1px"
                        borderColor="border"
                        bg="active"
                        aria-label={t("adminImagery.config.minZoom")}
                    />
                    <Slider.Thumb
                        index={1}
                        borderRadius="0"
                        w="2"
                        borderWidth="1px"
                        borderColor="border"
                        bg="active"
                        aria-label={t("adminImagery.config.maxZoom")}
                    />
                </Slider.Control>
            </Slider.Root>
        </Field.Root>
    );
}

// ---------------------------------------------------------------------------
// Shared base fields: tileSize, zoom range
// ---------------------------------------------------------------------------

interface BaseZoomFieldsProps {
    config: VectorTileConfig | RasterTileConfig;
    onConfigChange: (config: TileConfig) => void;
}

function BaseZoomFields({ config, onConfigChange }: BaseZoomFieldsProps) {
    const { t } = useTranslation();

    return (
        <>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.tileSize")}</Field.Label>
                <OcTabSelect
                    defaultValue={String(config.tileSize)}
                    value={String(config.tileSize)}
                    options={TILE_SIZE_OPTIONS}
                    onChange={(v) => onConfigChange({ ...config, tileSize: parseInt(v, 10) })}
                />
            </Field.Root>
            <ZoomRangeField
                minZoom={config.minZoom}
                maxZoom={config.maxZoom}
                onChange={(minZoom, maxZoom) => onConfigChange({ ...config, minZoom, maxZoom })}
            />
        </>
    );
}

// ---------------------------------------------------------------------------
// RASTER_DEM fields
// ---------------------------------------------------------------------------

const ENCODING_ITEMS = [
    { label: "Mapbox", value: "mapbox" },
    { label: "Terrarium", value: "terrarium" },
    { label: "Custom", value: "custom" },
];

interface RasterDemFieldsProps {
    config: RasterDemTileConfig;
    onConfigChange: (config: TileConfig) => void;
}

function RasterDemFields({ config, onConfigChange }: RasterDemFieldsProps) {
    const { t } = useTranslation();

    return (
        <>
            <Field.Root>
                <Field.Label>{t("adminImagery.config.tileSize")}</Field.Label>
                <OcTabSelect
                    defaultValue={String(config.tileSize)}
                    value={String(config.tileSize)}
                    options={TILE_SIZE_OPTIONS}
                    onChange={(v) => onConfigChange({ ...config, tileSize: parseInt(v, 10) })}
                />
            </Field.Root>
            <ZoomRangeField
                minZoom={config.minZoom}
                maxZoom={config.maxZoom}
                onChange={(minZoom, maxZoom) => onConfigChange({ ...config, minZoom, maxZoom })}
            />
            <Field.Root>
                <Field.Label>{t("adminImagery.config.encoding")}</Field.Label>
                <OcSelect
                    items={ENCODING_ITEMS}
                    value={config.encoding}
                    onValueChange={(v) =>
                        onConfigChange({
                            ...config,
                            encoding: v as RasterDemTileConfig["encoding"],
                        })
                    }
                />
            </Field.Root>
            {config.encoding === "custom" && (
                <>
                    <Field.Root>
                        <Field.Label>{t("adminImagery.config.redFactor")}</Field.Label>
                        <Input
                            type="number"
                            step="any"
                            value={config.redFactor ?? ""}
                            onChange={(e) =>
                                onConfigChange({
                                    ...config,
                                    redFactor: e.target.value ? Number(e.target.value) : undefined,
                                })
                            }
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>{t("adminImagery.config.greenFactor")}</Field.Label>
                        <Input
                            type="number"
                            step="any"
                            value={config.greenFactor ?? ""}
                            onChange={(e) =>
                                onConfigChange({
                                    ...config,
                                    greenFactor: e.target.value
                                        ? Number(e.target.value)
                                        : undefined,
                                })
                            }
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>{t("adminImagery.config.blueFactor")}</Field.Label>
                        <Input
                            type="number"
                            step="any"
                            value={config.blueFactor ?? ""}
                            onChange={(e) =>
                                onConfigChange({
                                    ...config,
                                    blueFactor: e.target.value ? Number(e.target.value) : undefined,
                                })
                            }
                        />
                    </Field.Root>
                    <Field.Root>
                        <Field.Label>{t("adminImagery.config.baseShift")}</Field.Label>
                        <Input
                            type="number"
                            step="any"
                            value={config.baseShift ?? ""}
                            onChange={(e) =>
                                onConfigChange({
                                    ...config,
                                    baseShift: e.target.value ? Number(e.target.value) : undefined,
                                })
                            }
                        />
                    </Field.Root>
                </>
            )}
        </>
    );
}

// ---------------------------------------------------------------------------
// TERRAIN_MESH fields
// ---------------------------------------------------------------------------

const TERRARIUM_DECODER_DEFAULTS = {
    rScaler: 256,
    gScaler: 1,
    bScaler: 1 / 256,
    offset: -32768,
};

interface TerrainMeshFieldsProps {
    config: TerrainMeshTileConfig;
    onConfigChange: (config: TileConfig) => void;
}

function TerrainMeshFields({ config, onConfigChange }: TerrainMeshFieldsProps) {
    const { t } = useTranslation();
    const [advancedOpen, setAdvancedOpen] = useState(false);

    return (
        <Collapsible.Root open={advancedOpen} onOpenChange={(e) => setAdvancedOpen(e.open)}>
            <Collapsible.Trigger cursor="pointer" userSelect="none" py={1}>
                <Flex align="center" gap={1}>
                    <Text fontSize="xs" fontWeight="600" color="fg.muted" textTransform="uppercase">
                        {t("adminImagery.config.advanced")}
                    </Text>
                    <Box color="fg.muted" fontSize="sm">
                        {advancedOpen ? <TbChevronDown /> : <TbChevronRight />}
                    </Box>
                </Flex>
            </Collapsible.Trigger>
            <Collapsible.Content>
                <Flex direction="column" gap={4} pt={2}>
                    {config.encoding === "TERRARIUM" && (
                        <SimpleGrid columns={2} gap={4}>
                            <Field.Root required>
                                <Field.Label>
                                    {t("adminImagery.config.rScaler")}
                                    <Text as="span" color="red.500" ml={1}>
                                        *
                                    </Text>
                                </Field.Label>
                                <Input
                                    type="number"
                                    step="any"
                                    value={
                                        config.decoderConfig?.rScaler ??
                                        TERRARIUM_DECODER_DEFAULTS.rScaler
                                    }
                                    onChange={(e) =>
                                        onConfigChange({
                                            ...config,
                                            decoderConfig: {
                                                ...TERRARIUM_DECODER_DEFAULTS,
                                                ...config.decoderConfig,
                                                rScaler: e.target.value
                                                    ? Number(e.target.value)
                                                    : TERRARIUM_DECODER_DEFAULTS.rScaler,
                                            },
                                        })
                                    }
                                />
                            </Field.Root>
                            <Field.Root required>
                                <Field.Label>
                                    {t("adminImagery.config.gScaler")}
                                    <Text as="span" color="red.500" ml={1}>
                                        *
                                    </Text>
                                </Field.Label>
                                <Input
                                    type="number"
                                    step="any"
                                    value={
                                        config.decoderConfig?.gScaler ??
                                        TERRARIUM_DECODER_DEFAULTS.gScaler
                                    }
                                    onChange={(e) =>
                                        onConfigChange({
                                            ...config,
                                            decoderConfig: {
                                                ...TERRARIUM_DECODER_DEFAULTS,
                                                ...config.decoderConfig,
                                                gScaler: e.target.value
                                                    ? Number(e.target.value)
                                                    : TERRARIUM_DECODER_DEFAULTS.gScaler,
                                            },
                                        })
                                    }
                                />
                            </Field.Root>
                            <Field.Root required>
                                <Field.Label>
                                    {t("adminImagery.config.bScaler")}
                                    <Text as="span" color="red.500" ml={1}>
                                        *
                                    </Text>
                                </Field.Label>
                                <Input
                                    type="number"
                                    step="any"
                                    value={
                                        config.decoderConfig?.bScaler ??
                                        TERRARIUM_DECODER_DEFAULTS.bScaler
                                    }
                                    onChange={(e) =>
                                        onConfigChange({
                                            ...config,
                                            decoderConfig: {
                                                ...TERRARIUM_DECODER_DEFAULTS,
                                                ...config.decoderConfig,
                                                bScaler: e.target.value
                                                    ? Number(e.target.value)
                                                    : TERRARIUM_DECODER_DEFAULTS.bScaler,
                                            },
                                        })
                                    }
                                />
                            </Field.Root>
                            <Field.Root required>
                                <Field.Label>
                                    {t("adminImagery.config.offset")}
                                    <Text as="span" color="red.500" ml={1}>
                                        *
                                    </Text>
                                </Field.Label>
                                <Input
                                    type="number"
                                    step="any"
                                    value={
                                        config.decoderConfig?.offset ??
                                        TERRARIUM_DECODER_DEFAULTS.offset
                                    }
                                    onChange={(e) =>
                                        onConfigChange({
                                            ...config,
                                            decoderConfig: {
                                                ...TERRARIUM_DECODER_DEFAULTS,
                                                ...config.decoderConfig,
                                                offset: e.target.value
                                                    ? Number(e.target.value)
                                                    : TERRARIUM_DECODER_DEFAULTS.offset,
                                            },
                                        })
                                    }
                                />
                            </Field.Root>
                        </SimpleGrid>
                    )}
                    <SimpleGrid columns={2} gap={4}>
                        <OcSlider
                            label={t("adminImagery.config.meshMaxError")}
                            min={0}
                            max={20}
                            step={0.5}
                            value={config.meshMaxError ?? 4}
                            onChange={(v) => onConfigChange({ ...config, meshMaxError: v })}
                            editable
                        />
                        <OcSlider
                            label={t("adminImagery.config.exaggeration")}
                            min={0}
                            max={5}
                            step={0.1}
                            value={config.exaggeration ?? 1}
                            onChange={(v) => onConfigChange({ ...config, exaggeration: v })}
                            editable
                        />
                    </SimpleGrid>
                    <OcSlider
                        label={t("adminImagery.config.boundsPadding")}
                        min={0}
                        max={50}
                        step={1}
                        value={config.boundsPadding ?? 0}
                        onChange={(v) => onConfigChange({ ...config, boundsPadding: v })}
                        editable
                    />
                </Flex>
            </Collapsible.Content>
        </Collapsible.Root>
    );
}

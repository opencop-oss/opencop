// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Grid, HStack, Text, VStack } from "@chakra-ui/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { TbChevronDown, TbChevronRight } from "react-icons/tb";
import { ThemesSection } from "../ThemesSection";
import { ocThemeConfig } from "@opencop/ui";

// ── Types ───────────────────────────────────────────────────────────

interface TokenEntry {
    token: string;
}

interface TokenGroup {
    name: string;
    tokens: TokenEntry[];
    children: TokenGroup[];
}

// ── Build groups dynamically from semantic tokens ───────────────────

function buildSemanticGroups(obj: Record<string, unknown>, prefix: string): TokenGroup {
    const tokens: TokenEntry[] = [];
    const children: TokenGroup[] = [];

    for (const [key, val] of Object.entries(obj)) {
        if (!val || typeof val !== "object") continue;
        const record = val as Record<string, unknown>;
        const path = prefix ? `${prefix}.${key}` : key;

        if (record.value !== undefined) {
            const tokenPath = key === "DEFAULT" ? prefix : path;
            tokens.push({ token: tokenPath });
        } else {
            const child = buildSemanticGroups(record, path);
            if (child.tokens.length > 0 || child.children.length > 0) {
                children.push(child);
            }
        }
    }

    return { name: prefix || "root", tokens, children };
}

function useSemanticGroups(): TokenGroup[] {
    return useMemo(() => {
        const cfg = ocThemeConfig as unknown as Record<string, unknown>;

        // ocThemeConfig from defineConfig stores theme at .theme
        const theme = (cfg.theme ?? {}) as Record<string, unknown>;
        const semanticTokens = (theme.semanticTokens ?? {}) as Record<string, unknown>;
        const semanticColors = (semanticTokens.colors ?? {}) as Record<string, unknown>;
        const groups: TokenGroup[] = [];

        for (const [key, val] of Object.entries(semanticColors)) {
            if (!val || typeof val !== "object") continue;
            const record = val as Record<string, unknown>;

            if (record.value !== undefined) {
                groups.push({ name: key, tokens: [{ token: key }], children: [] });
            } else {
                const group = buildSemanticGroups(record, key);
                if (group.tokens.length > 0 || group.children.length > 0) {
                    groups.push(group);
                }
            }
        }

        return groups;
    }, []);
}

// ── Build palette groups from tokens.colors ─────────────────────────

function usePaletteGroups(): TokenGroup[] {
    return useMemo(() => {
        const cfg = ocThemeConfig as unknown as Record<string, unknown>;
        const theme = (cfg.theme ?? {}) as Record<string, unknown>;
        const tokens = (theme.tokens ?? {}) as Record<string, unknown>;
        const tokenColors = (tokens.colors ?? {}) as Record<string, unknown>;
        const groups: TokenGroup[] = [];

        for (const [key, val] of Object.entries(tokenColors)) {
            if (!val || typeof val !== "object") continue;
            const record = val as Record<string, unknown>;
            const entries: TokenEntry[] = [];

            for (const [shade, shadeVal] of Object.entries(record)) {
                if (!shadeVal || typeof shadeVal !== "object") continue;
                const shadeRecord = shadeVal as Record<string, unknown>;

                if (shadeRecord.value !== undefined) {
                    entries.push({ token: `${key}.${shade}` });
                } else {
                    // nested (e.g. slate.surface.primary)
                    for (const [sub, subVal] of Object.entries(shadeRecord)) {
                        if (!subVal || typeof subVal !== "object") continue;
                        const subRecord = subVal as Record<string, unknown>;
                        if (subRecord.value !== undefined) {
                            entries.push({ token: `${key}.${shade}.${sub}` });
                        }
                    }
                }
            }

            if (entries.length > 0) {
                groups.push({ name: key, tokens: entries, children: [] });
            }
        }

        return groups;
    }, []);
}

// ── Live swatch that reads its computed color ───────────────────────

function LiveSwatch({ token }: { token: string }) {
    const ref = useRef<HTMLDivElement>(null);
    const [hex, setHex] = useState("");

    // TODO: refactor to derive state instead of syncing with useEffect
    /* eslint-disable @eslint-react/hooks-extra/no-direct-set-state-in-use-effect */
    useEffect(() => {
        if (!ref.current) return;
        const computed = getComputedStyle(ref.current).backgroundColor;
        setHex(rgbToHex(computed));
    }, []);
    /* eslint-enable @eslint-react/hooks-extra/no-direct-set-state-in-use-effect */

    return (
        <HStack gap="3" py="1.5" pl="2">
            <Box
                ref={ref}
                w="20px"
                h="20px"
                flexShrink={0}
                bg={token}
                borderWidth="1px"
                borderColor="border.muted"
            />
            <Text fontSize="xs" fontWeight="bold" minW="140px">
                {token}
            </Text>
            <Text fontSize="2xs" color="fg.muted" fontFamily="mono">
                {hex}
            </Text>
        </HStack>
    );
}

/** Swatch for raw palette tokens — uses colorPalette syntax {colors.X.Y} */
function PaletteSwatch({ token, rawValue }: { token: string; rawValue?: string }) {
    const ref = useRef<HTMLDivElement>(null);
    const [hex, setHex] = useState(rawValue ?? "");

    // TODO: refactor to derive state instead of syncing with useEffect
    /* eslint-disable react-hooks/set-state-in-effect, @eslint-react/hooks-extra/no-direct-set-state-in-use-effect */
    useEffect(() => {
        if (rawValue) {
            setHex(rawValue);
            return;
        }
        if (!ref.current) return;
        const computed = getComputedStyle(ref.current).backgroundColor;
        setHex(rgbToHex(computed));
    }, [rawValue]);
    /* eslint-enable react-hooks/set-state-in-effect, @eslint-react/hooks-extra/no-direct-set-state-in-use-effect */

    return (
        <HStack gap="3" py="1.5" pl="2">
            <Box
                ref={ref}
                w="20px"
                h="20px"
                flexShrink={0}
                style={rawValue ? { backgroundColor: rawValue } : undefined}
                bg={rawValue ? undefined : token}
                borderWidth="1px"
                borderColor="border.muted"
            />
            <Text fontSize="xs" fontWeight="bold" minW="140px">
                {token}
            </Text>
            <Text fontSize="2xs" color="fg.muted" fontFamily="mono">
                {hex}
            </Text>
        </HStack>
    );
}

function rgbToHex(rgb: string): string {
    const match = rgb.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
    if (!match) return rgb;
    const [, r, g, b] = match;
    return "#" + [r, g, b].map((v) => parseInt(v).toString(16).padStart(2, "0")).join("");
}

// ── Collapsible group (recursive) ───────────────────────────────────

function TokenGroupSection({ group, depth }: { group: TokenGroup; depth: number }) {
    const [open, setOpen] = useState(true);

    const allTokens = collectAllTokens(group);

    return (
        <VStack align="start" gap="0" w="full">
            <HStack
                gap="2"
                py="1.5"
                px="2"
                cursor="pointer"
                _hover={{ bg: "bg.elevatedLight" }}
                onClick={() => setOpen((v) => !v)}
                w="full"
                userSelect="none"
            >
                <Box color="fg.muted" fontSize="sm">
                    {open ? <TbChevronDown /> : <TbChevronRight />}
                </Box>
                <Text fontSize="sm" fontWeight="bold">
                    {group.name}
                </Text>
                {!open && (
                    <HStack gap="1" ml="2">
                        {allTokens.slice(0, 8).map((t) => (
                            <Box
                                key={t.token}
                                w="12px"
                                h="12px"
                                bg={t.token}
                                borderWidth="1px"
                                borderColor="border.muted"
                            />
                        ))}
                        {allTokens.length > 8 && (
                            <Text fontSize="2xs" color="fg.muted">
                                +{allTokens.length - 8}
                            </Text>
                        )}
                    </HStack>
                )}
            </HStack>

            {open && (
                <Box pl="4" ml="2" borderLeftWidth="2px" borderColor="border.muted" w="full">
                    {group.tokens.map((t) => (
                        <LiveSwatch key={t.token} token={t.token} />
                    ))}
                    {group.children.map((child) => (
                        <TokenGroupSection key={child.name} group={child} depth={depth + 1} />
                    ))}
                </Box>
            )}
        </VStack>
    );
}

/** Palette group — resolves raw hex values from the config */
function PaletteGroupSection({ group }: { group: TokenGroup }) {
    const [open, setOpen] = useState(true);

    const rawValues = useMemo(() => {
        const cfg = ocThemeConfig as unknown as Record<string, unknown>;
        const theme = (cfg.theme ?? {}) as Record<string, unknown>;
        const tokens = (theme.tokens ?? {}) as Record<string, unknown>;
        const tokenColors = (tokens.colors ?? {}) as Record<string, unknown>;

        const map: Record<string, string> = {};
        for (const entry of group.tokens) {
            const parts = entry.token.split(".");
            let current: unknown = tokenColors;
            for (const part of parts) {
                if (current && typeof current === "object") {
                    current = (current as Record<string, unknown>)[part];
                } else {
                    current = undefined;
                    break;
                }
            }
            if (
                current &&
                typeof current === "object" &&
                (current as Record<string, unknown>).value
            ) {
                map[entry.token] = (current as Record<string, unknown>).value as string;
            }
        }
        return map;
    }, [group.tokens]);

    return (
        <VStack align="start" gap="0" w="full">
            <HStack
                gap="2"
                py="1.5"
                px="2"
                cursor="pointer"
                _hover={{ bg: "bg.elevatedLight" }}
                onClick={() => setOpen((v) => !v)}
                w="full"
                userSelect="none"
            >
                <Box color="fg.muted" fontSize="sm">
                    {open ? <TbChevronDown /> : <TbChevronRight />}
                </Box>
                <Text fontSize="sm" fontWeight="bold">
                    {group.name}
                </Text>
                {!open && (
                    <HStack gap="1" ml="2">
                        {group.tokens.slice(0, 8).map((t) => (
                            <Box
                                key={t.token}
                                w="12px"
                                h="12px"
                                style={
                                    rawValues[t.token]
                                        ? { backgroundColor: rawValues[t.token] }
                                        : undefined
                                }
                                borderWidth="1px"
                                borderColor="border.muted"
                            />
                        ))}
                        {group.tokens.length > 8 && (
                            <Text fontSize="2xs" color="fg.muted">
                                +{group.tokens.length - 8}
                            </Text>
                        )}
                    </HStack>
                )}
            </HStack>

            {open && (
                <Box pl="4" ml="2" borderLeftWidth="2px" borderColor="border.muted" w="full">
                    {group.tokens.map((t) => (
                        <PaletteSwatch
                            key={t.token}
                            token={t.token}
                            rawValue={rawValues[t.token]}
                        />
                    ))}
                </Box>
            )}
        </VStack>
    );
}

function collectAllTokens(group: TokenGroup): TokenEntry[] {
    return [...group.tokens, ...group.children.flatMap(collectAllTokens)];
}

// ── Section ─────────────────────────────────────────────────────────

export function ColorBrowserSection() {
    const groups = useSemanticGroups();
    const palettes = usePaletteGroups();

    return (
        <ThemesSection title="Color Browser">
            <Grid templateColumns="1fr 1fr" gap="4" w="full">
                <Flex
                    direction="column"
                    w="full"
                    bg="bg.elevated"
                    borderWidth="1px"
                    borderColor="border"
                    p="2"
                >
                    <Text fontSize="sm" fontWeight="bold" color="fg.muted" px="2" py="2">
                        Semantic Tokens
                    </Text>
                    {groups.map((group) => (
                        <TokenGroupSection key={group.name} group={group} depth={0} />
                    ))}
                </Flex>

                <Flex
                    direction="column"
                    w="full"
                    bg="bg.elevated"
                    borderWidth="1px"
                    borderColor="border"
                    p="2"
                >
                    <Text fontSize="sm" fontWeight="bold" color="fg.muted" px="2" py="2">
                        Palettes
                    </Text>
                    {palettes.map((group) => (
                        <PaletteGroupSection key={group.name} group={group} />
                    ))}
                </Flex>
            </Grid>
        </ThemesSection>
    );
}

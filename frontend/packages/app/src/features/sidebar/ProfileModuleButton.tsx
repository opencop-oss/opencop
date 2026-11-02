// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Icon, VisuallyHidden } from "@chakra-ui/react";
import { useState, type ReactNode, type Ref } from "react";
import { useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { ApiError } from "@opencop/api";
import { useAuth } from "~/features/auth/AuthContext";
import { useToast } from "~/features/notifications/use-toast.hook";

type ProfileModule = {
    name: string;
    icon: ReactNode;
};

type ProfileModuleButtonProps = {
    m: ProfileModule;
    ref?: Ref<HTMLButtonElement>;
};

export function ProfileModuleButton({ m, ref, ...rest }: ProfileModuleButtonProps) {
    const { logout } = useAuth();
    const navigate = useNavigate();
    const [hovered, setHovered] = useState(false);
    const [logoutPending, setLogoutPending] = useState(false);
    const { t } = useTranslation();
    const { showToast } = useToast();

    const handleLogout = async () => {
        if (logoutPending) return;

        setLogoutPending(true);
        try {
            await logout();
        } catch (error) {
            const rejectedGlobalSignOut =
                error instanceof ApiError &&
                error.status === 503 &&
                error.body.code === "GLOBAL_SIGN_OUT_UNAVAILABLE";
            showToast({
                type: "error",
                title: t(
                    rejectedGlobalSignOut
                        ? "navigation.logoutRejected"
                        : "navigation.logoutUnconfirmed",
                ),
            });
        } finally {
            setLogoutPending(false);
        }
    };

    const handleClick = () => {
        if (m.name === "logout") {
            void handleLogout();
            return;
        }

        if (m.name === "admin") {
            navigate("/admin");
        }

        if (m.name === "user") {
            navigate("/user/preferences");
        }
    };

    return (
        <>
            {m.name === "logout" ? (
                <VisuallyHidden role="status">
                    {logoutPending ? t("navigation.loggingOut") : ""}
                </VisuallyHidden>
            ) : null}
            <Button
                ref={ref}
                {...rest}
                aria-label={m.name === "logout" ? t("navigation.logout") : m.name}
                padding={2}
                w="full"
                h="16"
                flexDirection="column"
                alignItems="center"
                justifyItems="center"
                opacity={1}
                variant="subtle"
                borderRadius="xs"
                gap="1"
                onClick={handleClick}
                loading={m.name === "logout" && logoutPending}
                disabled={m.name === "logout" && logoutPending}
                bg="transparent"
                onMouseEnter={() => setHovered(true)}
                onMouseLeave={() => setHovered(false)}
                _hover={m.name === "logout" ? { bg: "red.subtle" } : { bg: "bg.subtle" }}
            >
                <Icon w="8" color={hovered && m.name === "logout" ? "red.fg" : "inherit"}>
                    {m.icon}
                </Icon>
            </Button>
        </>
    );
}

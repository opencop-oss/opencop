#!/bin/sh
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

#
# Install the Maven distribution that ./mvnw pins, so later ./mvnw calls never download it.
#
# Usage (from the directory holding mvnw and .mvn/):
#   scripts/install-maven-distribution.sh
#
# The wrapper downloads Maven from one URL with no retry, so one HTTP 429 from Maven Central fails
# the build. This tries Central and then MAVEN_CENTRAL_FALLBACK_URL, for three rounds with a growing
# pause between rounds. The wrapper still does every download and still checks the
# distributionSha256Sum in .mvn/wrapper/maven-wrapper.properties.
#
# The wrapper installs into a directory named after a hash of the URL it downloaded from, so a
# copy fetched from the fallback lands in a directory that a plain ./mvnw does not look in. That
# copy is moved to the directory the Central URL hashes to. Every later ./mvnw whose
# distributionUrl is the same, including a module's own copy of the wrapper, then finds it.
#
# The script ends by asking the wrapper where it looks for Maven, and fails if the answer is not
# the installed directory. A change to the wrapper's hashing therefore fails every image build,
# not only builds that needed the fallback.

set -eu

properties=.mvn/wrapper/maven-wrapper.properties
fallback_url="${MAVEN_CENTRAL_FALLBACK_URL:-}"

die() {
    echo "install-maven-distribution: $*" >&2
    exit 1
}

[ -f "${properties}" ] || die "run from the directory holding mvnw; ${properties} not found"

distribution_url="$(sed -n 's/^distributionUrl=//p' "${properties}" | tr -d '[:space:]')"
[ -n "${distribution_url}" ] || die "no distributionUrl in ${properties}"

# The wrapper names its install directory with Java's String.hashCode of the distribution URL,
# printed as unsigned hex (see hash_string in mvnw).
hash_source="$(mktemp -d)"
trap 'rm -rf "${hash_source}"' EXIT
printf 'class UrlHash { public static void main(String[] a) { System.out.println(Integer.toHexString(a[0].hashCode())); } }\n' \
    > "${hash_source}/UrlHash.java"
distribution_name="${distribution_url##*/}"
distribution_name="${distribution_name%.zip}"
distribution_name="${distribution_name%-bin}"
dists="${MAVEN_USER_HOME:-${HOME}/.m2}/wrapper/dists/${distribution_name}"
maven_home="${dists}/$(java "${hash_source}/UrlHash.java" "${distribution_url}")"

install_from_fallback() {
    [ -n "${fallback_url}" ] || return 1
    staging="$(mktemp -d)"
    if MAVEN_USER_HOME="${staging}" MVNW_REPOURL="${fallback_url}" ./mvnw --version > /dev/null; then
        set -- "${staging}/wrapper/dists/${distribution_name}"/*
        # An unmatched glob stays literal, so also require that the one match exists.
        [ "$#" -eq 1 ] && [ -d "$1" ] || die "expected one Maven install from the fallback in ${staging}"
        mkdir -p "${dists}" && mv "$1" "${maven_home}" || die "could not move the fallback install to ${maven_home}"
        rm -rf "${staging}"
        echo "install-maven-distribution: installed ${distribution_name} from ${fallback_url}"
        return 0
    fi
    rm -rf "${staging}"
    return 1
}

for attempt in 1 2 3; do
    if ./mvnw --version > /dev/null || install_from_fallback; then
        break
    fi
    [ "${attempt}" != "3" ] || die "could not download ${distribution_name} from Central or ${fallback_url:-a fallback (MAVEN_CENTRAL_FALLBACK_URL is unset)}"
    sleep "$((attempt * 10))"
done

MVNW_VERBOSE=true ./mvnw --version | grep -Fqx "found existing MAVEN_HOME at ${maven_home}" \
    || die "./mvnw does not use ${maven_home}; the wrapper's install-directory hash may have changed"

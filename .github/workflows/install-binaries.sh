#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2024 SURF B.V.
# SPDX-FileContributor: Joost Diepenmaat
#
# SPDX-License-Identifier: Apache-2.0

# Install clojure and babashka to `./bin` and `./lib`

set -ex

CLOJURE_VERSION="1.11.1.1273"

if [ ! -x "bin/clojure" ]; then
    curl -O "https://download.clojure.org/install/linux-install-${CLOJURE_VERSION}.sh"
    bash "linux-install-${CLOJURE_VERSION}.sh" -p "$(pwd)"
fi

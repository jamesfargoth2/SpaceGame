#!/usr/bin/env bash
# Configure, build and run the headless GalacticProcGen tests. Extra arguments go to the runner,
# e.g. ./run_tests.sh "" Hub -v   (default data dir, only cases containing "Hub", verbose).
set -euo pipefail
cd "$(dirname "$0")"
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release > /dev/null
cmake --build build --parallel
./build/GOHeadlessTests "$@"

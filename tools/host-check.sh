#!/usr/bin/env bash
# Compile and run the host-side checks. They do not need the Android SDK.
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf build/host
mkdir -p build/host
javac --release 8 -encoding UTF-8 -d build/host \
  app/src/com/lin/magazinehome/Logic.java \
  app/src/com/lin/magazinehome/Pairing.java \
  app/src/com/lin/magazinehome/LanGuard.java \
  host-test/HostCheck.java
java -cp build/host com.lin.magazinehome.HostCheck

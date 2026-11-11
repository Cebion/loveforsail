#!/bin/bash
# Builds lovesail-portmaster.jar (shim + launcher) and libsdl2bridge.so for the host architecture.
set -e
cd "$(dirname "$0")"
rm -rf build/classes
mkdir -p build/classes
javac --release 11 -Xlint:-options -d build/classes $(find shim/src -name '*.java')
printf 'Manifest-Version: 1.0\nMain-Class: LarryPortmaster\n' > build/manifest.txt
jar cfm build/lovesail-portmaster.jar build/manifest.txt -C build/classes . -C shim/resources .
make -C bridge
cp bridge/libsdl2bridge.so build/
echo "built: build/lovesail-portmaster.jar build/libsdl2bridge.so ($(uname -m))"

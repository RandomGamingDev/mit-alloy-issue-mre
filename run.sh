#!/bin/bash
set -e

ALLOY_JAR=lib/alloy.jar   # adjust path to wherever your alloy.jar lives

echo "Compiling SiloedMRE.java against alloy.jar..."
mkdir -p build/classes
javac -cp "$ALLOY_JAR" SiloedMRE.java -d build/classes

echo "Running SiloedMRE..."
java -cp "$ALLOY_JAR:build/classes" SiloedMRE
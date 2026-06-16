#!/bin/bash
set -e

echo "Compiling official Kodkod from submodule..."
mkdir -p build/classes
# Find all Java source files in the official repo
find kodkod/src -name "*.java" > sources.txt
# Compile Kodkod (using the SAT4J jar we downloaded into lib/)
javac -cp lib/org.ow2.sat4j.core.jar @sources.txt -d build/classes
# Package it into pure-kodkod.jar
jar cf build/pure-kodkod.jar -C build/classes .
rm sources.txt

echo "Compiling SiloedMRE.java..."
javac -cp build/pure-kodkod.jar:lib/org.ow2.sat4j.core.jar SiloedMRE.java

echo "Running SiloedMRE..."
java -cp build/pure-kodkod.jar:lib/org.ow2.sat4j.core.jar:. SiloedMRE

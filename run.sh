#!/bin/bash
set -e

echo "Compiling DuplicateMRE.java..."
javac -d . -cp alloy.jar DuplicateMRE.java

echo "Running DuplicateMRE..."
java -cp alloy.jar:. edu.mit.csail.sdg.translator.DuplicateMRE

#!/bin/sh
set -e

find src -name "*.java" | sort | xargs $JAVA_HOME/bin/javac -d out
$JAVA_HOME/bin/jar cfe sprite-editor.jar com.atari.spritemaker.Main -C out .
echo "Build complete: sprite-editor.jar"

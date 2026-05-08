#!/bin/bash
# compile and run script for macos
set -e
mkdir -p bin
javac -d bin -cp "lib/*" $(find src -name "*.java")
java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -cp "bin:lib/*" com.miniv.core.Main

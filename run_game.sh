#!/bin/bash
# compile and run script for macos
set -e
mkdir -p bin
javac -cp "lib/*:src" src/com/miniv/core/Main.java -d bin
java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -cp "bin:lib/*" com.miniv.core.Main

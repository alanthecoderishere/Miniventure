#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
if [[ "$(uname)" == "Darwin" ]]; then
  exec java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -jar MiniVenture.jar "$@"
else
  exec java --enable-native-access=ALL-UNNAMED -jar MiniVenture.jar "$@"
fi

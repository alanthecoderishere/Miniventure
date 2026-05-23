#!/bin/bash
# Download LWJGL 3.3.6 + JOML into lib/ (for local build only — lib/ is gitignored).
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

LWJGL_VERSION="3.3.6"
JOML_VERSION="1.10.8"
BASE="https://repo1.maven.org/maven2"

mkdir -p lib

download() {
  local path="$1"
  local file="$2"
  if [ -f "lib/$file" ]; then
    echo "  have $file"
    return
  fi
  echo "  get $file"
  curl -fsSL "$BASE/$path" -o "lib/$file"
}

echo "==> LWJGL $LWJGL_VERSION"
GROUP="org/lwjgl"
V="$LWJGL_VERSION"

for artifact in lwjgl lwjgl-glfw lwjgl-opengl lwjgl-stb; do
  download "$GROUP/$artifact/$V/$artifact-$V.jar" "$artifact-$V.jar"
  download "$GROUP/$artifact/$V/$artifact-$V-natives-macos.jar" "$artifact-$V-natives-macos.jar"
  download "$GROUP/$artifact/$V/$artifact-$V-natives-linux.jar" "$artifact-$V-natives-linux.jar"
  download "$GROUP/$artifact/$V/$artifact-$V-natives-windows.jar" "$artifact-$V-natives-windows.jar"
done

# Symlink versioned names expected by build_jar.sh / run_game.sh
cd lib
for artifact in lwjgl lwjgl-glfw lwjgl-opengl lwjgl-stb; do
  ln -sf "${artifact}-${V}.jar" "${artifact}.jar" 2>/dev/null || cp -f "${artifact}-${V}.jar" "${artifact}.jar"
  ln -sf "${artifact}-${V}-natives-macos.jar" "${artifact}-natives-macos.jar" 2>/dev/null || cp -f "${artifact}-${V}-natives-macos.jar" "${artifact}-natives-macos.jar"
  ln -sf "${artifact}-${V}-natives-linux.jar" "${artifact}-natives-linux.jar" 2>/dev/null || cp -f "${artifact}-${V}-natives-linux.jar" "${artifact}-natives-linux.jar"
  ln -sf "${artifact}-${V}-natives-windows.jar" "${artifact}-natives-windows.jar" 2>/dev/null || cp -f "${artifact}-${V}-natives-windows.jar" "${artifact}-natives-windows.jar"
done
cd ..

echo "==> JOML $JOML_VERSION"
download "org/joml/joml/$JOML_VERSION/joml-$JOML_VERSION.jar" "joml-$JOML_VERSION.jar"

echo "Done. Run: ./build_jar.sh"

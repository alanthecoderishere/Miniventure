#!/bin/bash
# Build a runnable fat JAR (local only — do not commit dist/ or lib/ to git).
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

CLASSES="$ROOT/build/classes"
STAGING="$ROOT/build/jar-staging"
DIST="$ROOT/dist"

# Runtime JARs only (no -sources, -javadoc, unused modules)
RUNTIME_JARS=(
  lwjgl.jar
  lwjgl-glfw.jar
  lwjgl-opengl.jar
  lwjgl-stb.jar
  joml-1.10.8.jar
  lwjgl-natives-linux.jar
  lwjgl-natives-macos.jar
  lwjgl-natives-windows.jar
  lwjgl-glfw-natives-linux.jar
  lwjgl-glfw-natives-macos.jar
  lwjgl-glfw-natives-windows.jar
  lwjgl-opengl-natives-linux.jar
  lwjgl-opengl-natives-macos.jar
  lwjgl-opengl-natives-windows.jar
  lwjgl-stb-natives-linux.jar
  lwjgl-stb-natives-macos.jar
  lwjgl-stb-natives-windows.jar
)

if [ ! -d "$ROOT/lib" ]; then
  echo "Missing lib/ folder. Copy LWJGL 3 + JOML jars into lib/ first (see README)."
  exit 1
fi

for j in "${RUNTIME_JARS[@]}"; do
  if [ ! -f "$ROOT/lib/$j" ]; then
    echo "Missing: lib/$j"
    exit 1
  fi
done

echo "==> Compiling..."
mkdir -p "$CLASSES"
CP=$(echo "$ROOT/lib"/{lwjgl,lwjgl-glfw,lwjgl-opengl,lwjgl-stb,joml-1.10.8}.jar | tr ' ' ':')
javac -d "$CLASSES" -cp "$CP" $(find src -name "*.java")

echo "==> Staging fat JAR contents..."
rm -rf "$STAGING"
mkdir -p "$STAGING"
cp -R "$CLASSES"/. "$STAGING/"

for j in "${RUNTIME_JARS[@]}"; do
  (cd "$STAGING" && jar xf "$ROOT/lib/$j")
done

# Avoid duplicate signature issues when merging JARs
find "$STAGING/META-INF" -maxdepth 1 \( -name '*.SF' -o -name '*.RSA' -o -name '*.DSA' \) -delete 2>/dev/null || true

echo "==> Packaging dist/MiniVenture.jar..."
mkdir -p "$DIST"
rm -f "$DIST/MiniVenture.jar"

MANIFEST="$ROOT/build/MANIFEST.MF"
cat > "$MANIFEST" <<EOF
Manifest-Version: 1.0
Main-Class: com.miniv.core.Main

EOF

jar cfm "$DIST/MiniVenture.jar" "$MANIFEST" -C "$STAGING" .

echo "==> Copying assets..."
rm -rf "$DIST/assets"
cp -R assets "$DIST/assets"

cat > "$DIST/run.sh" <<'RUN'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
if [[ "$(uname)" == "Darwin" ]]; then
  exec java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -jar MiniVenture.jar "$@"
else
  exec java --enable-native-access=ALL-UNNAMED -jar MiniVenture.jar "$@"
fi
RUN
chmod +x "$DIST/run.sh"

cat > "$DIST/run.bat" <<'BAT'
@echo off
cd /d "%~dp0"
java --enable-native-access=ALL-UNNAMED -jar MiniVenture.jar %*
if errorlevel 1 (
    echo.
    echo Failed to start. Install JDK 17+ and make sure "java" is on your PATH.
    pause
)
BAT

echo ""
echo "Done: $DIST/MiniVenture.jar"
echo "Run (mac/Linux):  cd dist && ./run.sh"
echo "Run (Windows):    cd dist && run.bat"
echo "Zip:  (optional) zip -r MiniVenture.zip dist"

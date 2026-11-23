#!/usr/bin/env bash
# Builds Bloom.app on Linux (no Mac needed): the app jar + a Java runtime for Apple Silicon + a small launcher.
# Usage: desktop/mac/make-app.sh <bloom.jar> <version> <output folder>
# Output: <output folder>/Bloom-mac.zip (unsigned: first launch is right-click → Open, or Privacy & Security → Open Anyway).
set -euo pipefail

JAR="$1"; VERSION="$2"; OUT="$(mkdir -p "$3" && cd "$3" && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
APP="$WORK/Bloom.app"

mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources" "$APP/Contents/app"

# Java 25 runtime for macOS on Apple Silicon (Eclipse Temurin, signed and notarised by Eclipse). Cached between CI runs.
# 25 rather than 21 for its ahead-of-time cache (see the launcher below).
JRE_TGZ="${JRE_CACHE:-$WORK}/temurin-25-jre-mac-aarch64.tar.gz"
if [ ! -s "$JRE_TGZ" ]; then
  mkdir -p "$(dirname "$JRE_TGZ")"
  curl -fsSL -o "$JRE_TGZ" "https://api.adoptium.net/v3/binary/latest/25/ga/mac/aarch64/jre/hotspot/normal/eclipse"
fi
mkdir -p "$WORK/jre"
tar -xzf "$JRE_TGZ" -C "$WORK/jre"
JRE_DIR="$(find "$WORK/jre" -mindepth 1 -maxdepth 1 -type d | head -1)"
mv "$JRE_DIR" "$APP/Contents/runtime"
test -x "$APP/Contents/runtime/Contents/Home/bin/java"

cp "$JAR" "$APP/Contents/app/bloom.jar"
cp "$HERE/bloom.icns" "$APP/Contents/Resources/bloom.icns"

cat > "$APP/Contents/MacOS/Bloom" <<EOF
#!/bin/sh
# Starts Bloom with the Java runtime inside the app.
CONTENTS="\$(cd "\$(dirname "\$0")/.." && pwd)"
# Ahead-of-time cache (Java 25): the first launch of each version records which code Bloom loads and which parts run hot,
# and saves it when Bloom quits; every later launch starts with that, so the first minutes aren't spent warming up.
CACHE="\$HOME/Library/Caches/app.blush.mac"
mkdir -p "\$CACHE"
AOT="\$CACHE/bloom-$VERSION.aot"
if [ -s "\$AOT" ]; then AOTFLAG="-XX:AOTCache=\$AOT"; else rm -f "\$CACHE"/bloom-*.aot "\$CACHE"/bloom-*.jsa; AOTFLAG="-XX:AOTCacheOutput=\$AOT"; fi
exec "\$CONTENTS/runtime/Contents/Home/bin/java" \\
  -Xdock:name=Bloom \\
  -Xdock:icon="\$CONTENTS/Resources/bloom.icns" \\
  -Dapple.awt.application.name=Bloom \\
  -Dapple.awt.application.appearance=NSAppearanceNameDarkAqua \\
  -Dapple.laf.useScreenMenuBar=true \\
  -Dskiko.renderApi=METAL \\
  -Dbloom.version=$VERSION \\
  -Xmx1g \\
  "\$AOTFLAG" \\
  -Dbloom.app="\$(cd "\$CONTENTS/.." && pwd)" \\
  -Dskiko.rendering.macos.waitForPreviousFrameVsyncOnRedrawImmediately=false \\
  -Dcompose.scrolling.smooth.enabled=false \\
  -jar "\$CONTENTS/app/bloom.jar" "\$@"
EOF
chmod +x "$APP/Contents/MacOS/Bloom"

cat > "$APP/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Bloom</string>
  <key>CFBundleDisplayName</key><string>Bloom</string>
  <key>CFBundleIdentifier</key><string>app.blush.mac</string>
  <key>CFBundleExecutable</key><string>Bloom</string>
  <key>CFBundleIconFile</key><string>bloom.icns</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>$VERSION</string>
  <key>CFBundleVersion</key><string>$VERSION</string>
  <key>LSMinimumSystemVersion</key><string>12.0</string>
  <key>LSArchitecturePriority</key><array><string>arm64</string></array>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSSupportsAutomaticGraphicsSwitching</key><true/>
  <key>LSApplicationCategoryType</key><string>public.app-category.entertainment</string>
  <key>NSLocalNetworkUsageDescription</key><string>Bloom connects to your Jellyfin server on your home network.</string>
</dict>
</plist>
EOF

# zip -y keeps the runtime's symbolic links; -X leaves out Linux file attributes. Permissions (the launcher's "can run") are kept.
rm -f "$OUT/Bloom-mac.zip"
(cd "$WORK" && zip -qryX "$OUT/Bloom-mac.zip" Bloom.app)
ls -la "$OUT/Bloom-mac.zip"
du -sh "$APP"
rm -rf "$WORK"

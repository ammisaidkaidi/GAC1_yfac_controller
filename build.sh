#!/bin/bash
# build.sh — full build pipeline for the YF AC Controller APK.
# Usage: bash build.sh
set -euo pipefail

# Resolve paths relative to this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

export JAVA_HOME=/home/z/my-project/tools/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
BT=/home/z/my-project/tools/android-sdk/build-tools/34.0.0
ANDROID_JAR=/home/z/my-project/tools/android-sdk/platforms/android-28/android.jar
KS=$SCRIPT_DIR/yftech-debug.keystore
KS_PASS=android
KS_ALIAS=yftech-debug

# Clean
rm -rf build/classes build/obj build/apk
mkdir -p build/classes build/obj build/apk

echo "=== 1. Compile Java sources ==="
javac -source 1.8 -target 1.8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath  "$ANDROID_JAR" \
  -d build/classes \
  src/MainActivity.java \
  src/YfVehicle.java \
  src/AcFragment.java \
  src/ParkingFragment.java \
  src/SeatAcFragment.java

echo "=== 2. DEX with d8 ==="
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --output build/obj \
  $(find build/classes -name '*.class')

echo "=== 3. aapt2 link (manifest → APK skeleton) ==="
"$BT/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 26 \
  --target-sdk-version 28 \
  --version-code 1 \
  --version-name 1.0 \
  -o build/apk/yfac.base.apk

echo "=== 4. Merge classes.dex into APK ==="
cp build/apk/yfac.base.apk build/apk/yfac.unsigned.apk
( cd build/obj && zip -j "$SCRIPT_DIR/build/apk/yfac.unsigned.apk" classes.dex )

echo "=== 5. zipalign ==="
"$BT/zipalign" -p -f -v 4 build/apk/yfac.unsigned.apk build/apk/yfac.aligned.apk

echo "=== 6. apksigner sign ==="
"$BT/apksigner" sign \
  --ks "$KS" \
  --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --in build/apk/yfac.aligned.apk \
  --out build/apk/yfac.apk

echo "=== 7. Verify ==="
"$BT/apksigner" verify --print-certs build/apk/yfac.apk | head -5
echo ""
echo "Build OK: $(ls -la build/apk/yfac.apk | awk '{print $5, $NF}') bytes"

#!/bin/bash
# Baut OX Telefon ohne Gradle: aapt2, javac, d8, zipalign, apksigner.
# Schluessel: OX_SCHLUESSEL (keystore, nie im Repo), Kennwort in OX_KENNWORT.
set -e
SDK=${ANDROID_SDK:-/opt/android/sdk}; BT=$SDK/build-tools/35.0.0; JAR=$SDK/platforms/android-35/android.jar
cd "$(dirname "$0")"; rm -rf aus; mkdir -p aus/res aus/klassen aus/dex
$BT/aapt2 compile --dir res -o aus/res.zip
$BT/aapt2 link -o aus/roh.apk -I $JAR --manifest AndroidManifest.xml -R aus/res.zip --java aus/gen --auto-add-overlay
javac -Xlint:-options -source 11 -target 11 -encoding UTF-8 -classpath $JAR -d aus/klassen $(find src aus/gen -name '*.java')
$BT/d8 --release --min-api 26 --lib $JAR --output aus/dex $(find aus/klassen -name '*.class')
cp aus/roh.apk aus/ohne.apk; (cd aus/dex && zip -q -j ../ohne.apk classes.dex)
$BT/zipalign -f -p 4 aus/ohne.apk aus/aus.apk
$BT/apksigner sign --ks "${OX_SCHLUESSEL:?Schluessel fehlt}" --ks-pass env:OX_KENNWORT --out aus/ox-telefon.apk aus/aus.apk
$BT/apksigner verify aus/ox-telefon.apk && ls -la aus/ox-telefon.apk

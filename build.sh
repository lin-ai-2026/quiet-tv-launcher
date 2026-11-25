#!/usr/bin/env bash
# Debug build with public Android SDK tools: aapt2, javac, d8, zipalign, apksigner.
# No production keystore is read from this repository.
# Override JAVA_HOME, ANDROID_HOME, ANDROID_BUILD_TOOLS, ANDROID_PLATFORM, OUT_APK.
# To sign with your own key instead of a throwaway debug key, set:
#   KEYSTORE  KEY_ALIAS  KEYSTORE_PASSWORD  and optionally KEY_PASSWORD
set -euo pipefail
cd "$(dirname "$0")"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if ! command -v javac >/dev/null 2>&1; then
    echo "Set JAVA_HOME to a JDK 17 or newer." >&2
    exit 1
  fi
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
  export JAVA_HOME
fi
if [[ -z "${ANDROID_HOME:-}" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  ANDROID_HOME="$ANDROID_SDK_ROOT"
  export ANDROID_HOME
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "Set ANDROID_HOME to an Android SDK that contains platforms/${ANDROID_PLATFORM:-android-30} and build-tools/${ANDROID_BUILD_TOOLS:-34.0.0}." >&2
  exit 1
fi

BT_VER="${ANDROID_BUILD_TOOLS:-34.0.0}"
PLATFORM="${ANDROID_PLATFORM:-android-30}"
BT="$ANDROID_HOME/build-tools/$BT_VER"
AJ="$ANDROID_HOME/platforms/$PLATFORM/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"

for need in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$AJ" "$JAVA_HOME/bin/javac"; do
  if [[ ! -e "$need" ]]; then
    echo "Missing build tool: $need" >&2
    exit 1
  fi
done

spec="$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/.*java.specification.version = //p' | tr -d ' ')"
if [[ "$spec" != "17" ]]; then
  echo "Set JAVA_HOME to JDK 17. Verified combination: JDK 17, platforms;android-30, build-tools;${BT_VER}." >&2
  echo "JDK 21 with build-tools 34.0.0 stops in d8: internal NullPointerException while reading QrSegment\$Mode.class." >&2
  exit 1
fi

rm -rf build
mkdir -p build/res build/classes build/dex build/gen

"$BT/aapt2" compile --dir app/res -o build/res/
"$BT/aapt2" link -o build/unsigned.apk -I "$AJ" \
  --manifest app/AndroidManifest.xml -A app/assets \
  --min-sdk-version 24 --target-sdk-version 30 \
  --java build/gen build/res/*.flat

find app/src build/gen -name '*.java' > build/sources.list
"$JAVA_HOME/bin/javac" --release 8 -encoding UTF-8 -classpath "$AJ" -d build/classes @build/sources.list

mapfile -t classes < <(find build/classes -name '*.class')
"$BT/d8" --min-api 24 --lib "$AJ" --output build/dex "${classes[@]}"

cp build/unsigned.apk build/withdex.apk
python3 - <<'PY'
import zipfile
z = zipfile.ZipFile("build/withdex.apk", "a", zipfile.ZIP_DEFLATED)
z.write("build/dex/classes.dex", "classes.dex")
z.close()
PY
"$BT/zipalign" -f -p 4 build/withdex.apk build/aligned.apk

OUT="${OUT_APK:-build/quiet-tv-launcher-debug.apk}"
if [[ -n "${KEYSTORE:-}" ]]; then
  if [[ -z "${KEY_ALIAS:-}" || -z "${KEYSTORE_PASSWORD:-}" ]]; then
    echo "KEYSTORE is set; also set KEY_ALIAS and KEYSTORE_PASSWORD in the environment." >&2
    exit 1
  fi
  KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
  export KEYSTORE_PASSWORD KEY_PASSWORD
else
  KEYSTORE="build/debug.keystore"
  KEY_ALIAS="androiddebugkey"
  old_umask="$(umask)"
  umask 077
  KEYSTORE_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(18))')"
  KEY_PASSWORD="$KEYSTORE_PASSWORD"
  export KEYSTORE_PASSWORD KEY_PASSWORD
  printf '%s\n' "$KEYSTORE_PASSWORD" > build/debug-keystore-pass.txt
  umask "$old_umask"
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" \
    -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Quiet TV Launcher Debug, OU=Debug, O=Local Build, C=US" >/dev/null
fi

"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
  --out "$OUT" build/aligned.apk
"$BT/apksigner" verify --verbose "$OUT"
echo "Signed debug APK: $OUT"
echo "This signature is not a release key and cannot replace an already installed copy signed with a different key."
ls -la "$OUT"

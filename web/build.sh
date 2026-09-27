#!/bin/sh
# Builds the portable browser planner: web/dist/smart-garden-planner.html
# Needs: Java 17+, python3, and the Kotlin 2.2.10 compiler jars (downloaded from Maven Central on first run
# into $KOTLIN_JARS, default ~/.cache/sgp-kotlin).
set -e
# The Kotlin/JS compiler's output (interface order in class metadata) depends on the locale, so pin it; this keeps
# the committed dist/ file byte-identical to a CI build.
export LC_ALL=C
unset LANG LANGUAGE
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/.." && pwd)
V=2.2.10
J=${KOTLIN_JARS:-$HOME/.cache/sgp-kotlin}
mkdir -p "$J"
fetch() { # group/path artifact
  if [ ! -s "$J/$2" ]; then
    for i in 1 2 3 4 5; do
      curl -sSfL -o "$J/$2" "https://repo1.maven.org/maven2/$1/$2" && break
      echo "retrying $2"; sleep $((i * 3))
    done
  fi
}
K=org/jetbrains/kotlin
fetch $K/kotlin-compiler-embeddable/$V kotlin-compiler-embeddable-$V.jar
fetch $K/kotlin-stdlib/$V kotlin-stdlib-$V.jar
fetch $K/kotlin-script-runtime/$V kotlin-script-runtime-$V.jar
fetch $K/kotlin-reflect/1.6.10 kotlin-reflect-1.6.10.jar
fetch $K/kotlin-daemon-embeddable/$V kotlin-daemon-embeddable-$V.jar
fetch org/jetbrains/intellij/deps/trove4j/1.0.20200330 trove4j-1.0.20200330.jar
fetch org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.0 kotlinx-coroutines-core-jvm-1.8.0.jar
fetch org/jetbrains/annotations/13.0 annotations-13.0.jar
fetch $K/kotlin-stdlib-js/$V kotlin-stdlib-js-$V.klib
fetch $K/kotlin-dom-api-compat/$V kotlin-dom-api-compat-$V.klib
CP="$J/kotlin-compiler-embeddable-$V.jar:$J/kotlin-stdlib-$V.jar:$J/kotlin-script-runtime-$V.jar:$J/kotlin-reflect-1.6.10.jar:$J/kotlin-daemon-embeddable-$V.jar:$J/trove4j-1.0.20200330.jar:$J/kotlinx-coroutines-core-jvm-1.8.0.jar:$J/annotations-13.0.jar"
LIBS="$J/kotlin-stdlib-js-$V.klib:$J/kotlin-dom-api-compat-$V.klib"
kjs() { java -Xmx3g -cp "$CP" org.jetbrains.kotlin.cli.js.K2JSCompiler "$@"; }

# Shared planning rules: the same files the Android app compiles, minus Android-only ones and the JVM clock.
CORE="$ROOT/Android App/app/src/main/java/com/example/smartgardenplanner/core"
SRC=""
for f in "$CORE"/*.kt; do
  case "$(basename "$f")" in
    AgriculturalIsolationEngine.kt|CameraCaptureManager.kt|PlatformClock.kt) ;;
    *) SRC="$SRC|$f" ;;
  esac
done
for f in "$HERE"/src/platform/*.kt "$HERE"/src/app/*.kt; do SRC="$SRC|$f"; done

OUT="$HERE/build"
rm -rf "$OUT"; mkdir -p "$OUT/klib" "$OUT/js"
# Paths contain a space ("Android App"), so pass sources through an argument file.
printf '%s' "$SRC" | tr '|' '\n' | sed '/^$/d' | sed 's/.*/"&"/' > "$OUT/sources.txt"
kjs -nowarn -Xir-produce-klib-dir -libraries "$LIBS" -ir-output-dir "$OUT/klib" -ir-output-name sgp @"$OUT/sources.txt"
kjs -Xir-produce-js -Xinclude="$OUT/klib" -libraries "$LIBS" -ir-output-dir "$OUT/js" -ir-output-name sgp -target es2015 -Xir-dce
python3 "$HERE/tools/bundle.py" "$OUT/js/sgp.mjs" "$HERE/dist/smart-garden-planner.html"

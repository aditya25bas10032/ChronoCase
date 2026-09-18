#!/usr/bin/env bash
# Downloads the third-party jars ChronoCase needs into lib/.
# Run once after cloning (or whenever lib/ is empty).
#
# JavaFX publishes per-platform classifier jars (-win/-linux/-mac); the
# script downloads the right one and saves it under the plain artifact
# name, so build.sh / run-gui.sh work unchanged on every OS.
# Already-downloaded (and size-checked) jars are skipped.
set -e
cd "$(dirname "$0")"
mkdir -p lib

BASE=https://repo1.maven.org/maven2
MIN_JAR_BYTES=10000    # smaller than any real jar we need

# --- detect the JavaFX platform classifier ---
case "$(uname -s)" in
    Linux*)  CLASSIFIER=linux ;;
    Darwin*) CLASSIFIER=mac ;;
    *)       CLASSIFIER=win ;;
esac
echo "Detected platform classifier: -$CLASSIFIER"

# fetch <maven-artifact-path> <target-file> <min-bytes>
fetch() {
    local target="lib/$2"
    # Re-download when the file is missing OR suspiciously small
    # (Maven Central serves 302-byte manifest-only stubs for the
    # plain JavaFX artifacts, which once slipped through).
    if [ -f "$target" ] && [ "$(wc -c < "$target")" -ge "$3" ]; then
        echo "already present: $target"
        return 0
    fi
    echo "downloading $target ..."
    curl -fL --retry 3 -o "$target" "$BASE/$1"
    if [ "$(wc -c < "$target")" -lt "$3" ]; then
        echo "ERROR: $target is too small ($(... wc -c < "$target") bytes) - download failed"
        return 1
    fi
}

fetch com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar \
      mysql-connector-j-9.4.0.jar 100000
fetch "org/openjfx/javafx-base/26/javafx-base-26-$CLASSIFIER.jar"      javafx-base-26.jar      $MIN_JAR_BYTES
fetch "org/openjfx/javafx-graphics/26/javafx-graphics-26-$CLASSIFIER.jar" javafx-graphics-26.jar $MIN_JAR_BYTES
fetch "org/openjfx/javafx-controls/26/javafx-controls-26-$CLASSIFIER.jar" javafx-controls-26.jar $MIN_JAR_BYTES

echo "All libraries ready in lib/."

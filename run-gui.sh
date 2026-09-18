#!/usr/bin/env bash
# Runs the ChronoCase GUI on Windows (Git Bash), Linux and macOS.
# Works with a plain classpath thanks to ChronoCaseLauncher -
# no --module-path needed.
set -e
cd "$(dirname "$0")"

if [ ! -f out/view/ChronoCaseLauncher.class ]; then
    echo "Build first: bash build.sh"
    exit 1
fi

# Java classpaths separate entries with ';' on Windows and ':' on
# Linux/macOS. Git Bash reports MINGW*/MSYS*/CYGWIN* but runs the
# Windows java.exe, which needs ';'.
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
    *)                    SEP=':' ;;
esac

CP="out${SEP}lib/javafx-base-26.jar${SEP}lib/javafx-graphics-26.jar${SEP}lib/javafx-controls-26.jar"
java -cp "$CP" view.ChronoCaseLauncher "$@"

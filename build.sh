#!/usr/bin/env bash
# Compiles ChronoCase into out/ (JavaFX + MySQL jars from lib/).
# Works on Windows (Git Bash), Linux and macOS.
set -e
cd "$(dirname "$0")"

# Java classpaths separate entries with ';' on Windows and ':' on
# Linux/macOS. Git Bash reports MINGW*/MSYS*/CYGWIN* but runs the
# Windows java.exe, which needs ';'.
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
    *)                    SEP=':' ;;
esac

CP="lib/javafx-base-26.jar${SEP}lib/javafx-graphics-26.jar${SEP}lib/javafx-controls-26.jar${SEP}lib/mysql-connector-j-9.4.0.jar"

rm -rf out
mkdir -p out/view
javac -d out -cp "$CP" $(find src -name '*.java')
cp src/view/styles.css out/view/styles.css
echo "BUILD OK -> out/"

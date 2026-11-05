#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
command -v java >/dev/null || { echo "Install JDK 21 first (https://adoptium.net)"; exit 1; }
V=8.12
if [ ! -x ".gradle-dist/gradle-$V/bin/gradle" ]; then
  echo "Downloading Gradle $V ..."
  mkdir -p .gradle-dist
  curl -L -o gradle.zip "https://services.gradle.org/distributions/gradle-$V-bin.zip"
  unzip -q -o gradle.zip -d .gradle-dist && rm gradle.zip
fi
".gradle-dist/gradle-$V/bin/gradle" "${1:-build}"
VER=$(grep '^mod_version' gradle.properties | cut -d= -f2)
if [ -f "build/libs/saltbound-$VER.jar" ]; then
  rm -f saltbound-*.jar
  cp -f "build/libs/saltbound-$VER.jar" .
  echo "DONE. Mod file: saltbound-$VER.jar"
else
  echo "DONE."
fi

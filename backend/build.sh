#!/usr/bin/env bash

function die {
  red='\033[0;31m'
  no_color='\033[0m'

  >&2 log "${red}Fehler: ${*}${no_color}"
  exit 1
}

function cleanup() {
  rm -f build/app.jar
}

# Trap
trap 'cleanup; exit 1;'

docker compose up -d
if ! ./mvnw clean package; then
  die "Maven build failed."
fi

cp target/*-jar-with-dependencies.jar docker/app.jar
cd build/ || die "Could not move to 'build'"
if ! docker build -t ready2race .; then
  die "Docker build schlug fehl"
fi
cd ../
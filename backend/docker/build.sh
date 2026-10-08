#!/bin/sh
set -eu

build_database_dir=/tmp/ready2race-build-db
mkdir -p "$build_database_dir"
chown postgres:postgres "$build_database_dir"
gosu postgres initdb -D "$build_database_dir" --auth=trust --encoding=UTF8 --locale=C
gosu postgres pg_ctl -D "$build_database_dir" -l /tmp/ready2race-build-db.log \
    -o "-h 127.0.0.1 -p 5432" -w start
trap 'gosu postgres pg_ctl -D "$build_database_dir" -m immediate -w stop; rm -rf "$build_database_dir"' EXIT
gosu postgres createdb -h 127.0.0.1 ready2race-build
export BUILD_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/ready2race-build
export BUILD_DATABASE_USER=postgres
export BUILD_DATABASE_PASSWORD=unused-build-only
./gradlew :backend:shadowJar --no-daemon
mkdir -p /out
cp backend/build/libs/*-jar-with-dependencies.jar /out/app.jar

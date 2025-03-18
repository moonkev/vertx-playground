#!/usr/bin/env sh

cd ..
./gradlew build
docker build -f deploy/Dockerfile -t moonkev/vertx_playground:latest .
docker push moonkev/vertx_playground:latest
